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
package com.netflix.simianarmy.chaos;

import com.netflix.simianarmy.MonkeyEmailNotifier;
import com.netflix.simianarmy.chaos.ChaosCrawler.InstanceGroup;

/** The email notifier for Chaos monkey.
 *
 */
public interface ChaosEmailNotifier extends MonkeyEmailNotifier {

    /**
     * Sends an email notification for a termination of instance to group
     * owner's email address.
     * @param group the instance group
     * @param instance the instance id
     */
    public abstract void sendTerminationNotification(InstanceGroup group, String instance);

    /**
     * Sends an email notification for a termination of instance to a global
     * email address.
     * @param group the instance group
     * @param instance the instance id
     */
    public abstract void sendTerminationGlobalNotification(InstanceGroup group, String instance);

}
