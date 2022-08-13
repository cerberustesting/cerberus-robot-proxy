/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.repository;

import org.cerberus.robot.proxy.proxy.MySessionProxies;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;

/**
 *
 * @author bcivel
 */
@Component
public class MySessionProxiesRepository {

    private HashMap mySessionProxiesList;

    @PostConstruct
    public void init() {
        mySessionProxiesList = new HashMap<String, MySessionProxies>();
    }

    public HashMap<String, MySessionProxies> getMySessionProxiesList() {
        return mySessionProxiesList;
    }
    
    public void addMySessionProxies(String UUID, MySessionProxies msp) {
        mySessionProxiesList.put(UUID, msp);
    }

    public void removeMySessionProxies(String uuid) {
        mySessionProxiesList.remove(uuid);
    }

    public MySessionProxies getMySessionProxies(String uuid) {
        return (MySessionProxies) mySessionProxiesList.get(uuid);
    }
}