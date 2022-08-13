/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.screenrecorder.speedindex;

import java.util.ArrayList;
import java.util.List;
import org.cerberus.robot.proxy.screenrecorder.vncclient.MyAction;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class SpeedIndexStep {
    
    String status;
    MyAction myAction;
    String speedIndex;
    List<SpeedIndexSample> speedIndexSample;
    
    public SpeedIndexStep(){
        this.status = "started";
        speedIndexSample = new ArrayList();
    }

    public MyAction getMyAction() {
        return myAction;
    }

    public void setMyAction(MyAction myAction) {
        this.myAction = myAction;
    }

    public List<SpeedIndexSample> getSpeedIndexSample() {
        return speedIndexSample;
    }

    public void setSpeedIndexSample(List<SpeedIndexSample> speedIndexSample) {
        this.speedIndexSample = speedIndexSample;
    }
    
    public void addSpeedIndexSample(SpeedIndexSample speedIndexSample) {
        this.speedIndexSample.add(speedIndexSample);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
    String message;
    
    
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSpeedIndex() {
        return speedIndex;
    }

    public void setSpeedIndex(String speedIndex) {
        this.speedIndex = speedIndex;
    }

    
    
    
}
