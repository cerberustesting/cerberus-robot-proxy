/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.screenrecorder;

import java.util.Date;
import org.cerberus.screenrecorder.vncclient.VNCSession;
import org.cerberus.screenrecorder.speedindex.SpeedIndex;
import org.cerberus.screenrecorder.video.MyVideo;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class MyScreenRecorderSession {

    String uuid;
    VNCSession vncSession;
    MyVideo myVideo;
    SpeedIndex speedIndex;

    public MyScreenRecorderSession(){
        startVncSession();
    }
            
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public VNCSession getVncSession() {
        return vncSession;
    }

    public void setVncSession(VNCSession vncSession) {
        this.vncSession = vncSession;
    }

    public void startVncSession() {
        this.vncSession = new VNCSession();
    }
    
    public void stopVncSession() {
        this.vncSession.stopVncClient();
        this.vncSession.setEndDate(new Date());
        this.vncSession.setStatus("completed");
    }

    public MyVideo getMyVideo() {
        return myVideo;
    }

    public void setMyVideo(MyVideo myVideo) {
        this.myVideo = myVideo;
    }

    public void startMyVideoGeneration() {
        this.myVideo = new MyVideo();
    }

    public SpeedIndex getSpeedIndex() {
        return speedIndex;
    }

    public void setSpeedIndex(SpeedIndex speedIndex) {
        this.speedIndex = speedIndex;
    }

    public void startSpeedIndex() {
        this.speedIndex = new SpeedIndex();
    }

}
