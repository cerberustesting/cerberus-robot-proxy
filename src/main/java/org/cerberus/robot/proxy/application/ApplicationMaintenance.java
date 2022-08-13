/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.application;

import java.io.File;
import java.io.FileFilter;
import org.apache.commons.io.filefilter.AgeFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cerberus.robot.proxy.repository.UUIDRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class ApplicationMaintenance {

    private static final Logger LOG = LogManager.getLogger(ApplicationMaintenance.class);

    @Value("${scheduledtask.clean.delayInMinute}")
    private Integer delay;

    @Autowired
    UUIDRepository uuidRepository;

    /**
     * Scheduled Task that clean media
     */
    @Scheduled(cron = "${scheduledtask.clean}")
    public void cleanMedia() {
        LOG.debug("Clean media");

        File path = new File("./recordings");
        if(!path.exists()){
            path.mkdir();
        }

        long cutoff = System.currentTimeMillis() - (delay * 60 * 1000);
        File[] oldFiles = path.listFiles((FileFilter) new AgeFileFilter(cutoff));

        if (oldFiles != null) {
            for (File file : oldFiles) {
                //First empty if directory
                if (file.isDirectory()) {
                    String[] entries = file.list();
                    for (String s : entries) {
                        File currentFile = new File(file.getPath(), s);
                        LOG.info("Automatically Deleting Files : " + currentFile.getPath());
                        currentFile.delete();
                    }
                    //Delete the object in memory
                    try {
                        uuidRepository.removeSession(file.getName());
                    } catch (Exception ex) {
                        LOG.warn(ex);
                    }
                }
                //Then delete folder
                LOG.info("Automatically Deleting Files : " + file.getPath());
                file.delete();
            }
        }
    }
}
