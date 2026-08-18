/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.proxy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cerberus.robot.proxy.repository.MySessionProxiesRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author bcivel
 */
@Service
public class MyMITMProxyService {

    private static final Logger LOG = LogManager.getLogger(MyMITMProxyService.class);

    @Autowired
    MySessionProxiesRepository mySessionProxiesRepository;

    /**
     * Directory, mounted and shared with the mitmproxy container, where
     * real-time JSONL traffic logs are written. Empty disables the feature.
     */
    @Value("${mitmproxy.traffic-log-dir:}")
    private String trafficLogDir;

    /**
     * Handle to a started mitmdump process and the port its embedded
     * TrafficControl REST API (getHar/getStats/reset) is bound to.
     */
    public static class MitmProxyHandle {
        public final Process process;
        public final int apiPort;

        public MitmProxyHandle(Process process, int apiPort) {
            this.process = process;
            this.apiPort = apiPort;
        }
    }

    /**
     * Start Proxy on specific Port. If port = 0, a random free port will be
     * used
     *
     * @param port
     * @param enableCapture
     * @param uuid session identifier, used to name the real-time JSONL traffic log file
     * @return BrowserMobProxy
     */
    public MitmProxyHandle startProxy(int port, boolean enableCapture, UUID uuid) throws IOException {

        Path script = Files.createTempFile("traffic_control", ".py");

        try (InputStream is = getClass().getResourceAsStream("/traffic_control.py")) {
            Files.copy(is, script, StandardCopyOption.REPLACE_EXISTING);
        }

        // Each session's embedded TrafficControl REST API (getHar/getStats/reset) needs its
        // own port: mitmdump runs as a plain sibling process on this host, so a hardcoded port
        // would make every session but the first fail to bind it, silently returning empty HARs.
        int apiPort = findFreePort();

        List<String> command = new ArrayList<>(List.of(
                "mitmdump",
                "--listen-port", String.valueOf(port),
                "-s", script.toAbsolutePath().toString(),
                "--set", "block_global=false",
                "--set", "api_port=" + apiPort
        ));

        if (trafficLogDir != null && !trafficLogDir.isBlank()) {
            command.add("--set");
            command.add("traffic_log_dir=" + trafficLogDir);
            command.add("--set");
            command.add("session_uuid=" + uuid);
        }

        ProcessBuilder pb = new ProcessBuilder(command);

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Drain stdout/stderr continuously: mitmdump logs every intercepted
        // request, and an unread pipe fills up its OS buffer, which makes
        // the mitmdump process block on write() and freeze all traffic.
        Thread logDrain = new Thread(() -> {
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.debug("[mitmdump] {}", line);
                }
            } catch (IOException ignored) {
            }
        });
        logDrain.setDaemon(true);
        logDrain.start();

        return new MitmProxyHandle(process, apiPort);
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Path of the real-time JSONL traffic log file for a given session, or
     * {@code null} if real-time traffic logging is not configured
     * (mitmproxy.traffic-log-dir is empty).
     *
     * @param uuid
     */
    public Path getTrafficLogFile(UUID uuid) {
        if (trafficLogDir == null || trafficLogDir.isBlank()) {
            return null;
        }
        return Paths.get(trafficLogDir, uuid + ".jsonl");
    }

    /**
     *
     * @param msp
     * @throws InterruptedException
     */
    public void stop(MySessionProxies msp) throws InterruptedException {
        Process p = msp.getMitmProcess();

        if (p != null && p.isAlive()) {
            LOG.info("Stopping mitmproxy process for '{}'", msp.getUuid().toString());
            p.destroy();

            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                LOG.warn("Mitmproxy '{}' did not stop gracefully, killing",  msp.getUuid().toString());
                p.destroyForcibly();
            }
        }

        Path trafficLogFile = msp.getTrafficLogFile();
        if (trafficLogFile != null) {
            try {
                Files.deleteIfExists(trafficLogFile);
            } catch (IOException e) {
                LOG.warn("Failed to delete traffic log file {}", trafficLogFile, e);
            }
        }
    }

    /**
     *
     * @param msp
     * @param requestUrlPattern
     * @param emptyResponseContentText
     * @return
     */
    public JSONObject getHar(MySessionProxies msp,
                      String requestUrlPattern,
                      boolean emptyResponseContentText) {

        try {
            int apiPort = msp.getMitmApiPort();
            String mode = emptyResponseContentText ? "noresponse" : "full";

            StringBuilder url = new StringBuilder(
                    "http://localhost:" + apiPort + "/har?mode=" + mode
            );

            if (requestUrlPattern != null && !requestUrlPattern.isEmpty()) {
                url.append("&contains=")
                        .append(URLEncoder.encode(requestUrlPattern, StandardCharsets.UTF_8));
            }

            LOG.info("Request URL: {}", url.toString());

            HttpURLConnection conn = (HttpURLConnection)
                    new URL(url.toString()).openConnection();

            conn.setRequestMethod("POST");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(10000);

            try (InputStream is = conn.getInputStream()) {
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                LOG.info("Get HAR: {}", json.toString());
                return new JSONObject(json);
            }

        } catch (Exception ex) {
            LOG.error("Failed to retrieve HAR for {}", msp.getUuid(), ex);
            return new JSONObject();
        }
    }


    /**
     *
     * @param msp
     * @param requestUrlPattern
     * @return
     */
    public String getHarMD5(MySessionProxies msp, String requestUrlPattern) {

        try {

            // Static getInstance method is called with hashing MD5 
            MessageDigest md = MessageDigest.getInstance("MD5");

            // digest() method is called to calculate message digest 
            //  of an input digest() return array of byte 
            byte[] messageDigest = md.digest(this.getHar(msp, requestUrlPattern, true).toString().getBytes());

            // Convert byte array into signum representation 
            BigInteger no = new BigInteger(1, messageDigest);

            // Convert message digest into hex value 
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            return hashtext;
        } // For specifying wrong message digest algorithms 
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     *
     * @param msp
     */
    public void clearHar(MySessionProxies msp) {
        try {
            int apiPort = msp.getMitmApiPort();

            URL url = new URL("http://localhost:" + apiPort + "/reset");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(5000);

            int code = conn.getResponseCode();
            if (code != 200) {
                LOG.warn("Mitmproxy reset failed for {} (HTTP {})",
                        msp.getUuid(), code);
            } else {
                LOG.debug("Mitmproxy traffic reset for {}", msp.getUuid());
            }

        } catch (Exception ex) {
            LOG.error("Failed to reset mitmproxy traffic for {}", msp.getUuid(), ex);
        }
    }


    /**
     *
     * @param msp
     * @return
     */
    public JSONObject getStats(MySessionProxies msp) {

        JSONObject response = new JSONObject();

        try {
            int apiPort = msp.getMitmApiPort();

            String url = "http://localhost:" + apiPort + "/stats";

            HttpURLConnection conn = (HttpURLConnection)
                    new URL(url).openConnection();

            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(5000);

            try (InputStream is = conn.getInputStream()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                response = new JSONObject(body);
            }

        } catch (Exception ex) {
            LOG.warn("Failed to get stats for proxy {}", msp.getUuid(), ex);
        }

        return response;
    }







}
