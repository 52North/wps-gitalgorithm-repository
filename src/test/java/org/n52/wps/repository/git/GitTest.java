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

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.FetchResult;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitTest {

    private static final Logger logger = LoggerFactory.getLogger(GitTest.class);

    private String localPath, remotePath;

    private Repository localRepo;

    private Git git;

    @Before
    public void init() throws IOException, InvalidRemoteException, TransportException, GitAPIException {
        String tmpdir = System.getProperty("java.io.tmpdir");
        localPath = (tmpdir.endsWith(File.separator) ? tmpdir : (tmpdir + File.separator)) + "tmp-git-dir-" + UUID.randomUUID().toString().substring(0, 5);
        new File(localPath).deleteOnExit();
        remotePath = "https://github.com/bpross-52n/scriptsNprocesses.git";
        localRepo = new FileRepository(localPath + File.separator + ".git");
        logger.info("Cloning {} into {}", remotePath, localPath);
        Git.cloneRepository().setURI(remotePath).setDirectory(new File(localPath)).call();
        logger.info("Cloning succeeded");
        logger.info("Creating new Git repository {}", localPath);
        git = new Git(localRepo);
    }

    @Test
    public void fetchProcesses() throws InvalidRemoteException, TransportException, GitAPIException {
        logger.info("Starting fetch");
        FetchResult result = git.fetch().setCheckFetchedObjects(true).call();
        logger.info("Messages: " + result.getMessages());
    }

}
