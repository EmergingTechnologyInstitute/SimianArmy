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

import java.io.InputStream;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.simianarmy.CloudClient;
import com.netflix.simianarmy.Monkey;
import com.netflix.simianarmy.MonkeyCalendar;
import com.netflix.simianarmy.MonkeyConfiguration;
import com.netflix.simianarmy.MonkeyRecorder;
import com.netflix.simianarmy.MonkeyRecorder.Event;
import com.netflix.simianarmy.MonkeyScheduler;

/**
 * The Class BasicSimianArmyContext.
 */
public abstract class AbstractSimianArmyContext<T extends CloudClient> implements Monkey.Context<T> {

    /** The Constant LOGGER. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSimianArmyContext.class);

    /** The configuration properties. */
    private final Properties properties = new Properties();

    /** The Constant MONKEY_THREADS. */
    private static final int MONKEY_THREADS = 1;

    /** The scheduler. */
    private MonkeyScheduler scheduler;

    /** The calendar. */
    private MonkeyCalendar calendar;

    /** The config. */
    private BasicConfiguration config;

    /** The recorder. */
    private MonkeyRecorder recorder;

    /** The reported events. */
    private final LinkedList<Event> eventReport;

    /** protected constructor as the Shell is meant to be subclassed. */
    protected AbstractSimianArmyContext(String... configFiles) {
        eventReport = new LinkedList<Event>();
        // Load the config files into props following the provided order.
        for (String configFile : configFiles) {
            loadConfigurationFileIntoProperties(configFile);
        }
        LOGGER.info("The following are properties in the context.");
        for (Entry<Object, Object> prop : properties.entrySet()) {
            LOGGER.info(String.format("%s = %s", prop.getKey(), prop.getValue()));
        }

        config = new BasicConfiguration(properties);
        calendar = new BasicCalendar(config);

        createScheduler();
    }

    /** loads the given config on top of the config read by previous calls. */
    protected void loadConfigurationFileIntoProperties(String propertyFileName) {
        String propFile = System.getProperty(propertyFileName, "/" + propertyFileName);
        try {
            InputStream is = AbstractSimianArmyContext.class.getResourceAsStream(propFile);
            try {
                properties.load(is);
            } finally {
                is.close();
            }
        } catch (Exception e) {
            String msg = "Unable to load properties file " + propFile + " set System property \"" + propertyFileName
                    + "\" to valid file";
            LOGGER.error(msg);
            throw new RuntimeException(msg, e);
        }
    }

    private void createScheduler() {
        int freq = (int) config.getNumOrElse("simianarmy.scheduler.frequency", 1);
        TimeUnit freqUnit = TimeUnit.valueOf(config.getStrOrElse("simianarmy.scheduler.frequencyUnit", "HOURS"));
        int threads = (int) config.getNumOrElse("simianarmy.scheduler.threads", MONKEY_THREADS);
        setScheduler(new BasicScheduler(freq, freqUnit, threads));
    }
         

    @Override
    public void reportEvent(Event evt) {
        this.eventReport.add(evt);
    }

    @Override
    public void resetEventReport() {
        eventReport.clear();
    }

    @Override
    public String getEventReport() {
        StringBuilder report = new StringBuilder();
        for (Event event : this.eventReport) {
            report.append(String.format("%s %s (", event.eventType(), event.id()));
            boolean isFirst = true;
            for (Entry<String, String> field : event.fields().entrySet()) {
                if (!isFirst) {
                    report.append(", ");
                } else {
                    isFirst = false;
                }
                report.append(String.format("%s:%s", field.getKey(), field.getValue()));
            }
            report.append(")\n");
        }
        return report.toString();
    }

    /** {@inheritDoc} */
    @Override
    public MonkeyScheduler scheduler() {
        return scheduler;
    }

    /**
     * Sets the scheduler.
     *
     * @param scheduler
     *            the new scheduler
     */
    protected void setScheduler(MonkeyScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** {@inheritDoc} */
    @Override
    public MonkeyCalendar calendar() {
        return calendar;
    }

    /**
     * Sets the calendar.
     *
     * @param calendar
     *            the new calendar
     */
    protected void setCalendar(MonkeyCalendar calendar) {
        this.calendar = calendar;
    }

    /** {@inheritDoc} */
    @Override
    public MonkeyConfiguration configuration() {
        return config;
    }

    /**
     * Sets the configuration.
     *
     * @param configuration
     *            the new configuration
     */
    protected void setConfiguration(MonkeyConfiguration configuration) {
        this.config = (BasicConfiguration) configuration;
    }

    /** {@inheritDoc} */
    @Override
    public MonkeyRecorder recorder() {
        return recorder;
    }

    /**
     * Sets the recorder.
     *
     * @param recorder
     *            the new recorder
     */
    protected void setRecorder(MonkeyRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * Gets the configuration properties.
     * @return the configuration properties
     */
    protected Properties getProperties() {
        return this.properties;
    }
}