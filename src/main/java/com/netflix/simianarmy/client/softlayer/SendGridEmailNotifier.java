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

package com.netflix.simianarmy.client.softlayer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.netflix.simianarmy.MonkeyConfiguration;
import com.netflix.simianarmy.MonkeyEmailNotifier;

/**
 * The class implements the monkey email notifier using AWS simple email service
 * for sending email.
 */
public abstract class SendGridEmailNotifier implements MonkeyEmailNotifier {
    /** The Constant LOGGER. */
    private static final Logger LOGGER = LoggerFactory.getLogger(SendGridEmailNotifier.class);
    private static final String EMAIL_PATTERN =
            "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
                    + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";

    private final Pattern emailPattern;
    
    
    private final String domain = "https://sendgrid.com/";
    private final String endpoint= "api/mail.send.json";
    private final String username;
    private final String password;

    /**
     * The constructor.
     */
    public SendGridEmailNotifier(final MonkeyConfiguration cfg) {
        super();
        String username = cfg.getStr("simianarmy.emailnotifier.sendgrid.username");
        String password = cfg.getStr("simianarmy.emailnotifier.sendgrid.password");
        this.username=username;
        this.password=password;
        this.emailPattern = Pattern.compile(EMAIL_PATTERN);
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        if (!isValidEmail(to)) {
            LOGGER.error(String.format("The destination email address %s is not valid,  no email is sent.", to));
            return;
        }
        
        List<String> toAddresses = new ArrayList<String>();        
        toAddresses.add(to);
        toAddresses.addAll(Arrays.asList(getCcAddresses(to)));
               
        String sourceAddress = getSourceAddress(to);
        
        LOGGER.debug(String.format("Sending email with subject '%s' to %s",
                subject, to));
                
        try {
            send(subject, body, sourceAddress, toAddresses);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Failed to send email to %s", to), e);
        }
        LOGGER.info(String.format("Email to %s sent, subject is %s",
                to, subject));
    }

    @Override
    public boolean isValidEmail(String email) {
        if (email == null) {
            return false;
        }
        if (emailPattern.matcher(email).matches()) {
            return true;
        } else {
            LOGGER.error(String.format("Invalid email address: %s", email));
            return false;
        }
    }
        
    private StringBuilder appendKeyValue(StringBuilder sb, String key, String value) throws UnsupportedEncodingException
    {
    	sb.append(URLEncoder.encode(key, "UTF-8"));
        sb.append("=");
        sb.append(URLEncoder.encode(value, "UTF-8"));
        sb.append("&");
    	return sb;
    }
    
    private StringBuilder appendKeyValue(StringBuilder sb, String key, List<String> value) throws UnsupportedEncodingException{        

        for(int i = 0;i < value.size();i++)
        {            
        	sb.append("&" + URLEncoder.encode(key, "UTF-8") + "[]=" + URLEncoder.encode(value.get(i), "UTF-8"));        	               
        }

        return sb;
    }
        
    public String send(String subject, String html, String from, List<String> to) throws IOException, JSONException {
           	    	
        StringBuilder requestBody = new StringBuilder();
                
        appendKeyValue(requestBody,"api_user", this.username);
        appendKeyValue(requestBody,"api_key", this.password);
        appendKeyValue(requestBody,"subject", subject);        
        appendKeyValue(requestBody,"html",html);
        appendKeyValue(requestBody,"from",from);
        appendKeyValue(requestBody,"to",to);
                
        String request = this.domain + this.endpoint;

        String message="";
        try {
            URL url = new URL(request);                     
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");

            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(),Charset.forName("UTF-8"));
            writer.write(requestBody.toString());
            
            // Get the response
            writer.flush(); 

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(),Charset.forName("UTF-8"))); 
            String line;
            StringBuilder sb = new StringBuilder();

            while ((line = reader.readLine()) != null) { 
                sb.append(line);
            }
            reader.close();
            writer.close();
            
            String response = sb.toString();
            
            
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                message = "success";
            } else {
                message = response;
            }
        } catch (MalformedURLException e) {
            message = "MalformedURLException - " + e.getMessage();
        } catch (IOException e) {
            message = "IOException - " + e.getMessage();
        }
        
        return message;
    }

}
