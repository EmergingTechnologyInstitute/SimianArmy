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
package com.netflix.simianarmy.basic;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.simianarmy.MonkeyConfiguration;
import com.netflix.simianarmy.aws.SimpleDBRecorder;
import com.netflix.simianarmy.client.aws.AWSClient;

/**
 * The Class BasicSimianArmyContext.
 */
public class BasicSimianArmyContext extends AbstractSimianArmyContext<AWSClient> {

    /** The Constant LOGGER. */
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicSimianArmyContext.class);

    private final String account;

    private final String secret;

    private final String region;
    
    private AWSClient awsClient;

    /** protected constructor as the Shell is meant to be subclassed. */
    protected BasicSimianArmyContext(String... configFiles) {
    	super(configFiles);
        account = configuration().getStr("simianarmy.client.aws.accountKey");
        secret = configuration().getStr("simianarmy.client.aws.secretKey");
        region = configuration().getStrOrElse("simianarmy.client.aws.region", "us-east-1");

        // if credentials are set explicitly make them available to the AWS SDK
        if (StringUtils.isNotBlank(account) && StringUtils.isNotBlank(secret)) {
            this.exportCredentials(account, secret);
        }    
        
        createRecorder(configuration());
        awsClient = createClient(region);
    }

    protected void createRecorder(MonkeyConfiguration configuration) {
        String domain = configuration().getStrOrElse("simianarmy.recorder.sdb.domain", "SIMIAN_ARMY");
        if (cloudClient() != null) {
            setRecorder(new SimpleDBRecorder(cloudClient(), domain));
        }
    }

    /**
     * Create the specific client. Override to provide your own client.
     */
    protected AWSClient createClient(String region) {       
        return new AWSClient(region);
    }

    /**
     * Gets the region.
     * @return the region
     */
    public String region() {
        return region;
    }

    /**
     * Exports credentials as Java system properties
     * to be picked up by AWS SDK clients.
     * @param accountKey
     * @param secretKey
     */
    public void exportCredentials(String accountKey, String secretKey) {
        System.setProperty("aws.accessKeyId", accountKey);
        System.setProperty("aws.secretKey", secretKey);
    }

	@Override
	public AWSClient cloudClient() {
		return awsClient;
	}

 
}
