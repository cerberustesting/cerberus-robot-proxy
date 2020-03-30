/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.proxy;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarLog;
import net.lightbody.bmp.proxy.CaptureType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class MyProxyService {

    private static final Logger LOG = LogManager.getLogger(MyProxyService.class);

    @Autowired
    MyProxy myProxy;

    /**
     * Start Proxy on specific Port. If port = 0, a random free port will be
     * used
     *
     * @param port
     * @return BrowserMobProxy
     */
    public BrowserMobProxy startProxy(int port) {

        BrowserMobProxy proxy = new BrowserMobProxyServer();
        proxy.setTrustAllServers(true);
        proxy.enableHarCaptureTypes(EnumSet.allOf(CaptureType.class));
        proxy.start(port);
        proxy.newHar();

        return proxy;
    }

    /**
     * Get Har for a specific Proxy defined with an uuid
     *
     * @param uuid uuid of the Proxy
     * @param requestUrlPattern Request URL to filter
     * @param emptyResponseContentText Boolean that activate the cleaning of the
     * Response Content Text
     * @return
     */
    public Har getHar(String uuid, String requestUrlPattern, boolean emptyResponseContentText) {

        Har response;
        BrowserMobProxy proxy = myProxy.getProxy(uuid);

        if (!"".equals(requestUrlPattern) || emptyResponseContentText) {
            response = getFilteredHar(proxy.getHar(), requestUrlPattern, emptyResponseContentText);
        } else {
            response = proxy.getHar();
        }

        return response;

    }

    /**
     * Get Filtered Har on specific request URL or with less detail on the
     * response
     *
     * @param har
     * @param requestUrlPattern
     * @param emptyResponseContentText
     * @return
     */
    public Har getFilteredHar(Har har, String requestUrlPattern, boolean emptyResponseContentText) {

        Har response = new Har();

        HarLog harLog = new HarLog();

        for (HarEntry entry : har.getLog().getEntries()) {
            //Filter on specific URL
            if (!"".equals(requestUrlPattern)) {
                if (entry.getRequest() != null) {
                    String requestUrl = entry.getRequest().getUrl();
                    if (requestUrl.contains(requestUrlPattern)) {
                        //Filter Response Content Text
                        if (emptyResponseContentText) {
                            entry = clearResponseContentText(entry);
                        }
                        harLog.addEntry(entry);
                    }
                }
            } else {
                //Filter Response Content Text
                if (emptyResponseContentText) {
                    entry = clearResponseContentText(entry);
                }
                harLog.addEntry(entry);
            }
        }
        response.setLog(harLog);

        return response;
    }

    /**
     * 
     * @param entry
     * @return 
     */
    private HarEntry clearResponseContentText(HarEntry entry) {
        HarEntry newEntry = new HarEntry();
        if (entry.getResponse() != null && entry.getResponse().getContent() != null) {
            newEntry = entry;
            newEntry.getResponse().getContent().setText("");
        }
        return newEntry;
    }

    /**
     * Get Har MD5 to compare files before downloading a new HAR
     * @param uuid
     * @param requestUrlPattern
     * @return 
     */
    public String getHarMD5(String uuid, String requestUrlPattern) {

        try {

            // Static getInstance method is called with hashing MD5 
            MessageDigest md = MessageDigest.getInstance("MD5");

            // digest() method is called to calculate message digest 
            //  of an input digest() return array of byte 
            byte[] messageDigest = md.digest(this.getHar(uuid, requestUrlPattern, true).toString().getBytes());

            // Convert byte array into signum representation 
            BigInteger no = new BigInteger(1, messageDigest);

            // Convert message digest into hex value 
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            return hashtext;
        } // For specifying wrong message digest algorithms 
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Stop Specific proxy
     * @param uuid 
     */
    public void stopProxy(String uuid) {
        BrowserMobProxy proxy = myProxy.getProxy(uuid);
        proxy.stop();
        myProxy.removeProxy(uuid);
    }

    /**
     * Scheduled Task that kill proxy
     */
    @Scheduled(cron = "${scheduledtask.killproxy}")
    public void killProxy() {
        LOG.debug("Check if outdated proxy to kill");
        HashMap<String, Date> map = new HashMap(myProxy.getProxyTimeoutList());
        Date now = new Date();

        for (Map.Entry<String, Date> entry : map.entrySet()) {
            if (((Date) entry.getValue()).before(now)) {
                LOG.warn("Automatically Killing Proxy : " + entry.getKey());
                this.stopProxy(entry.getKey());
                LOG.warn("Successfully Killed Proxy : " + entry.getKey());
            }
        }
    }
}
