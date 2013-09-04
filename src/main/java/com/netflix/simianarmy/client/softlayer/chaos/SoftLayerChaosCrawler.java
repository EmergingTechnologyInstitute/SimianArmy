package com.netflix.simianarmy.client.softlayer.chaos;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.netflix.simianarmy.basic.chaos.BasicInstanceGroup;
import com.netflix.simianarmy.chaos.ChaosCrawler;
import com.netflix.simianarmy.client.softlayer.SoftLayerClient;
import com.netflix.simianarmy.client.softlayer.SoftLayerClient.Instance;

/*******************************************************************************
* Copyright (c) 2013 IBM Corp.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/


public class SoftLayerChaosCrawler implements ChaosCrawler {

	/**
	 * The group types Types.
	 */
	public enum Types {

		/** only crawls AutoScalingGroups. */
		ASG;
	}
	
	private final SoftLayerClient client;

	/**
	 * Instantiates a new basic chaos crawler.
	 * 
	 */
	public SoftLayerChaosCrawler(final SoftLayerClient client) {
		this.client=client;
	}

	/** {@inheritDoc} */
	@Override
	public EnumSet<?> groupTypes() {
		return EnumSet.allOf(Types.class);
	}

	/** {@inheritDoc} */
	@Override
	public List<InstanceGroup> groups() {
		return groups((String[]) null);
	}

	@Override
	public List<InstanceGroup> groups(String... names) {
		List<InstanceGroup> list = new LinkedList<InstanceGroup>();
		Set<String> nameSet = new HashSet<String>();
		if(names!=null)
			nameSet.addAll(Arrays.asList(names));
		
		for (Map.Entry<String, List<Instance>> asg : client.getInstances().entrySet()) {

			if (names==null || nameSet.contains(asg.getKey())) {
				InstanceGroup ig = new BasicInstanceGroup(asg.getKey(),
						Types.ASG, client.getRegion());
				for (Instance inst : asg.getValue()) {
					ig.addInstance(inst.getPrivateIpAddress());
				}
				list.add(ig);
			}
		}
		return list;
	}
}
