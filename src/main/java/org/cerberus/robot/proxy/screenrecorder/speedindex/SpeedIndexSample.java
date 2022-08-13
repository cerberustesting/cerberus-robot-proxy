/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.screenrecorder.speedindex;

import org.cerberus.robot.proxy.screenrecorder.vncclient.MyScreenshot;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class SpeedIndexSample {
    
    double diffPercentage;
    MyScreenshot myScreenshot;
    double speedIndexSample;
    long timestamp;

    public double getDiffPercentage() {
        return diffPercentage;
    }

    public void setDiffPercentage(double diffPercentage) {
        this.diffPercentage = diffPercentage;
    }

    public MyScreenshot getMyScreenshot() {
        return myScreenshot;
    }

    public void setMyScreenshot(MyScreenshot myScreenshot) {
        this.myScreenshot = myScreenshot;
    }

    public double getSpeedIndexSample() {
        return speedIndexSample;
    }

    public void setSpeedIndexSample(double speedIndexSample) {
        this.speedIndexSample = speedIndexSample;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    
}
