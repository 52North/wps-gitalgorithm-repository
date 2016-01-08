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
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.n52.wps.repository.git;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.Is;
import static org.hamcrest.core.Is.is;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockitoAnnotations;
import org.n52.wps.repository.git.module.GitAlgorithmRepositoryCM;
import org.n52.wps.server.RepositoryManager;

/**
 *
 * @author <a href="mailto:h.bredel@52north.org">Henning Bredel</a>
 */
public class GitAlgorithmRepositoryTest {

    @Rule
    public TemporaryFolder testRoot = new TemporaryFolder();

    private Path cleanRepository;

    private RepositoryManager repoManager;

    @Before
    public void setup() throws GitAPIException, IOException {
        cleanRepository = initRepository();
        System.out.println("[GitAlgorithmRepositoryTest] Initalized test respository at " + cleanRepository);
        MockitoAnnotations.initMocks(this);

        repoManager = new FileRepository();
    }

    private Path initRepository() throws GitAPIException, IOException {
        File repository = testRoot.newFolder("sourceRepository");
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
        MatcherAssert.assertThat(i, Is.is(1));
    }

    @Test
    public void loadRFileFromRoot() throws IOException, GitAPIException, UpdateGitAlgorithmsRepositoryException, GitAlgorithmsRepositoryConfigException {
        List<String> lines = Lists.newArrayList("# wps.des: id = testgit1;");
        Path testfile = cleanRepository.resolve("test.R");
        Files.write(testfile, lines);
        Git repoGit = Git.open(cleanRepository.toFile());
        repoGit.add().addFilepattern(cleanRepository.relativize(testfile).toString()).call();
        repoGit.commit().setMessage("commit test file").call();

        GitAlgorithmRepositoryCM config = new GitAlgorithmRepositoryCM();
        config.setRepositoryURL(cleanRepository.toFile().toURI().toURL().toString());
        File targetRepo = testRoot.newFolder("testRepoLoadRFileFromRoot");
        config.setLocalRepositoryDirectory(targetRepo.toString());
        GitAlgorithmRepository gitRepo = new GitAlgorithmRepository(false, config, repoManager);

        Assert.assertNotNull(gitRepo);
        MatcherAssert.assertThat(repoManager.getAlgorithms().size(), is(1));
        MatcherAssert.assertThat(repoManager.getAlgorithms().get(0), is(targetRepo.toPath().resolve(testfile.getFileName()).toString()));
    }

    @Ignore("Must fix classpath before algorithm can be loaded.")
    @Test
    public void loadJavaFileFromRoot() throws IOException, GitAPIException, UpdateGitAlgorithmsRepositoryException, GitAlgorithmsRepositoryConfigException, URISyntaxException {
        Path source = Paths.get(Resources.getResource("algorithms/AnnotatedExtensionAlgorithm.java").toURI());
        Path testfile = cleanRepository.resolve(source.getFileName());
        Files.copy(source, testfile);
        Git repoGit = Git.open(cleanRepository.toFile());
        repoGit.add().addFilepattern(cleanRepository.relativize(testfile).toString()).call();
        repoGit.commit().setMessage("commit test file").call();

        GitAlgorithmRepositoryCM config = new GitAlgorithmRepositoryCM();
        config.setRepositoryURL(cleanRepository.toFile().toURI().toURL().toString());
        File targetRepo = testRoot.newFolder("testRepoLoadJavaFileFromRoot");
        config.setLocalRepositoryDirectory(targetRepo.toString());
        GitAlgorithmRepository gitRepo = new GitAlgorithmRepository(false, config, repoManager);

        Assert.assertNotNull(gitRepo);
        MatcherAssert.assertThat(repoManager.getAlgorithms().size(), is(1));
        MatcherAssert.assertThat(repoManager.getAlgorithms().get(0), is(targetRepo.toPath().resolve(testfile.getFileName()).toString()));
        MatcherAssert.assertThat(gitRepo.getAlgorithmNames().size(), is(1));
        MatcherAssert.assertThat(gitRepo.getAlgorithmNames().iterator().next(), is("lala"));
    }

    @Test
    public void loadRFileFromRootInBranch() throws IOException, GitAPIException, UpdateGitAlgorithmsRepositoryException, GitAlgorithmsRepositoryConfigException {
        List<String> lines = Lists.newArrayList("# wps.des: id = testgit2branch;");
        Path testfile = cleanRepository.resolve("testbranch.R");
        Files.write(testfile, lines);

        Git repoGit = Git.open(cleanRepository.toFile());
        repoGit.add().addFilepattern(cleanRepository.relativize(testfile).toString()).call();
        repoGit.commit().setMessage("commit test file").call();

        // create branch and check it out
        String branchName = "testbranch";
        repoGit.branchCreate().setName(branchName).setForce(true).call();
        repoGit.checkout().setName(branchName).call();

        // remove file from master branch
        repoGit.checkout().setName("master").call();
        repoGit.rm().addFilepattern(testfile.getFileName().toString()).call();
        repoGit.commit().setMessage("remove testfile from master");

        GitAlgorithmRepositoryCM config = new GitAlgorithmRepositoryCM();
        config.setRepositoryURL(cleanRepository.toFile().toURI().toURL().toString());
        config.setBranchName(branchName);
        File targetRepo = testRoot.newFolder("loadRFileFromRootInBranch");
        config.setLocalRepositoryDirectory(targetRepo.toString());
        GitAlgorithmRepository gitRepo = new GitAlgorithmRepository(false, config, repoManager);

        Assert.assertNotNull(gitRepo);
        MatcherAssert.assertThat(repoManager.getAlgorithms().size(), is(1));
        MatcherAssert.assertThat(repoManager.getAlgorithms().get(0), is(targetRepo.toPath().resolve(testfile.getFileName()).toString()));
    }

    @Test
    public void loadRFileFromSubdirectory() throws IOException, GitAPIException, UpdateGitAlgorithmsRepositoryException, GitAlgorithmsRepositoryConfigException {
        List<String> lines = Lists.newArrayList("# wps.des: id = testgit3subdir;");
        String dirAndName = "dir/subdir/testsubdir.R";
        Path testfile = cleanRepository.resolve(dirAndName);
        Files.createDirectories(testfile.getParent());
        Files.write(testfile, lines);

        Git repoGit = Git.open(cleanRepository.toFile());
        repoGit.add().addFilepattern("dir").call();
        repoGit.commit().setMessage("commit test file").call();

        GitAlgorithmRepositoryCM config = new GitAlgorithmRepositoryCM();
        config.setRepositoryURL(cleanRepository.toFile().toURI().toURL().toString());
        File targetRepo = testRoot.newFolder("loadRFileFromSubdirectory");
        config.setLocalRepositoryDirectory(targetRepo.toString());
        GitAlgorithmRepository gitRepo = new GitAlgorithmRepository(false, config, repoManager);

        Assert.assertNotNull(gitRepo);
        MatcherAssert.assertThat(repoManager.getAlgorithms().size(), is(1));
        MatcherAssert.assertThat(repoManager.getAlgorithms().get(0), is(targetRepo.toPath().resolve(dirAndName).toString()));
    }

    private static class FileRepository extends RepositoryManager {

        private final List<Object> internalRepository = Lists.newArrayList();

        public FileRepository() {
            //
        }

        @Override
        public boolean addAlgorithm(Object item) {
            this.internalRepository.add(item);
            return true;
        }

        @Override
        public boolean removeAlgorithm(Object item) {
            return super.removeAlgorithm(item);
        }

        @Override
        public List<String> getAlgorithms() {
            return internalRepository.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }

    }

}
