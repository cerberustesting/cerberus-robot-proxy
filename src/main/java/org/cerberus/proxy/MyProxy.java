/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.proxy;

import java.util.Date;
import java.util.HashMap;
import javax.annotation.PostConstruct;
import net.lightbody.bmp.BrowserMobProxy;
import org.springframework.stereotype.Component;

/**
 *
 * @author bcivel
 */
@Component
public class MyProxy {

    private HashMap proxyList;
    private HashMap proxyTimeoutList;

    @PostConstruct
    public void init() {
        proxyList = new HashMap<String, BrowserMobProxy>();
        proxyTimeoutList = new HashMap<String, Date>();
    }

    public HashMap getProxyList() {
        return proxyList;
    }
    
    public HashMap getProxyTimeoutList() {
        return proxyTimeoutList;
    }

    public void addProxy(String UUID, BrowserMobProxy proxy, Date maxDateUp) {
        proxyList.put(UUID, proxy);
        proxyTimeoutList.put(UUID, maxDateUp);
    }

    public void removeProxy(String uuid) {
        proxyList.remove(uuid);
        proxyTimeoutList.remove(uuid);
    }

    public BrowserMobProxy getProxy(String uuid) {
        return (BrowserMobProxy) proxyList.get(uuid);
    }

    public int size() {
        return proxyList.size();
    }
}