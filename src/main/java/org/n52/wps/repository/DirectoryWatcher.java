/**
 * ﻿Copyright (C) 2015 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *       • Apache License, version 2.0
 *       • Apache Software License, version 1.0
 *       • GNU Lesser General Public License, version 3
 *       • Mozilla Public License, versions 1.0, 1.1 and 2.0
 *       • Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.wps.repository;

import java.io.File;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Directory watcher 
 * 
 * @author Benjamin Pross
 *
 */
public class DirectoryWatcher {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryWatcher.class);
    
    public DirectoryWatcher(String directory, final WatchListener listener) {
        
        final Path path = Paths.get(directory);
        
        Thread thread = new Thread() {
            public void run() {
                try {
                    final WatchService service = path.getFileSystem().newWatchService();
                    WatchKey key = path.register(service, StandardWatchEventKinds.ENTRY_CREATE);
                    try {
                        while (true) {
                            for (WatchEvent<?> event : service.take().pollEvents()){
                                Path createdPath = path.resolve((Path)event.context());
                                listener.handleNewFile(createdPath.toString());
                                logger.info("File "+createdPath+" was created.");
                            }
                            key.reset();
                        }
                    } catch (ClosedWatchServiceException e) {
                        logger.info("Service closed");
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                } finally {
                    logger.info("Watcher thread exiting");
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }
    

    public DirectoryWatcher(File directory, WatchListener listener) {
        this(directory.getAbsolutePath(), listener);
    }
}
