/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.proxy;

import java.util.*;

import net.lightbody.bmp.BrowserMobProxy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cerberus.robot.proxy.repository.MySessionProxiesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.browserstack.local.Local;

/**
 *
 * @author bcivel
 */
@Service
public class MySessionProxiesService {

    private static final Logger LOG = LogManager.getLogger(MySessionProxiesService.class);

    @Autowired
    MySessionProxiesRepository mySessionProxiesRepository;
    @Autowired
    MyBrowserMobProxyService myBrowserMobProxyService;
    @Autowired
    MyBrowserStackLocalService myBrowserStackLocalService;

    /**
     * Start Proxy on specific Port. If port = 0, a random free port will be
     * used
     *
     * @param port
     * @return BrowserMobProxy
     */
    public MySessionProxies start(int port, int timeout, boolean enableCapture, boolean bsLocalProxyActive, String bsKey, String bsLocalIdentifier, String bsLocalProxyHost) {

        MySessionProxies msp = new MySessionProxies();
        UUID uuid = UUID.randomUUID();
        msp.setUuid(uuid);

        //Calculate the max date for the proxy to be alive. Keep null if timeout = 0
        Date maxDateUp = null;
        String endDateMessage = "infinite timeout";
        if (timeout != 0) {
            Date now = new Date();
            Calendar c = Calendar.getInstance();
            c.setTime(now);
            c.add(Calendar.MILLISECOND, timeout);
            maxDateUp = c.getTime();
            endDateMessage = maxDateUp.toString();
        }
        msp.setMaxDateUp(maxDateUp);
        msp.setEndDateMessage(endDateMessage);

        //Start BrowserMob proxy
        LOG.info("Start Proxy '" + uuid + "'");
        BrowserMobProxy bmp = myBrowserMobProxyService.startProxy(port, enableCapture);
        msp.setBrowserMobProxy(bmp);
        msp.setPort(bmp.getPort());
        LOG.info("Proxy '" + uuid + "' started on port :" + bmp.getPort() + " until :" + endDateMessage);

        //Start BrowserStack proxy
        if (bsLocalProxyActive) {
            LOG.info("Start BrowserStackLocalProxy '" + uuid + "'");
            Local local = myBrowserStackLocalService.startBsLocal(uuid.toString(), bsKey, bsLocalIdentifier, bsLocalProxyHost, bmp.getPort());
            msp.setBrowserStackLocal(local);
            LOG.info("BrowserStackLocalProxy '" + uuid + "' started");
        }

        //Add Started proxy to the persistent list
        mySessionProxiesRepository.addMySessionProxies(uuid.toString(), msp);

        return msp;
    }

    /**
     * Stop Specific proxy
     *
     * @param uuid
     */
    public void stop(String uuid) {
        myBrowserMobProxyService.stop(uuid);
        myBrowserStackLocalService.stop(uuid);
        mySessionProxiesRepository.removeMySessionProxies(uuid);
    }

    public List<MySessionProxies> mySessionProxiesList() {
        HashMap<String, MySessionProxies> mspMap = mySessionProxiesRepository.getMySessionProxiesList();
        List<MySessionProxies> mspList = new ArrayList<>(mspMap.values());
        return mspList;
    }

    /**
     * Scheduled Task that kill proxy
     */
    @Scheduled(cron = "${scheduledtask.killproxy}")
    public void killProxy() {
        LOG.debug("Check if outdated proxy to kill");
        Date now = new Date();

        List<String> mspToStop = new ArrayList<>();

        // Selecting all Proxy that needs to be stopped.
        mySessionProxiesRepository.getMySessionProxiesList().entrySet().stream().filter(msp -> ((msp.getValue().getMaxDateUp() != null) && ((Date) msp.getValue().getMaxDateUp()).before(now))).forEachOrdered(msp -> {
            mspToStop.add(msp.getKey());
        });

        for (String mspKeyToStop : mspToStop) {
            LOG.warn("Automatically Killing Proxy : " + mspKeyToStop);
            this.stop(mspKeyToStop);
            LOG.warn("Successfully Killed Proxy : " + mspKeyToStop);
        }

    }
}
