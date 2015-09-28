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
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

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

    private final ExecutorService service = Executors.newSingleThreadExecutor();

    private final WatchListener listener;

    private final Path directory;

    private boolean running;

    public DirectoryWatcher(File directory, final WatchListener listener) {
        this(directory.getAbsolutePath(), listener);
    }

    public DirectoryWatcher(String directory, final WatchListener listener) {
        this.directory = Paths.get(directory);
        this.listener = listener;
    }

    public DirectoryWatcher start() {
        try {
            running = true;
            final WatchService watchService = directory.getFileSystem().newWatchService();
            WatchKey key = directory.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            service.execute(new Thread() {
                @Override
                public void run() {
                    handleEvents(watchService);
                }
            });
        } catch (IOException e) {
            logger.error("Could not start watching directory {}", directory.toString(), e);
        }
        return this;
    }

    public void stop() {
        running = false;
        service.shutdownNow();
    }

    private void handleEvents(WatchService watchService) {
        try {
            while (running) {
                WatchKey key = watchService.take(); // waits, if empty
                for (WatchEvent<?> event : key.pollEvents()) {
                    handleEvent(event);
                    if ( !key.reset()) {
                        stop(); //got invalid
                        break;
                    }
                }
            }
        } catch (ClosedWatchServiceException e) {
            logger.info("Service closed", e);
        } catch (InterruptedException e) {
            logger.error("Could not handle event(s) on directory {}", directory, e);
        } finally {
            logger.info("Watcher thread exiting");
        }
    }

    private void handleEvent(WatchEvent<?> event) {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            return;
        }

        Path changed = directory.resolve((Path) event.context());
        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
            listener.handleNewFile(changed.toString());
            logger.info("File " + changed + " was created.");
        } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
            listener.handleDeleteFile(changed.toString());
            // TODO
        } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
            logger.info("no handle to update modified entries.");
            // TODO
        }
    }

}
