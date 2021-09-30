/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.proxy;

import com.browserstack.local.Local;
import net.lightbody.bmp.BrowserMobProxy;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

/**
 *
 * @author bcivel
 */
@Service
public class MySessionProxies {

    private UUID uuid;
    private Integer port;
    private BrowserMobProxy browserMobProxy;
    private Local browserStackLocal;
    private Date maxDateUp;
    private String endDateMessage;

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getEndDateMessage() {
        return endDateMessage;
    }

    public void setEndDateMessage(String endDateMessage) {
        this.endDateMessage = endDateMessage;
    }

    public BrowserMobProxy getBrowserMobProxy() {
        return browserMobProxy;
    }

    public void setBrowserMobProxy(BrowserMobProxy browserMobProxy) {
        this.browserMobProxy = browserMobProxy;
    }

    public Local getBrowserStackLocal() {
        return browserStackLocal;
    }

    public void setBrowserStackLocal(Local browserStackLocal) {
        this.browserStackLocal = browserStackLocal;
    }

    public Date getMaxDateUp() {
        return maxDateUp;
    }

    public void setMaxDateUp(Date maxDateUp) {
        this.maxDateUp = maxDateUp;
    }
}