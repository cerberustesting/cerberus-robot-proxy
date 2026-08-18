/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.proxy;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tails the real-time JSONL traffic log written by the mitmproxy addon
 * (traffic_control.py) and forwards each new entry to the front-end over
 * STOMP, on destination "/topic/traffic/{uuid}".
 *
 * @author bcivel
 */
@Service
public class TrafficStreamService {

    private static final Logger LOG = LogManager.getLogger(TrafficStreamService.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final Map<String, Tailer> tailers = new ConcurrentHashMap<>();

    /**
     * Start streaming a session's real-time traffic log to the front-end.
     *
     * @param uuid
     * @param file
     */
    public void startTailing(UUID uuid, Path file) {
        String key = uuid.toString();

        TailerListener listener = new TailerListenerAdapter() {
            @Override
            public void handle(String line) {
                if (line == null || line.isBlank()) {
                    return;
                }
                try {
                    new JSONObject(line); // validate before forwarding
                    messagingTemplate.convertAndSend("/topic/traffic/" + key, line);
                } catch (Exception e) {
                    LOG.warn("Failed to forward traffic log line for {}", key, e);
                }
            }

            @Override
            public void handle(Exception ex) {
                LOG.warn("Traffic log tailer error for {}", key, ex);
            }
        };

        Tailer tailer = new Tailer(file.toFile(), listener, 300, true);
        Thread tailerThread = new Thread(tailer, "traffic-tail-" + key);
        tailerThread.setDaemon(true);
        tailerThread.start();

        tailers.put(key, tailer);
        LOG.info("Started real-time traffic streaming for '{}' on {}", key, file);
    }

    /**
     * Stop streaming a session's real-time traffic log.
     *
     * @param uuid
     */
    public void stopTailing(UUID uuid) {
        Tailer tailer = tailers.remove(uuid.toString());
        if (tailer != null) {
            tailer.stop();
            LOG.info("Stopped real-time traffic streaming for '{}'", uuid);
        }
    }
}