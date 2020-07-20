/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.screenrecorder.vncclient;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class MyScreenshot {

    @JsonProperty("timestamp")
    public Long timestamp;
    @JsonProperty("picturePath")
    public String picturePath;
    @JsonProperty("myAction")
    public MyAction myAction;

    public MyScreenshot initMyScreenshot(Long timestamp, String picturePath, MyAction myAction) {
        MyScreenshot ms = new MyScreenshot();
        ms.timestamp = timestamp;
        ms.picturePath = picturePath;
        ms.myAction = myAction;
        return ms;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getPicturePath() {
        return picturePath;
    }

    public void setPicturePath(String picturePath) {
        this.picturePath = picturePath;
    }

    public MyAction getMyAction() {
        return myAction;
    }

    public void setMyAction(MyAction myAction) {
        this.myAction = myAction;
    }

}
