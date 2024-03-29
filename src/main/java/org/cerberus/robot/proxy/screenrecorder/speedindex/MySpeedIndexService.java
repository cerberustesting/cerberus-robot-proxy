/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.screenrecorder.speedindex;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cerberus.robot.proxy.screenrecorder.MyScreenRecorderSession;
import org.cerberus.robot.proxy.repository.UUIDRepository;
import org.cerberus.robot.proxy.screenrecorder.vncclient.MyAction;
import org.cerberus.robot.proxy.screenrecorder.vncclient.MyScreenshot;
import org.cerberus.robot.proxy.screenrecorder.vncclient.VNCSession;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class MySpeedIndexService {

    private static final Logger LOG = LogManager.getLogger(MySpeedIndexService.class);

    @Autowired
    SimpMessagingTemplate webSocket;
    @Autowired
    UUIDRepository uuidRepository;
    @Autowired
    MyScreenRecorderSession myScreenRecorderSession;

    /**
     * Get Diff percentage, removing the pixel identical from the begining
     *
     * @param currentPicture
     * @param lastPicture
     * @param firstPicture
     * @return
     */
    private static double getDiffPercentage(BufferedImage currentPicture, BufferedImage lastPicture, BufferedImage firstPicture) {
        int width = currentPicture.getWidth();
        int height = currentPicture.getHeight();
        int width2 = lastPicture.getWidth();
        int height2 = lastPicture.getHeight();
        if (width != width2 || height != height2) {
            throw new IllegalArgumentException(String.format("Images must have the same dimensions: (%d,%d) vs. (%d,%d)", width, height, width2, height2));
        }

        //Iterate on all pixels
        long pixelDifferent = 0;
        long pixelIdenticalFromBegining = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                //If pixel is different between the first frame and the last one
                if (lastPicture.getRGB(x, y) != firstPicture.getRGB(x, y)) {
                    //If pixel is different between the current frame and the last one, add 1
                    pixelDifferent += lastPicture.getRGB(x, y) == currentPicture.getRGB(x, y) ? 0 : 1;
                } else {
                    pixelIdenticalFromBegining++;
                }
            }
        }
        LOG.debug("Pixels identical from the begining :" + pixelIdenticalFromBegining);
        LOG.debug("Pixels total :" + (width * height));

        // Max difference is total pixels - pixels identical from the begining
        long maxDifference = ((width * height) - pixelIdenticalFromBegining);

        if (maxDifference == 0) {
            return 0;
        }
        return 100.0 * pixelDifferent / maxDifference;
    }

    public JSONObject calculateSpeedIndex(String uuid) throws Exception {

        JSONObject response = new JSONObject();
        
        //Start Speed Index Calculation
        uuidRepository.get(uuid).startSpeedIndex();
        uuidRepository.writeSession(uuid);
        
        //Send notification to listener
        LOG.debug("call service websocket startSpeedIndex");
        webSocket.convertAndSend("/topic/picture", new String("{\"uuid\":\"" + uuid + "\",\"action\":\"startSpeedIndex\"} "));

        VNCSession vncSession = uuidRepository.get(uuid).getVncSession();
        HashMap<MyAction, List<MyScreenshot>> msl = vncSession.getMyScreenshotListGoupByMyAction(uuid);
        
        int j = 0;
        for (Map.Entry<MyAction, List<MyScreenshot>> entry : msl.entrySet()) {

            LOG.info("First Picture : " + vncSession.getFirstMyScreenshotByMyAction(uuid, entry.getKey()).getPicturePath());
            LOG.info("Last Picture : " + vncSession.getLastMyScreenshotByMyAction(uuid, entry.getKey()).getPicturePath());
            BufferedImage lastImage = ImageIO.read(new File(vncSession.getFirstMyScreenshotByMyAction(uuid, entry.getKey()).getPicturePath()));
            BufferedImage firstImage = ImageIO.read(new File(vncSession.getLastMyScreenshotByMyAction(uuid, entry.getKey()).getPicturePath()));

            SpeedIndexStep siStep = new SpeedIndexStep();
            siStep.setMyAction(entry.getKey());
            
            double speedIndex = 0;

            List<MyScreenshot> pictureList = entry.getValue();

            LOG.info("Iterate on List size (" + pictureList.size() + ")");
            MyScreenshot previousPicture = pictureList.get(0);
            for (MyScreenshot myScreenshot : pictureList) {

                SpeedIndexSample sis = new SpeedIndexSample();
                sis.setMyScreenshot(myScreenshot);
                
                BufferedImage i = ImageIO.read(new File(myScreenshot.getPicturePath()));

                double p = getDiffPercentage(i, lastImage, firstImage);
                LOG.debug(p);
                sis.setDiffPercentage(p);

                if (myScreenshot.getTimestamp() != previousPicture.getTimestamp()) {
                    long sample = (myScreenshot.getTimestamp() - previousPicture.getTimestamp());
                    speedIndex += sample * (1 - ((100 - p) / 100));
                    sis.setSpeedIndexSample(sample * (1 - ((100 - p) / 100)));
                } else {
                    sis.setSpeedIndexSample(0);
                }

                previousPicture = myScreenshot;
                siStep.addSpeedIndexSample(sis);
            }
            DecimalFormat df = new DecimalFormat("0.000"); // import java.text.DecimalFormat;
            siStep.setSpeedIndex(df.format(speedIndex / 1000));
            siStep.setMessage("Speed Index calculated : " + df.format(speedIndex / 1000) + "s");

            uuidRepository.get(uuid).getSpeedIndex().addSpeedIndexStep(siStep);
        }
        
        LOG.info(response.toString());
        
        LOG.debug("call service websocket stopSpeedIndex");
        webSocket.convertAndSend("/topic/picture", new String("{\"uuid\":\"" + uuid + "\",\"action\":\"stopSpeedIndex\"} "));
        
        uuidRepository.get(uuid).getSpeedIndex().setStatus("completed");
        uuidRepository.writeSession(uuid);
        
        ObjectMapper mapper = new ObjectMapper();
        response = new JSONObject(mapper.writeValueAsString(uuidRepository.get(uuid)));
        
        LOG.info("response" + response.toString());
        
        return response;
    }

}
