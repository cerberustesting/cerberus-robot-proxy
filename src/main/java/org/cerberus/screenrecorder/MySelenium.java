/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.screenrecorder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class MySelenium {
    
    @Value( "${chromedriver.path}" )
    private String chromedriverPath;

    private static final Logger LOG = LogManager.getLogger(MySelenium.class);

    public JSONObject launch(String url) throws Exception {

        JSONObject response = new JSONObject();
        System.setProperty("webdriver.chrome.driver", chromedriverPath);
        System.setProperty("java.awt.headless", "false");

        ChromeOptions options = new ChromeOptions();
        //options.addArguments("--headless");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--no-sandbox");
        options.addArguments("--remote-debugging-port=9222");
        options.addArguments("--kiosk");

        WebDriver driver = new ChromeDriver(options);

        LOG.info("Start Recording for url : " + url);
        MyScreenRecorder.startRecording("navigationTest");

        LOG.info("Opening url : " + url);
        driver.get("http://" + url);
        TimeUnit.SECONDS.sleep(1);

        LOG.info("Stop Recording for url : " + url);
        MyScreenRecorder.stopRecording();
        driver.quit();

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber("./recordings/navigationTest.avi");
        grabber.start();

        Java2DFrameConverter paintConverter = new Java2DFrameConverter();
        LOG.info("Video Duration : " + grabber.getLengthInTime());
        LOG.info("Number of Frame : " + grabber.getLengthInFrames());

        HashMap<Integer, BufferedImage> hm = new HashMap();
        BufferedImage lastImage = null;
        List<Long> timestampList = new ArrayList();

        try {
            Frame frame;
            int i = 0;
            while ((frame = grabber.grab()) != null){

                timestampList.add(frame.timestamp);
                BufferedImage bi = paintConverter.getBufferedImage(frame);

                //Record first Frame.
                if (i == 0) {
                    ImageIO.write(bi, "png", new File("./recordings/firstImage.png"));
                }

                ImageIO.write(bi, "png", new File("./recordings/video-frame-" + i + ".png"));
                hm.put(i, bi);
                lastImage = bi;
                i++;
            }
        } catch (Exception ex) {
            LOG.warn("Exception" + ex);
        }

        //Record last Frame
        ImageIO.write(lastImage,"png", new File("./recordings/lastImage.png"));
        //Read first Frame
        BufferedImage fi = ImageIO.read(new File("./recordings/firstImage.png"));

        
        JSONArray ja = new JSONArray();
        double speedIndex = 0;
        int j = 0;

        LOG.info("Iterate on Hashmap size (" + hm.size() + ")");
        for (Map.Entry<Integer, BufferedImage> entry : hm.entrySet()) {

            JSONObject information = new JSONObject();

            information.put("picture", entry.getKey());
            BufferedImage i = ImageIO.read(new File("./recordings/video-frame-" + entry.getKey() + ".png"));

            information.put("timestamp", timestampList.get(j));

            double p = getDiffPercentage(i, lastImage, fi);
            information.put("diffPercentage", p);

            if (j != 0) {
                long sample = (timestampList.get(j) - timestampList.get(j - 1)) / 1000;
                speedIndex += sample * (1 - ((100 - p) / 100));
                information.put("speedIndexSample", sample * (1 - ((100 - p) / 100)));
            } else {
                information.put("speedIndexSample", 0);
            }

            LOG.info(information.toString());
            j++;
            ja.put(information);
        }

        DecimalFormat df = new DecimalFormat("0.000"); // import java.text.DecimalFormat;

        response.put("detail", ja);
        response.put("speedIndex", df.format(speedIndex / 1000));
        response.put("message", "Speed Index calculated for " + url + " : " + df.format(speedIndex / 1000) + "s");
        response.put("url", url);
        response.put("status", "Success");
        LOG.info(response.toString());

        return response;

    }

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

        return 100.0 * pixelDifferent / maxDifference;
    }

}
