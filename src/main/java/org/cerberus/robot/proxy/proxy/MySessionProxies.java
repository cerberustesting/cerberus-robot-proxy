/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.proxy;

import com.browserstack.local.Local;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Date;
import java.util.UUID;

/**
 *
 * @author bcivel
 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@Service
public class MySessionProxies {

    public static final String PROXY_TYPE_MITMPROXY = "mitmproxy";

    private UUID uuid;
    private Integer port;
    private Process mitmProcess;
    private Integer mitmApiPort;
    private MyMITMProxyService.RecentOutput mitmOutput; // last lines mitmdump printed, to explain a failure
    private Path trafficLogFile;
    private Local browserStackLocal;
    private Date maxDateUp;
    private String endDateMessage;

    public MyMITMProxyService.RecentOutput getMitmOutput() {
        return mitmOutput;
    }

    public void setMitmOutput(MyMITMProxyService.RecentOutput mitmOutput) {
        this.mitmOutput = mitmOutput;
    }

    public boolean isMitmproxy() {
        return mitmProcess != null;
    }
}