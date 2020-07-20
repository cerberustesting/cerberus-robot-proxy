/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.screenrecorder.vncclient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.shinyhut.vernacular.client.VernacularClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class VNCSession {
    
    @JsonIgnore
    VernacularClient vncClient;
    
    String status;
    List<MyScreenshot> myScreenshot;
    List<MyAction> myActionList;
    MyAction myCurrentAction;
    Date startDate;
    Date endDate;
    
    public VNCSession (){
        this.status = "started";
        myScreenshot = new ArrayList();
        myActionList = new ArrayList();
        startDate = new Date();
        myCurrentAction = new MyAction();
    }

    public VernacularClient getVncClient() {
        return vncClient;
    }
    
    public void stopVncClient() {
        this.vncClient.stop();
    }
    
    public void startVncClient(String host, int port) {
        this.vncClient.start(host, port);
    }

    public void setVncClient(VernacularClient vncClient) {
        this.vncClient = vncClient;
    }

    public List<MyScreenshot> getMyScreenshot() {
        return myScreenshot;
    }

    public void setMyScreenshot(List<MyScreenshot> myScreenshot) {
        this.myScreenshot = myScreenshot;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public List<MyAction> getMyActionList() {
        return myActionList;
    }

    public void setMyActionList(List<MyAction> myActionList) {
        this.myActionList = myActionList;
    }

    public MyAction getMyCurrentAction() {
        return myCurrentAction;
    }

    public void setMyCurrentAction(MyAction myCurrentAction) {
        this.myCurrentAction = myCurrentAction;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    
    public HashMap<MyAction, List<MyScreenshot>> getMyScreenshotListGoupByMyAction(String uuid) {
        HashMap<MyAction, List<MyScreenshot>> result = new HashMap();
        for (MyScreenshot ms : myScreenshot) {
            List<MyScreenshot> m = result.get(ms.getMyAction()) == null ? new ArrayList() : result.get(ms.getMyAction());
            m.add(ms);
            result.put(ms.getMyAction(), m);
        }
        return result;
    }
    
    public MyScreenshot getFirstMyScreenshotByMyAction(String uuid, MyAction myAction) {
        List<MyScreenshot> screenshots = this.getScreenshotListByMyAction(uuid, myAction);
        Collections.sort(screenshots, (o1, o2) -> o1.getTimestamp().compareTo(o2.getTimestamp()));
        return screenshots.get(0);
    }

    public MyScreenshot getLastMyScreenshotByMyAction(String uuid, MyAction myAction) {
        List<MyScreenshot> screenshots = this.getScreenshotListByMyAction(uuid, myAction);
        Collections.sort(screenshots, (o1, o2) -> o1.getTimestamp().compareTo(o2.getTimestamp()));
        return screenshots.get(screenshots.size() - 1);
    }
    
    public List<MyScreenshot> getScreenshotListByMyAction(String uuid, MyAction myAction) {
        List<MyScreenshot> result = new ArrayList();
        for (MyScreenshot ms : myScreenshot) {
            if (ms.getMyAction().equals(myAction)) {
                result.add(ms);
            }
        }
        return result;
    }
    
}
