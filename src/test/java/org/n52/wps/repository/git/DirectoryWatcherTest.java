/*
 * ﻿Copyright (C) 2018 52°North Initiative for Geospatial Open Source
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
package org.n52.wps.repository.git;

import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.n52.wps.repository.git.DirectoryWatcher;
import org.n52.wps.repository.git.WatchListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.class)
public class DirectoryWatcherTest {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryWatcherTest.class);

    @Mock
    WatchListener watchlistener;

    @Test
    public void testCreatNewFileEvent() {

        String tmpdir = System.getProperty("java.io.tmpdir");

        String tmpWatchedDir = (tmpdir.endsWith(File.separator) ? tmpdir : (tmpdir + File.separator)) + "tmp-git-dir-" + UUID.randomUUID().toString().substring(0, 5);

        new File(tmpWatchedDir).mkdir();

        logger.info("Starting to watch  directory {}", tmpWatchedDir);

        DirectoryWatcher watcher = new DirectoryWatcher(tmpWatchedDir, watchlistener);
        watcher.start();

        //give the watcher a little time
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            /*
             * ignore
             */
        }

        File newfile = new File(tmpWatchedDir + File.separator + "newFile.txt");

        try {
            newfile.createNewFile();

            logger.info("Created {}", newfile.getAbsolutePath());

        } catch (IOException e) {
            logger.error("Could not create new file, {}.", newfile.getAbsolutePath());
            logger.error(e.getMessage());
        }

        verify(watchlistener).handleNewFile(newfile.getAbsolutePath());
    }

}
