/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.proxy;

import com.browserstack.local.Local;
import net.lightbody.bmp.BrowserMobProxy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;

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
    public static final String PROXY_TYPE_BROWSERMOB = "browsermob";

    private UUID uuid;
    private Integer port;
    private BrowserMobProxy browserMobProxy;
    private Process mitmProcess;
    private Integer mitmApiPort;
    private Local browserStackLocal;
    private Date maxDateUp;
    private String endDateMessage;

    public boolean isMitmproxy() {
        return mitmProcess != null;
    }

    public boolean isBrowserMobProxy() {
        return browserMobProxy != null;
    }
}