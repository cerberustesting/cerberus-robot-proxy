/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.screenrecorder.speedindex;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class SpeedIndex {
    
    String status;
    List<SpeedIndexStep> speedIndexStep;
    Integer numberOfStep;
    
    public SpeedIndex (){
        this.status = "started";
        speedIndexStep = new ArrayList();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<SpeedIndexStep> getSpeedIndexStep() {
        return speedIndexStep;
    }

    public void setSpeedIndexStep(List<SpeedIndexStep> speedIndexStep) {
        this.speedIndexStep = speedIndexStep;
    }
    
    public void addSpeedIndexStep(SpeedIndexStep speedIndexStep) {
        this.speedIndexStep.add(speedIndexStep);
    }

    public Integer getNumberOfStep(){
        return speedIndexStep.size();
    }
}
