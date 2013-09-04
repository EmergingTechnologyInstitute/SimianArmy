package com.netflix.simianarmy.softlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.simianarmy.Monkey;
import com.netflix.simianarmy.MonkeyConfiguration;
import com.netflix.simianarmy.basic.AbstractSimianArmyContext;
import com.netflix.simianarmy.client.softlayer.SoftLayerClient;

public class SoftLayerSimianArmyContext extends AbstractSimianArmyContext<SoftLayerClient> implements Monkey.Context<SoftLayerClient>{

    /** The Constant LOGGER. */
    private static final Logger LOGGER = LoggerFactory.getLogger(SoftLayerSimianArmyContext.class);
    
    private final String region;
    
    private final String softlayer_account;
    
    private final String softlayer_api_secret;
    
    private final String asgard_userid;
    
    private final String asgard_password;
    
    private final String asgard_server;
    
    private SoftLayerClient client;


    /** protected constructor as the Shell is meant to be subclassed. */
    protected SoftLayerSimianArmyContext(String... configFiles) {
    	super(configFiles);
    	MonkeyConfiguration config = configuration();
        
        softlayer_account = config.getStr("simianarmy.client.softlayer.account");
        softlayer_api_secret = config.getStr("simianarmy.client.softlayer.apiSecret");
        asgard_userid=config.getStr("simianarmy.client.asgard.userid");
        asgard_password=config.getStr("simianarmy.client.asgard.password");
        asgard_server=config.getStr("simianarmy.client.asgard.server");
        region = config.getStr("simianarmy.client.softlayer.region");
        
        client = new SoftLayerClient(region, softlayer_account, softlayer_api_secret,asgard_server,asgard_userid,asgard_password);
        
        createRecorder();
        
    }

    
    private void createRecorder() {        
        setRecorder(new LinkedListRecorder());
    } 

    /**
     * Gets the region.
     * @return the region
     */
    public String getRegion() {
        return region;
    }


	@Override
	public SoftLayerClient cloudClient() {
		return client;
	}
}
