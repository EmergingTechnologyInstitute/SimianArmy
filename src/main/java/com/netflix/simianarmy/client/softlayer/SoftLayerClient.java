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

package com.netflix.simianarmy.client.softlayer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.ComputeMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.netflix.simianarmy.CloudClient;

public class SoftLayerClient implements CloudClient {

	public static class TerminationRuntimeException extends RuntimeException {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		private TerminationRuntimeException() {
			super();
		}

		private TerminationRuntimeException(String message) {
			super(message);
		}

		private TerminationRuntimeException(Throwable cause) {
			super(cause);
		}

		private TerminationRuntimeException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	public static class Instance {
		private String amiId;
		private String autoScalingGroupName;
		private String instanceId;
		private String privateIpAddress;
		private String publicIpAddress;
		private String jcloudsProvder;
		private String zone;

		protected Instance(JSONObject object) {
			autoScalingGroupName = object.optString("autoScalingGroupName");
			amiId = object.optString("amiId");
			JSONObject ec2Instance = object.optJSONObject("ec2Instance");
			{
				privateIpAddress = ec2Instance.optString("privateIpAddress");
				publicIpAddress = ec2Instance.optString("publicIpAddress");
			}
			zone = object.optString("zone");
			instanceId = object.optString("instanceId");
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Instance other = (Instance) obj;
			if (amiId == null) {
				if (other.amiId != null)
					return false;
			} else if (!amiId.equals(other.amiId))
				return false;
			if (autoScalingGroupName == null) {
				if (other.autoScalingGroupName != null)
					return false;
			} else if (!autoScalingGroupName.equals(other.autoScalingGroupName))
				return false;
			if (instanceId == null) {
				if (other.instanceId != null)
					return false;
			} else if (!instanceId.equals(other.instanceId))
				return false;
			if (privateIpAddress == null) {
				if (other.privateIpAddress != null)
					return false;
			} else if (!privateIpAddress.equals(other.privateIpAddress))
				return false;
			if (publicIpAddress == null) {
				if (other.publicIpAddress != null)
					return false;
			} else if (!publicIpAddress.equals(other.publicIpAddress))
				return false;
			if (zone == null) {
				if (other.zone != null)
					return false;
			} else if (!zone.equals(other.zone))
				return false;
			return true;
		}

		public String getAmiId() {
			return amiId;
		}

		public String getAutoScalingGroupName() {
			return autoScalingGroupName;
		}

		public String getInstanceId() {
			return instanceId;
		}

		public String getPrivateIpAddress() {
			return privateIpAddress;
		}

		public String getPublicIpAddress() {
			return publicIpAddress;
		}

		public String getZone() {
			return zone;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((amiId == null) ? 0 : amiId.hashCode());
			result = prime
					* result
					+ ((autoScalingGroupName == null) ? 0
							: autoScalingGroupName.hashCode());
			result = prime * result
					+ ((instanceId == null) ? 0 : instanceId.hashCode());
			result = prime
					* result
					+ ((privateIpAddress == null) ? 0 : privateIpAddress
							.hashCode());
			result = prime
					* result
					+ ((publicIpAddress == null) ? 0 : publicIpAddress
							.hashCode());
			result = prime * result + ((zone == null) ? 0 : zone.hashCode());
			return result;
		}

		@Override
		public String toString() {
			return "Instance [amiId=" + amiId + ", autoScalingGroupName="
					+ autoScalingGroupName + ", instanceId=" + instanceId
					+ ", privateIpAddress=" + privateIpAddress
					+ ", publicIpAddress=" + publicIpAddress + ", zone=" + zone
					+ "]";
		}

	}

	public static class InstanceGroup extends HashMap<String, List<Instance>> {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

	}

	private static final Logger LOGGER = LoggerFactory
			.getLogger(SoftLayerClient.class);

	private static DefaultHttpClient sharedHttpClient;

	private final String softlayer_secret;

	private final String softlayer_userid;

	private final String region;
	
	private final String asgardServer;
	
	private final String asgardUserid;
	
	private final String asgardPasswod;
	
	private final String jcloudsProvider;

	public SoftLayerClient(String region, String softlayer_userid,
			String softlayer_secret, String asgardServer, String asgardUserid, String asgardPassword) {
		this.region = region;
		this.softlayer_secret = softlayer_secret;
		this.softlayer_userid = softlayer_userid;
		this.asgardServer=asgardServer;
		this.asgardUserid=asgardUserid;
		this.asgardPasswod=asgardPassword;
		jcloudsProvider = "softlayer";
	}

	@Override
	public void createTagsForResources(Map<String, String> keyValueMap,
			String... resourceIds) {
		LOGGER.warn("createTagsForResources:" + keyValueMap + " "
				+ Arrays.toString(resourceIds));
	}

	@Override
	public void deleteAutoScalingGroup(String asgName) {
		LOGGER.warn("deleteAutoScalingGroup:" + asgName);

	}

	@Override
	public void deleteImage(String imageId) {
		LOGGER.warn("deleteImage:" + imageId);

	}

	@Override
	public void deleteLaunchConfiguration(String launchConfigName) {
		LOGGER.warn("deleteLaunchConfiguration:" + launchConfigName);
	}

	@Override
	public void deleteSnapshot(String snapshotId) {
		LOGGER.warn("deleteSnapshot:" + snapshotId);
	}

	@Override
	public void deleteVolume(String volumeId) {
		LOGGER.warn("deleteVolume:" + volumeId);

	}

	public synchronized DefaultHttpClient getHttpClient() {
		if (sharedHttpClient == null) {
			sharedHttpClient = new DefaultHttpClient();

			UsernamePasswordCredentials upc = new UsernamePasswordCredentials(
					softlayer_userid, softlayer_secret);
			
			UsernamePasswordCredentials apc = new UsernamePasswordCredentials(
					asgardUserid, asgardPasswod);
			
			sharedHttpClient.getCredentialsProvider()
					.setCredentials(
							new AuthScope("api.softlayer.com",
									AuthScope.ANY_PORT), upc);
			
			sharedHttpClient.getCredentialsProvider()
			.setCredentials(
					new AuthScope(asgardServer,
							AuthScope.ANY_PORT), apc);
		}
		return sharedHttpClient;
	}

	public InstanceGroup getInstances() {

		try {

			HttpGet get = new HttpGet("http://"+asgardServer+":8080/" + region
					+ "/instance/list.json");

			JSONArray array = getJSONArrayResponse(get);

			InstanceGroup result = new InstanceGroup();

			for (int i = 0; i < array.length(); i++) {
				Instance inst = new Instance(array.getJSONObject(i));
				String asgName = inst.getAutoScalingGroupName();
				if (asgName != null && asgName.length() > 0) {
					List<Instance> instanceList = result.get(asgName);
					if (instanceList == null) {
						instanceList = new ArrayList<SoftLayerClient.Instance>();
						result.put(asgName, instanceList);
					}
					instanceList.add(inst);
				}
			}
			return result;
		} catch (IOException ioe) {
			throw new RuntimeException(ioe);
		} catch (JSONException ioe) {
			throw new RuntimeException(ioe);
		}

	}

	private JSONArray getJSONArrayResponse(HttpRequestBase request)
			throws IOException, JSONException {

		String string = getStringResponse(request);

		JSONArray result = string.equals("null") ? null : new JSONArray(string);
		return result;
	}

	private JSONObject getJSONObjectResponse(HttpRequestBase request)
			throws IOException, JSONException {

		String string = getStringResponse(request);

		JSONObject result = string.equals("null") ? null : new JSONObject(
				string);
		return result;
	}

	public String getRegion() {
		return region;
	}

	private String getStringResponse(HttpRequestBase request)
			throws IOException, JSONException {
		DefaultHttpClient client = getHttpClient();
		HttpResponse response = client.execute(request);
		LOGGER.info(request.getRequestLine() + "->"
				+ response.getStatusLine().toString());
		BufferedInputStream bis = null;
		try {
			bis = new BufferedInputStream(response.getEntity().getContent());

			byte[] data = new byte[8192];
			StringBuilder sb = new StringBuilder();
			int bytesRead = bis.read(data, 0, 8192);

			while (bytesRead != -1) {
				String str = new String(data, 0, bytesRead,Charset.forName("UTF-8"));
				bytesRead = bis.read(data, 0, 8192);
				sb.append(str);
			}
			return sb.toString();
		} finally {
			if(bis!=null)
			{
				bis.close();
			}
		}
	}

	@Override
	public void terminateInstance(String instanceId) {
		LOGGER.warn("terminating instance:" + instanceId);
		ComputeServiceContext context = ContextBuilder.newBuilder(jcloudsProvider).credentials(softlayer_userid, softlayer_secret).buildView(ComputeServiceContext.class);
		ComputeService client = context.getComputeService();		
		client.destroyNode(instanceId);
	}
}
