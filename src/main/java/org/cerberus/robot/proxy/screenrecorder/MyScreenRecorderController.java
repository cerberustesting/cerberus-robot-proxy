package org.cerberus.robot.proxy.screenrecorder;

import org.cerberus.robot.proxy.screenrecorder.vncclient.MyAction;
import org.cerberus.robot.proxy.screenrecorder.video.MyVideoService;
import org.cerberus.robot.proxy.screenrecorder.video.MultipartFileSender;
import java.io.BufferedReader;
import org.cerberus.robot.proxy.screenrecorder.vncclient.MyVNCClientService;
import org.cerberus.robot.proxy.screenrecorder.speedindex.MySpeedIndexService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cerberus.robot.proxy.repository.UUIDRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMethod;

@RestController
public class MyScreenRecorderController {

    private static final Logger LOG = LogManager.getLogger(MyScreenRecorderController.class);

    @Autowired
    MySpeedIndexService speedIndexService;
    @Autowired
    MyVNCClientService vncRecorder;
    @Autowired
    MyVideoService myScreenRecorderService;
    @Autowired
    UUIDRepository uuidRepository;

    /**
     * Get recorded video
     *
     * @param param
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/generateVideo", method = RequestMethod.POST)
    public ResponseEntity<Resource> generateVideo(@RequestParam(value = "uuid", defaultValue = "") String uuid) throws IOException, Exception {

        myScreenRecorderService.createVideo(uuid);
        File file = myScreenRecorderService.getVideoFile(uuid);

        HttpHeaders header = new HttpHeaders();
        header.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + uuid + ".mp4");
        header.add("Cache-Control", "no-cache, no-store, must-revalidate");
        header.add("Pragma", "no-cache");
        header.add("Expires", "0");

        Path path = Paths.get(file.getAbsolutePath());
        Resource resource = resource = new UrlResource(path.toUri());

        return ResponseEntity.ok()
                .headers(header)
                .contentLength(file.length())
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .body(resource);
    }

    /**
     * get image knowing path
     *
     * @param path
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/image", method = RequestMethod.GET)
    public ResponseEntity<byte[]> getImage(@RequestParam(value = "path", defaultValue = "") String path) throws IOException {

        byte[] bytes = Files.readAllBytes(Paths.get(path));

        return ResponseEntity
                .ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(bytes);
    }

    /**
     * Start recording video
     *
     * @param url
     * @return
     */
    @RequestMapping(value = "/startRecording", method = RequestMethod.POST)
    public String startRecording(
            @RequestParam(value = "vncHost", defaultValue = "") String vncHost,
            @RequestParam(value = "vncPort", defaultValue = "") Integer vncPort,
            @RequestParam(value = "vncPassword", defaultValue = "") String vncPassword) {
        JSONObject result = new JSONObject();
        try {
            result = vncRecorder.start(vncHost, vncPort, vncPassword);
        } catch (Exception ex) {
            LOG.warn(ex);
        }
        return result.toString();
    }

    /**
     * Stop recording video
     *
     * @param url
     * @return
     */
    @RequestMapping(value = "/stopRecording", method = RequestMethod.POST)
    public String stopRecording(@RequestParam(value = "uuid", defaultValue = "") String uuid) {
        JSONObject result = new JSONObject();
        try {
            vncRecorder.stop(uuid);
            result.put("message", "Stopped recorder");
        } catch (Exception ex) {
            LOG.warn(ex);
        }
        return result.toString();
    }

    /**
     * Calculate speed index knowing the uuid
     *
     * @param uuid
     * @return
     */
    @RequestMapping(value = "/calculateSpeedIndex", method = RequestMethod.POST)
    public String calculateSpeedIndex(@RequestParam(value = "uuid", defaultValue = "") String uuid) {
        JSONObject result = new JSONObject();
        try {
            JSONObject si = speedIndexService.calculateSpeedIndex(uuid);
            result.put("message", si);
        } catch (Exception ex) {
            LOG.warn(ex);
        }
        return result.toString();
    }

    /**
     * Trigger Change Action in order to link screenshot with dedicated actions
     *
     * @param uuid
     * @return
     */
    @RequestMapping(value = "/triggerChangeAction", method = RequestMethod.POST)
    public String triggerChangeAction(@RequestParam(value = "uuid", defaultValue = "") String uuid,
            @RequestParam(value = "actionId", defaultValue = "") String actionId,
            @RequestParam(value = "silentAction", defaultValue = "false") boolean silentAction,
            @RequestParam(value = "description", defaultValue = "") String description) {
        JSONObject result = new JSONObject();
        try {
            MyAction myAction = new MyAction();
            myAction.setActionId(actionId);
            myAction.setActionDescription(description);
            myAction.setSilentAction(silentAction);
            uuidRepository.get(uuid).getVncSession().setMyCurrentAction(myAction);
        } catch (Exception ex) {
            LOG.warn(ex);
        }
        return result.toString();
    }

    /**
     * Stream video
     *
     * @param request
     * @param response
     * @param path
     * @throws Exception
     */
    @RequestMapping(value = "/streamVideo", method = RequestMethod.GET)
    public void streamVideo(HttpServletRequest request, HttpServletResponse response,
            @RequestParam(value = "path", defaultValue = "") String path) throws Exception {

        MultipartFileSender.fromPath(Paths.get(path))
                .with(request)
                .with(response)
                .serveResource();
    }

    /**
     * Get Video list stored in disk
     *
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "/videos", method = RequestMethod.GET)
    public String getVideos() throws Exception {
        JSONArray fileList = myScreenRecorderService.getVideos();
        return fileList.toString();
    }
    
    @RequestMapping(value = "/video", method = RequestMethod.GET)
    public String getVideos(@RequestParam(value = "uuid", defaultValue = "") String uuid) throws Exception {
        JSONObject fileList = myScreenRecorderService.getVideo(uuid);
        return fileList.toString();
    }

    /**
     * Get speedIndex calculated knowing uuid
     *
     * @param uuid
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "/speedIndex", method = RequestMethod.GET)
    public String speedIndex(@RequestParam(value = "uuid", defaultValue = "") String uuid) throws Exception {

        File file = new File("./recordings/" + uuid + "/" + uuid + ".json");
        Path filepath = Paths.get(file.getAbsolutePath());
        BufferedReader reader = Files.newBufferedReader(filepath);
        String line = reader.readLine();

        return line;
    }

}
