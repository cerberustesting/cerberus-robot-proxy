/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.proxy;

import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cerberus.repository.MySessionProxiesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.browserstack.local.Local;

/**
 *
 * @author bcivel
 */
@Service
public class MyBrowserStackLocalService {

    @Autowired
    MySessionProxiesRepository mySessionProxiesRepository;

    private static final Logger LOG = LogManager.getLogger(MyBrowserStackLocalService.class);

    /**
     * Start BrowserStackLocal
     *
     * @param bsKey
     * @param localIdentifier
     * @param localProxyPort
     * @return Local
     */
     public Local startBsLocal(String uuid, String bsKey, String localIdentifier, String localProxyHost, Integer localProxyPort){

        Local bsLocal = new Local();
        HashMap<String, String> bsLocalArgs = new HashMap<String, String>();
        bsLocalArgs.put("key", bsKey);
        bsLocalArgs.put("forcelocal", "true");
        bsLocalArgs.put("forceproxy", "true");
        bsLocalArgs.put("localIdentifier", localIdentifier);
        bsLocalArgs.put("localProxyHost", localProxyHost);
        bsLocalArgs.put("localProxyPort", String.valueOf(localProxyPort));

        try {
            bsLocal.start(bsLocalArgs);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bsLocal;
    }


    /**
     * Stop Specific proxy
     * @param uuid 
     */
    public void stop(String uuid) {
        Local bsLocal = mySessionProxiesRepository.getMySessionProxies(uuid).getBrowserStackLocal();
        try {
            if(bsLocal!=null && bsLocal.isRunning()) {
                LOG.info("Stopping BrowserStackLocalProxy : '" + uuid + "'");
                bsLocal.stop();
                LOG.info("BrowserStackLocalProxy : '" + uuid + "' stopped");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    

}
