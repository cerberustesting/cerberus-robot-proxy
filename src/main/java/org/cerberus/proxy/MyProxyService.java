/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.proxy;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarLog;
import net.lightbody.bmp.proxy.CaptureType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class MyProxyService {

    private static final Logger LOG = LogManager.getLogger(MyProxyController.class);

    @Autowired
    MyProxy myProxy;

    public BrowserMobProxy startProxy(int port) {

        BrowserMobProxy proxy = new BrowserMobProxyServer();
        proxy.setTrustAllServers(true);
        proxy.enableHarCaptureTypes(EnumSet.allOf(CaptureType.class));
        proxy.start(port);

        proxy.newHar();

        return proxy;
    }

    public Har getHar(String uuid, String requestUrlPattern) {

        Har response;
        BrowserMobProxy proxy = myProxy.getProxy(uuid);

        if (!"".equals(requestUrlPattern)) {
            response = getFilteredHar(proxy.getHar(), requestUrlPattern);
        } else {
            response = proxy.getHar();
        }

        return response;

    }

    public Har getFilteredHar(Har har, String requestUrlPattern) {

        Har response = new Har();
        HarLog harLog = new HarLog();

        for (HarEntry entry : har.getLog().getEntries()) {
            if (entry.getRequest() != null) {

                String requestUrl = entry.getRequest().getUrl();
                if (requestUrl.contains(requestUrlPattern)) {
                    harLog.addEntry(entry);
                }
            }
        }
        response.setLog(harLog);
        return response;
    }

    public String getHarMD5(String uuid, String requestUrlPattern) {

        try {

            // Static getInstance method is called with hashing MD5 
            MessageDigest md = MessageDigest.getInstance("MD5");

            // digest() method is called to calculate message digest 
            //  of an input digest() return array of byte 
            byte[] messageDigest = md.digest(this.getHar(uuid, requestUrlPattern).toString().getBytes());

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
}
