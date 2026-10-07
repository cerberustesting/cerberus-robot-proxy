/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.proxy;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cerberus.robot.proxy.repository.MySessionProxiesRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.expression.EnvironmentAccessor;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.browserstack.local.Local;

/**
 *
 * @author bcivel
 */
@Service
public class MySessionProxiesService {

    private static final Logger LOG = LogManager.getLogger(MySessionProxiesService.class);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    @Autowired
    MySessionProxiesRepository mySessionProxiesRepository;
    @Autowired
    MyMITMProxyService myMITMProxyService;
    @Autowired
    TrafficStreamService trafficStreamService;
    @Autowired
    MyBrowserStackLocalService myBrowserStackLocalService;
    @Autowired
    PostStartScriptProperties postStartScriptsProperties;

    /**
     * Start Proxy on specific Port. If port = 0, a random free port will be
     * used
     *
     * @param port
     * @return the started session
     */
    public MySessionProxies start(int port, int timeout, boolean enableCapture, boolean bsLocalProxyActive, String bsKey, String bsLocalIdentifier, String bsLocalProxyHost,
                                  String proxyType) {

        MySessionProxies msp = new MySessionProxies();
        UUID uuid = UUID.randomUUID();
        msp.setUuid(uuid);

        //Calculate the max date for the proxy to be alive. Keep null if timeout = 0
        Date maxDateUp = null;
        String endDateMessage = "infinite timeout";
        if (timeout != 0) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.MILLISECOND, timeout);
            maxDateUp = c.getTime();
            endDateMessage = maxDateUp.toString();
        }
        msp.setMaxDateUp(maxDateUp);
        msp.setEndDateMessage(endDateMessage);

        //Start proxy
        LOG.info("Start Proxy '{}' type={}", uuid, proxyType);

        try {

            if (MySessionProxies.PROXY_TYPE_MITMPROXY.equals(proxyType)) {
                // ---- MITMPROXY ----
                MyMITMProxyService.MitmProxyHandle handle = myMITMProxyService.startProxy(port, enableCapture, uuid);
                msp.setMitmProcess(handle.process);
                msp.setPort(port);
                msp.setMitmApiPort(handle.apiPort);
                msp.setMitmOutput(handle.output);

                LOG.info("Mitmproxy '{}' started on port {} until {}", uuid, port, endDateMessage);

                Path trafficLogFile = myMITMProxyService.getTrafficLogFile(uuid);
                if (trafficLogFile != null) {
                    msp.setTrafficLogFile(trafficLogFile);
                    trafficStreamService.startTailing(uuid, trafficLogFile);
                }

            } else {
                throw new IllegalArgumentException("Unsupported proxyType '" + proxyType + "': BrowserMob has been removed, only mitmproxy is supported");
            }

        //Start BrowserStack proxy
        if (bsLocalProxyActive) {
            LOG.info("Start BrowserStackLocalProxy '" + uuid + "'");
            Local local = myBrowserStackLocalService.startBsLocal(uuid.toString(), bsKey, bsLocalIdentifier, bsLocalProxyHost, msp.getPort());
            msp.setBrowserStackLocal(local);
            LOG.info("BrowserStackLocalProxy '" + uuid + "' started");
        }

        //Add Started proxy to the persistent list
        mySessionProxiesRepository.addMySessionProxies(uuid.toString(), msp);

        return msp;

        } catch (ProxyStartException e) {
            LOG.error("Failed to start proxy {}: {}", uuid, e.getMessage());
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to start proxy {}", uuid, e);
            throw new RuntimeException("Unable to start proxy", e);
        }
    }
    /**
     * Stop specific proxy
     *
     * @param uuid
     */
    public void stop(String uuid) {

        MySessionProxies msp = mySessionProxiesRepository.getMySessionProxies(uuid);
        if (msp == null) {
            throw new IllegalArgumentException("Cannot stop. Proxy not found for uuid " + uuid);
        }

        LOG.info("Stopping proxy '{}'", uuid);

        try {

            // ---- Mitmproxy ----
            if (msp.isMitmproxy()) {
                LOG.info("Stopping MitmProxy for '{}'", uuid);
                trafficStreamService.stopTailing(msp.getUuid());
                myMITMProxyService.stop(msp);
            }

            // ---- BrowserStack Local ----
            if (msp.getBrowserStackLocal() != null) {
                LOG.info("Stopping BrowserStackLocal for '{}'", uuid);
                myBrowserStackLocalService.stop(uuid);
            }

        } catch (Exception e) {
            LOG.error("Error while stopping proxy {}", uuid, e);
            throw new IllegalStateException("Error while stopping proxy for uuid " + uuid);
        } finally {
            mySessionProxiesRepository.removeMySessionProxies(uuid);
            LOG.info("Proxy '{}' fully stopped and removed", uuid);
        }
    }

    public List<MySessionProxies> mySessionProxiesList() {
        HashMap<String, MySessionProxies> mspMap = mySessionProxiesRepository.getMySessionProxiesList();
        List<MySessionProxies> mspList = new ArrayList<>(mspMap.values());
        return mspList;
    }

    /**
     * Scheduled Task that kill proxy
     */
    @Scheduled(cron = "${scheduledtask.killproxy}")
    public void killProxy() {
        LOG.debug("Check if outdated proxy to kill");
        Date now = new Date();

        List<String> mspToStop = new ArrayList<>();

        // Selecting all Proxy that needs to be stopped.
        mySessionProxiesRepository.getMySessionProxiesList().entrySet().stream().filter(msp -> ((msp.getValue().getMaxDateUp() != null) && ((Date) msp.getValue().getMaxDateUp()).before(now))).forEachOrdered(msp -> {
            mspToStop.add(msp.getKey());
        });

        for (String mspKeyToStop : mspToStop) {
            LOG.warn("Automatically Killing Proxy : " + mspKeyToStop);
            this.stop(mspKeyToStop);
            LOG.warn("Successfully Killed Proxy : " + mspKeyToStop);
        }

    }

    /**
     * Clear HAR and create a new Har
     * @param uuid
     */
    public void clearHar(String uuid) {

        MySessionProxies msp = mySessionProxiesRepository.getMySessionProxies(uuid);
        if (msp == null) {
            throw new IllegalArgumentException("Proxy not found for uuid " + uuid);
        }

        // ---- Mitmproxy ----
        if (msp.isMitmproxy()) {
            // Appel via mitmproxy command API
            myMITMProxyService.clearHar(msp);
            return;
        }

        throw new IllegalStateException("Proxy exists but no implementation found for uuid " + uuid);
    }

    /**
     *
     * @param uuid
     * @param requestUrl
     * @param emptyResponseContentText
     * @return
     * @throws Exception
     */
    public String getHar(String uuid, String requestUrl, boolean emptyResponseContentText) throws Exception {

        MySessionProxies msp = mySessionProxiesRepository.getMySessionProxies(uuid);
        if (msp == null) {
            throw new IllegalArgumentException("Proxy not found for uuid " + uuid);
        }

        // ---- Mitmproxy ----
        if (msp.isMitmproxy()) {
            // Appel via mitmproxy command API
            try (StringWriter stringwriter = new StringWriter()) {
                JSONObject har = myMITMProxyService.getHar(msp, requestUrl, emptyResponseContentText);
                return har.toString();
            }
        }

        throw new IllegalStateException("Proxy exists but no implementation found for uuid " + uuid);

    }

    /**
     * Stop Specific proxy
     * @param uuid
     */
    public JSONObject getStats(String uuid) {
        JSONObject response = new JSONObject();

        MySessionProxies msp = mySessionProxiesRepository.getMySessionProxies(uuid);
        if (msp == null) {
            throw new IllegalArgumentException("Proxy not found for uuid " + uuid);
        }

        // ---- Mitmproxy ----
        if (msp.isMitmproxy()) {
            // Appel via mitmproxy command API
            return myMITMProxyService.getStats(msp);
        }

        throw new IllegalStateException("Proxy exists but no implementation found for uuid " + uuid);

    }


    /**
     *
     * @param uuid
     * @param requestUrlPattern
     * @return
     */
    public String getHarMD5(String uuid, String requestUrlPattern){

        MySessionProxies msp = mySessionProxiesRepository.getMySessionProxies(uuid);
        if (msp == null) {
            throw new IllegalArgumentException("Proxy not found for uuid " + uuid);
        }

        // ---- Mitmproxy ----
        if (msp.isMitmproxy()) {
            // Appel via mitmproxy command API
            return myMITMProxyService.getHarMD5(msp, requestUrlPattern);
        }

        throw new IllegalStateException("Proxy exists but no implementation found for uuid " + uuid);

    }

    public void executePostStartScript(String executionUuid) throws IOException, InterruptedException {

        if (postStartScriptsProperties.getScripts().isEmpty()) {
            LOG.info("--- No PostStart script configured. Skipping post start ---");
            return;
        }

        Map<String, String> variables = new HashMap<>();
        variables.put("executionUuid", executionUuid);

        LOG.info("--- Post start Scripts : ---");

        for (PostStartScriptConfiguration script : postStartScriptsProperties.getScripts()) {
            if (script.isEnabled()) {
                List<String> fullCommand = new ArrayList<>();
                fullCommand.add(substituteVariables(script.getExecutable(), variables));

                for (String argument : script.getArguments()) {
                    fullCommand.add(substituteVariables(argument, variables));
                }

                LOG.info("Execute script: " + script.getName() + " (command: " + String.join(" ", fullCommand) + ")");
                this.startProcess(
                            fullCommand,
                            script.isWaitFor(),
                            script.getName()
                );
            } else {
                LOG.info("Script " + script.getName() + " discarded.");
            }
        }

    }

    /**
     * Replaces {@code ${variableName}} placeholders in the given value with the matching entry from
     * {@code variables}. Unknown placeholders are left untouched.
     */
    private static String substituteVariables(String value, Map<String, String> variables) {
        if (value == null) {
            return null;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String replacement = variables.get(variableName);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement != null ? replacement : matcher.group()));
        }
        matcher.appendTail(result);

        return result.toString();
    }


    private static Process startProcess(List<String> command, boolean wait, String name) throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Log async (important pour frida)
        new Thread(() -> {
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[" + name + "] " + line);
                }
            } catch (IOException ignored) {}
        }).start();

        if (wait) {
            int exitCode = process.waitFor();
            LOG.warn("[" + name + "] finished with code " + exitCode);
        }

        return process;
    }

}
