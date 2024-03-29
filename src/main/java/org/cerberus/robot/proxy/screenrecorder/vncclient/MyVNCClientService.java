/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.screenrecorder.vncclient;

import com.shinyhut.vernacular.client.VernacularClient;
import com.shinyhut.vernacular.client.VernacularConfig;
import com.shinyhut.vernacular.client.rendering.ColorDepth;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cerberus.robot.proxy.screenrecorder.MyScreenRecorderSession;
import org.cerberus.robot.proxy.repository.UUIDRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class MyVNCClientService {

    @Autowired
    UUIDRepository uuidRepository;
    @Autowired
    SimpMessagingTemplate webSocket;
    @Autowired
    MyScreenRecorderSession myScreenRecorderSession;
    @Autowired
    MyScreenshot myScreenshot;

    private static final Logger LOG = LogManager.getLogger(MyVNCClientService.class);

    /**
     * Start VNC Session
     *
     * @param vncHost
     * @param vncPort
     * @param vncPassword
     * @return
     * @throws Exception
     */
    public JSONObject start(String vncHost, Integer vncPort, String vncPassword) throws Exception {

        JSONObject result = new JSONObject();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss.SSS");

        //Initialize UUID
        UUID uuid = UUID.randomUUID();

        //StartRecorderSession
        MyScreenRecorderSession myScreenRecorderSession = new MyScreenRecorderSession();
        myScreenRecorderSession.setUuid(uuid.toString());

        //Create folder with uuid
        File folder = new File("./recordings/" + uuid);
        if (!folder.exists()) {
            folder.mkdirs();
        } else if (!folder.isDirectory()) {
            throw new IOException("\"" + folder + "\" is not a directory.");
        }

        //Store Session in memory hashmap
        uuidRepository.addSession(uuid.toString(), myScreenRecorderSession);
        //Print log in dediacted folder
        uuidRepository.writeSession(uuid.toString());

        //Send notification to listener
        LOG.debug("call service websocket start");
        webSocket.convertAndSend("/topic/picture", new String("{\"uuid\":\"" + uuid + "\",\"action\":\"start\"} "));

        //Configure VNC Client and attach to vncSession
        VernacularConfig config = new VernacularConfig();
        VernacularClient client = new VernacularClient(config);
        myScreenRecorderSession.getVncSession().setVncClient(client);
        config.setColorDepth(ColorDepth.BPP_8_INDEXED);
        config.setErrorListener(Throwable::printStackTrace);
        config.setPasswordSupplier(() -> vncPassword);
        config.setTargetFramesPerSecond(10);
        config.setScreenUpdateListener(image -> {
            VNCSession session = myScreenRecorderSession.getVncSession();
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            Instant start = Instant.now();
            LOG.debug(String.format("Received screen update %dx%d ", width, height));
            try {
                Date now = new Date();
                MyAction currentMyAction = session.getMyCurrentAction();
                ImageIO.write((RenderedImage) image, "png", new File("./recordings/" + uuid + "/" + currentMyAction.getActionId() + "-" + dateFormat.format(now) + ".png"));
                String pictureUrl = "./recordings/" + uuid + "/" + currentMyAction.getActionId() + "-" + dateFormat.format(now) + ".png";

                //Add screenshot to session screenshot list
                session.getMyScreenshot().add(myScreenshot.initMyScreenshot(now.getTime(), pictureUrl, currentMyAction));

                //Send notification to listener
                LOG.debug("call service websocket new picture");
                webSocket.convertAndSend("/topic/picture", new String("{\"uuid\":\"" + uuid + "\",\"text\":\"" + pictureUrl + "\",\"action\":\"newPicture\",\"numberOfScreenshot\":\"" + String.valueOf(myScreenRecorderSession.getVncSession().getMyScreenshot().size()) + "\"} "));

            } catch (IOException ex) {
                LOG.warn(ex);
            } catch (Exception ex) {
                LOG.warn(ex);
            }
            Instant finish = Instant.now();
            LOG.info(String.format("Picture written in (ms) :" + Duration.between(start, finish).toMillis()));

        });
        config.setRemoteClipboardListener(text -> LOG.info(String.format("Received copied text: %s", text)));

        // Start the VNC client
        LOG.info("connecting to vnc server : " + vncHost + ":" + vncPort);
        myScreenRecorderSession.getVncSession().startVncClient(vncHost, vncPort);

        result.put("message", "VNC Client :" + vncHost + ":" + vncPort + "  Started");
        result.put("clientId", uuid.toString());
        return result;
    }

    /**
     * Disconnect VNC
     *
     * @param uuid
     * @throws Exception
     */
    public void stop(String uuid) throws Exception {
        LOG.info("disconnect");
        //Stop VNC Session
        uuidRepository.get(uuid).stopVncSession();
        //Write Log
        uuidRepository.writeSession(uuid.toString());
        //Send notification to listener
        LOG.debug("call service websocket stop");
        webSocket.convertAndSend("/topic/picture", new String("{\"uuid\":\"" + uuid + "\",\"action\":\"stop\"} "));
    }

}
