/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.screenrecorder.video;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import net.minidev.json.parser.ParseException;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.cerberus.robot.proxy.screenrecorder.MyScreenRecorderSession;
import org.cerberus.robot.proxy.repository.UUIDRepository;
import org.cerberus.robot.proxy.screenrecorder.vncclient.MyScreenshot;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class MyVideoService {

    private static final Logger LOG = LogManager.getLogger(MyVideoService.class);

    @Autowired
    SimpMessagingTemplate webSocket;
    @Autowired
    MyScreenRecorderSession myScreenRecorderSession;
    @Autowired
    UUIDRepository uuidRepository;

    public void createVideo(String uuid) throws Exception {

        //myScreenRecorderRepository.updateSessionLogFile(uuid, OBJECT_VIDEORECORDING, new JSONObject("{\"key\":\"status\",\"value\":"+STATUS_STARTED+"}"));
        uuidRepository.get(uuid).startMyVideoGeneration();
        uuidRepository.writeSession(uuid);

        LOG.debug("call service websocket startVideo");
        webSocket.convertAndSend("/topic/picture", new String("{\"uuid\":\"" + uuid + "\",\"action\":\"startVideo\"} "));

        List<MyScreenshot> pictureList = uuidRepository.get(uuid).getVncSession().getMyScreenshot();
        MyScreenshot firstPicture = pictureList.get(0);
        Long timestampFirstPicture = firstPicture.getTimestamp();
        Long duration = 0L;
        String videoPath = "./recordings/" + uuid + "/" + uuid + ".mp4";

        Java2DFrameConverter paintConverter = new Java2DFrameConverter();
        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(videoPath, 1360, 1020);

        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setFormat("mp4");
        recorder.start();
        for (MyScreenshot ms : pictureList) {
            LOG.debug("Encoding Video : Picture : " + ms.toString());

            BufferedImage bi = addTextWatermark(ms.getMyAction().getActionId() + " - " + ms.getMyAction().getActionDescription(), "png", new File(ms.picturePath));
            Frame frame = paintConverter.getFrame(bi);
            if (recorder.getTimestamp() <= 1000 * (ms.getTimestamp() - timestampFirstPicture)) {
                recorder.setTimestamp(1000 * (ms.getTimestamp() - timestampFirstPicture));
            }
            recorder.record(frame);
        }
        recorder.stop();
        File video = new File(videoPath);

        uuidRepository.get(uuid).getMyVideo().setPath(videoPath);
        uuidRepository.get(uuid).getMyVideo().setDuration(getVideoDuration(video));
        uuidRepository.get(uuid).getMyVideo().setStatus("completed");
        uuidRepository.writeSession(uuid);
        
        LOG.debug("call service websocket stopVideo");
        webSocket.convertAndSend("/topic/picture", new String("{\"uuid\":\"" + uuid + "\",\"action\":\"stopVideo\"} "));

    }

    /**
     * Find the video on disk
     *
     * @return the video File
     */
    public static File getVideoFile(String uuid) {
        LOG.info("Get Video");
        File file = new File("./recordings/" + uuid + "/" + uuid + ".mp4");
        return file;
    }

    /**
     * Add text at the bottom of the picture
     *
     * @param text
     * @param type
     * @param source
     * @return
     * @throws IOException
     */
    private BufferedImage addTextWatermark(String text, String type, File source) throws IOException {
        BufferedImage image = ImageIO.read(source);

        // initializes necessary graphic properties
        Graphics2D w = (Graphics2D) image.getGraphics();
        w.drawImage(image, 0, 0, null);
        AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f);
        w.setComposite(alphaChannel);

        w.setColor(Color.YELLOW);
        w.fillRect(0, image.getHeight() - 40, image.getWidth(), 40);

        w.setColor(Color.RED);
        w.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));

        FontMetrics fontMetrics = w.getFontMetrics();
        Rectangle2D rect = fontMetrics.getStringBounds(text, w);

        // calculate center of the image
        int centerX = (image.getWidth() - (int) rect.getWidth()) / 2;
        int centerY = image.getHeight() - 10;

        // add text overlay to the image
        w.drawString(text, centerX, centerY);
        //ImageIO.write(watermarked, type, destination);
        w.dispose();
        return image;
    }

    public JSONArray getVideos() {

        JSONArray result = new JSONArray();
        try {

            for (Map.Entry<String, MyScreenRecorderSession> entry : uuidRepository.getAll().entrySet()) {
                ObjectMapper mapper = new ObjectMapper();
                result.put(new JSONObject(mapper.writeValueAsString(entry.getValue())));
            }
        } catch (Exception ex) {
            LOG.warn(ex);
        }
        return result;
    }
    
    public JSONObject getVideo(String uuid) {

        JSONObject result = new JSONObject();
        ObjectMapper mapper = new ObjectMapper();
        try {
            result = new JSONObject(mapper.writeValueAsString(uuidRepository.get(uuid)));
        } catch (Exception ex) {
            LOG.warn(ex);
        } 
        return result;
    }

    private static Long getVideoDuration(File video) {
        long duration = 0L;
        FFmpegFrameGrabber ff = new FFmpegFrameGrabber(video);
        try {
            ff.start();
            duration = ff.getLengthInTime() / (1000 * 1000);
            ff.stop();
        } catch (FrameGrabber.Exception e) {
            e.printStackTrace();
        }
        return duration;
    }

}
