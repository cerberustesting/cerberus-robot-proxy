/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.repository;

import org.cerberus.screenrecorder.MyScreenRecorderSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import net.minidev.json.parser.ParseException;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONException;
import org.springframework.stereotype.Component;

/**
 *
 * @author bcivel
 */
@Component
public class UUIDRepository {

    HashMap<String, MyScreenRecorderSession> sessionList;

    @PostConstruct
    public void init() {
        sessionList = new HashMap<String, MyScreenRecorderSession>();
        try {
            this.parse("./recordings");
        } catch (JSONException ex) {
            Logger.getLogger(UUIDRepository.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(UUIDRepository.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParseException ex) {
            Logger.getLogger(UUIDRepository.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public HashMap<String, MyScreenRecorderSession> getAll(){
        return sessionList;
    }
    
    public void addSession(String uuid, MyScreenRecorderSession myScreenRecorderSession) {
        this.sessionList.put(uuid, myScreenRecorderSession);
    }

    public void removeSession(String uuid) {
        this.sessionList.remove(uuid);
    }
    
    public MyScreenRecorderSession get(String uuid){
        return sessionList.get(uuid);
    }
    
    public void writeSession(String uuid) {
        MyScreenRecorderSession session = this.get(uuid);
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(new File("./recordings/" + uuid + "/" + uuid + ".json"), session);
        } catch (IOException ex) {
            Logger.getLogger(MyScreenRecorderSession.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
    public void parse(String path) throws JSONException, FileNotFoundException, IOException, ParseException {

        File root = new File(path);
        File[] list = root.listFiles();

        if (list != null) {
            for (File f : list) {
                if (f.isDirectory()) {
                    parse(f.getAbsolutePath());
                } else {
                    if ("json".equals(FilenameUtils.getExtension(f.getName()))) {
                        Path filepath = Paths.get(f.getAbsolutePath());

                        BufferedReader reader = Files.newBufferedReader(filepath);
                        String line = reader.readLine();
                        ObjectMapper objectMapper = new ObjectMapper();
                        MyScreenRecorderSession msrs = objectMapper.readValue(line, MyScreenRecorderSession.class);
                        this.addSession(msrs.getUuid(), msrs);
                    }
                }
            }
        }
    }
}
