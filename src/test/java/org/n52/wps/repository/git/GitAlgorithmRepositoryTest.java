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
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.n52.wps.repository.git;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.Is;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author <a href="mailto:h.bredel@52north.org">Henning Bredel</a>
 */
public class GitAlgorithmRepositoryTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitAlgorithmRepositoryTest.class);

    @Rule
    public TemporaryFolder testRoot = new TemporaryFolder();

    @Mock
    private GitAlgorithmRepository gitAlgorithmRepository;

    private Path cleanRepository;

    @Before
    public void setup() throws GitAPIException, IOException {
        cleanRepository = initRepository();
        MockitoAnnotations.initMocks(this);
    }

    private Path initRepository() throws GitAPIException, IOException {
        File repository = testRoot.newFolder("repository");
        Git.init().setDirectory(repository).call();
        return repository.toPath();
    }

    @Test
    public void initLocalGitRepository() throws IOException, GitAPIException {
        File file = cleanRepository.resolve("hello_world.txt").toFile();
        file.createNewFile();

        Git repoGit = Git.open(cleanRepository.toFile());
        repoGit.add().addFilepattern(file.getPath());
        repoGit.commit().setMessage("initial commit").call();

        File wc = testRoot.newFolder("workingCopy");

        Git wcGit = Git.cloneRepository()
                .setURI(cleanRepository.toUri().toString())
                .setDirectory(wc)
                .call();

        int i = 0;
        Iterator<RevCommit> commits = wcGit.log().all().call().iterator();
        while (commits.hasNext()) {
            i++;
            commits.next();
        }
        MatcherAssert.assertThat(i , Is.is(1));
    }

}
