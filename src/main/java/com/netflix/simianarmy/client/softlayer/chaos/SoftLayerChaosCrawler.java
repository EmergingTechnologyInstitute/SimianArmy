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
