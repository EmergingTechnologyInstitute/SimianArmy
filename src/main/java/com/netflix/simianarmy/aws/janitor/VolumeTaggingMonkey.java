/*
 *
 *  Copyright 2012 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.simianarmy.aws.janitor;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Volume;
import com.amazonaws.services.ec2.model.VolumeAttachment;
import com.netflix.simianarmy.CloudClient;
import com.netflix.simianarmy.Monkey;
import com.netflix.simianarmy.MonkeyCalendar;
import com.netflix.simianarmy.MonkeyConfiguration;
import com.netflix.simianarmy.MonkeyRecorder.Event;
import com.netflix.simianarmy.aws.AWSResource;
import com.netflix.simianarmy.client.aws.AWSClient;
import com.netflix.simianarmy.janitor.JanitorMonkey;

/**
 * A companion monkey of Janitor Monkey for tagging EBS volumes with the last attachment information.
 * In many scenarios, EBS volumes generated by applications remain unattached to instances. Amazon
 * does not keep track of last unattached time, which makes it difficult to determine its usage.
 * To solve this, this monkey will tag all EBS volumes with last owner and instance to which they are attached
 * and the time they got detached from instance. The monkey will poll and monitor EBS volumes hourly (by default).
 *
 */
public class VolumeTaggingMonkey extends Monkey {

    /** The Constant LOGGER. */
    private static final Logger LOGGER = LoggerFactory.getLogger(VolumeTaggingMonkey.class);

    /**
     * The Interface Context.
     */
    public interface Context<T extends CloudClient> extends Monkey.Context<T> {
        /**
         * Configuration.
         *
         * @return the monkey configuration
         */
        @Override
        MonkeyConfiguration configuration();

        /**
         * AWS clients. Using a collection of clients for supporting running one monkey for multiple regions.
         *
         * @return the collection of AWS clients
         */
        Collection<T> awsClients();
    }

    private final MonkeyConfiguration config;
    private final Collection<AWSClient> awsClients;
    private final MonkeyCalendar calendar;

    /** We cache the global map from instance id to its owner when starting the monkey. */
    private final Map<AWSClient, Map<String, String>> awsClientToInstanceToOwner;

    /**
     * The constructor.
     * @param ctx the context
     */
    public VolumeTaggingMonkey(Context<AWSClient> ctx) {
        super(ctx);
        this.config = ctx.configuration();
        this.awsClients = ctx.awsClients();
        this.calendar = ctx.calendar();
        awsClientToInstanceToOwner = Maps.newHashMap();
        for (AWSClient awsClient : awsClients) {
            Map<String, String> instanceToOwner = Maps.newHashMap();
            awsClientToInstanceToOwner.put(awsClient, instanceToOwner);
            for (Instance instance : awsClient.describeInstances()) {
                for (Tag tag : instance.getTags()) {
                    if (tag.getKey().equals(JanitorMonkey.OWNER_TAG_KEY)) {
                        instanceToOwner.put(instance.getInstanceId(), tag.getValue());
                    }
                }
            }
        }
    }

    /**
     * The monkey Type.
     */
    public enum Type {
        /** Volume tagging monkey. */
        VOLUME_TAGGING
    }

    /**
     * The event types that this monkey causes.
     */
    public enum EventTypes {
        /** The event type for tagging the volume with Janitor meta data information. */
        TAGGING_JANITOR
    }

    @Override
    public Enum type() {
        return Type.VOLUME_TAGGING;
    }

    @Override
    public void doMonkeyBusiness() {
        String prop = "simianarmy.volumeTagging.enabled";
        if (config.getBoolOrElse(prop, false)) {
            for (AWSClient awsClient : awsClients) {
                tagVolumesWithLatestAttachment(awsClient);
            }
        } else {
            LOGGER.info(String.format("Volume tagging monkey is not enabled. You can set %s to true to enable it.",
                    prop));
        }
    }

    private void tagVolumesWithLatestAttachment(AWSClient awsClient) {
        List<Volume> volumes = awsClient.describeVolumes();
        LOGGER.info(String.format("Trying to tag %d volumes for Janitor Monkey meta data.",
                volumes.size()));
        Date now = calendar.now().getTime();
        for (Volume volume : volumes) {
            String owner = null, instanceId = null;
            Date lastDetachTime = null;
            List<VolumeAttachment> attachments = volume.getAttachments();
            List<Tag> tags = volume.getTags();

            // The volume can have a special tag is it does not want to be changed/tagged
            // by Janitor monkey.
            if ("donotmark".equals(getTagValue(JanitorMonkey.JANITOR_TAG, tags))) {
                LOGGER.info(String.format("The volume %s is tagged as not handled by Janitor",
                        volume.getVolumeId()));
                continue;
            }

            Map<String, String> janitorMetadata = parseJanitorTag(tags);
            // finding the instance attached most recently.
            VolumeAttachment latest = null;
            for (VolumeAttachment attachment : attachments) {
                if (latest == null || latest.getAttachTime().before(attachment.getAttachTime())) {
                    latest = attachment;
                }
            }
            if (latest != null) {
                instanceId = latest.getInstanceId();
                owner = getOwnerEmail(instanceId, janitorMetadata, tags, awsClient);
            }

            if (latest == null || "detached".equals(latest.getState())) {
                if (janitorMetadata.get(JanitorMonkey.DETACH_TIME_TAG_KEY) == null) {
                    // There is no attached instance and the last detached time is not set.
                    // Use the current time as the last detached time.
                    LOGGER.info(String.format("Setting the last detached time to %s for volume %s",
                            now, volume.getVolumeId()));
                    lastDetachTime = now;
                } else {
                    LOGGER.debug(String.format("The volume %s was already marked as detached at time %s",
                            volume.getVolumeId(), janitorMetadata.get(JanitorMonkey.DETACH_TIME_TAG_KEY)));
                }
            } else {
                // The volume is currently attached to an instance
                lastDetachTime = null;
            }
            String existingOwner = janitorMetadata.get(JanitorMonkey.OWNER_TAG_KEY);
            if (owner == null && existingOwner != null) {
                // Save the current owner in the tag when we are not able to find a owner.
                owner = existingOwner;
            }
            if (needsUpdate(janitorMetadata, owner, instanceId, lastDetachTime)) {
                Event evt = updateJanitorMetaTag(volume, instanceId, owner, lastDetachTime, awsClient);
                if (evt != null) {
                    context().recorder().recordEvent(evt);
                }
            }
        }
    }

    private String getOwnerEmail(String instanceId, Map<String, String> janitorMetadata,
                                 List<Tag> tags, AWSClient awsClient) {
        // The owner of the volume is set as the owner of the last instance attached to it.
        String owner = awsClientToInstanceToOwner.get(awsClient).get(instanceId);
        if (owner == null) {
            owner = janitorMetadata.get(JanitorMonkey.OWNER_TAG_KEY);
        }
        if (owner == null) {
            owner = getTagValue(JanitorMonkey.OWNER_TAG_KEY, tags);
        }
        String emailDomain = getOwnerEmailDomain();
        if (owner != null && !owner.contains("@")
                && StringUtils.isNotBlank(emailDomain)) {
            owner = String.format("%s@%s", owner, emailDomain);
        }
        return owner;
    }

    /**
     * Parses the Janitor meta tag set by this monkey and gets a map from key
     * to value for the tag values.
     * @param tags the tags of the volumes
     * @return the map from the Janitor meta tag key to value
     */
    private static Map<String, String> parseJanitorTag(List<Tag> tags) {
        String janitorTag = getTagValue(JanitorMonkey.JANITOR_META_TAG, tags);
        return parseJanitorMetaTag(janitorTag);
    }

    /**
     * Parses the string of Janitor meta-data tag value to get a key value map.
     * @param janitorMetaTag the value of the Janitor meta-data tag
     * @return the key value map in the Janitor meta-data tag
     */
    public static Map<String, String> parseJanitorMetaTag(String janitorMetaTag) {
        Map<String, String> metadata = new HashMap<String, String>();
        if (janitorMetaTag != null) {
            for (String keyValue : janitorMetaTag.split(";")) {
                String[] meta = keyValue.split("=");
                if (meta.length == 2) {
                    metadata.put(meta[0], meta[1]);
                }
            }
        }
        return metadata;
    }

    /** Gets the domain name for the owner email. The method can be overridden in subclasses.
     *
     * @return the domain name for the owner email.
     */
    protected String getOwnerEmailDomain() {
        return config.getStrOrElse("simianarmy.volumeTagging.ownerEmailDomain", "");
    }

    private Event updateJanitorMetaTag(Volume volume, String instance, String owner, Date lastDetachTime,
                                       AWSClient awsClient) {
        String meta = makeMetaTag(instance, owner, lastDetachTime);
        Map<String, String> janitorTags = new HashMap<String, String>();
        janitorTags.put(JanitorMonkey.JANITOR_META_TAG, meta);
        LOGGER.info(String.format("Setting tag %s to '%s' for volume %s",
                JanitorMonkey.JANITOR_META_TAG, meta, volume.getVolumeId()));
        String prop = "simianarmy.volumeTagging.leashed";
        Event evt = null;
        if (config.getBoolOrElse(prop, true)) {
            LOGGER.info("Volume tagging monkey is leashed. No real change is made to the volume.");
        } else {
            try {
                awsClient.createTagsForResources(janitorTags, volume.getVolumeId());
                evt = context().recorder().newEvent(type(), EventTypes.TAGGING_JANITOR,
                        awsClient.region(), volume.getVolumeId());
                evt.addField(JanitorMonkey.JANITOR_META_TAG, meta);
            } catch (Exception e) {
                LOGGER.error(String.format("Failed to update the tag for volume %s", volume.getVolumeId()));
            }
        }
        return evt;
    }

    /**
     * Makes the Janitor meta tag for volumes to track the last attachment/detachment information.
     * The method is intentionally made public for testing.
     * @param instance the last attached instance
     * @param owner the last owner
     * @param lastDetachTime the detach time
     * @return the meta tag of Janitor Monkey
     */
    public static String makeMetaTag(String instance, String owner, Date lastDetachTime) {
        StringBuilder meta = new StringBuilder();
        meta.append(String.format("%s=%s;",
                JanitorMonkey.INSTANCE_TAG_KEY, instance == null ? "" : instance));
        meta.append(String.format("%s=%s;", JanitorMonkey.OWNER_TAG_KEY, owner == null ? "" : owner));
        meta.append(String.format("%s=%s", JanitorMonkey.DETACH_TIME_TAG_KEY,
                lastDetachTime == null ? "" : AWSResource.DATE_FORMATTER.print(lastDetachTime.getTime())));
        return meta.toString();
    }

    private static String getTagValue(String key, List<Tag> tags) {
        for (Tag tag : tags) {
            if (tag.getKey().equals(key)) {
                return tag.getValue();
            }
        }
        return null;
    }

    /** Needs to update tags of the volume if
     * 1) owner or instance attached changed or
     * 2) the last detached status is changed.
     */
    private static boolean needsUpdate(Map<String, String> metadata,
            String owner, String instance, Date lastDetachTime) {
        return (owner != null && !StringUtils.equals(metadata.get(JanitorMonkey.OWNER_TAG_KEY), owner))
                || (instance != null && !StringUtils.equals(metadata.get(JanitorMonkey.INSTANCE_TAG_KEY), instance))
                || lastDetachTime != null;
    }

}
