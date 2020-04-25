package org.cerberus.screenrecorder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    MySelenium mySelenium;

    /**
     * Check server is Up
     *
     * @param port
     * @return
     */
    @RequestMapping("/launch")
    public String launch(@RequestParam(value = "url", defaultValue = "") String url) {
        JSONObject result = new JSONObject();
        try {
            // Check if url is empty
            if (url.equals("")) {
                result.put("message", "url is empty. Please feed a url value");
                result.put("url", "");
                result.put("status", "Error");
                return result.toString();
            }
            // Launch the test and get the result
            result = mySelenium.launch(url);
        } catch (Exception ex) {
            LOG.warn(ex);
        }
        return result.toString();
    }

    @RequestMapping(path = "/getVideo", method = RequestMethod.GET)
    public ResponseEntity<Resource> getVideo(String param) throws IOException {

        File file = MyScreenRecorder.getVideo();

        HttpHeaders header = new HttpHeaders();
        header.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=video.avi");
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

}
