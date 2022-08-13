/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.screenrecorder.vncclient;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class MyAction {
    
    public String actionId;
    public String actionDescription;
    public boolean silentAction;
    
    public static final String ACTION_UNKNOWN = "unknown";
    
    public MyAction(){
        this.actionId = ACTION_UNKNOWN;
        this.actionDescription = ACTION_UNKNOWN;
        this.silentAction = false;
    }
    
    public JSONObject toJson() throws JSONException{
        JSONObject jo = new JSONObject();
        jo.put("actionId", this.actionId);
        jo.put("actionDescription", this.actionDescription);
        jo.put("silentAction", this.silentAction);
        return jo;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public String getActionDescription() {
        return actionDescription;
    }

    public void setActionDescription(String actionDescription) {
        this.actionDescription = actionDescription;
    }

    public boolean isSilentAction() {
        return silentAction;
    }

    public void setSilentAction(boolean silentAction) {
        this.silentAction = silentAction;
    }
    
    
}
