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
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.FetchResult;
import org.n52.wps.algorithm.annotation.Algorithm;
import org.n52.wps.algorithm.util.CustomClassLoader;
import org.n52.wps.algorithm.util.JavaProcessCompiler;
import org.n52.wps.commons.WPSConfig;
import org.n52.wps.repository.module.GitAlgorithmRepositoryCM;
import org.n52.wps.server.AbstractAnnotatedAlgorithm;
import org.n52.wps.server.IAlgorithm;
import org.n52.wps.server.ITransactionalAlgorithmRepository;
import org.n52.wps.server.ProcessDescription;
import org.n52.wps.webapp.api.AlgorithmEntry;
import org.n52.wps.webapp.api.ConfigurationCategory;
import org.n52.wps.webapp.api.ConfigurationModule;
import org.n52.wps.webapp.api.types.ConfigurationEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GitAlgorithmRepository
 *
 * @author Benjamin Pross
 *
 */
public class GitAlgorithmRepository implements ITransactionalAlgorithmRepository {

    private static final Logger logger = LoggerFactory.getLogger(GitAlgorithmRepository.class);

    private final DirectoryWatcher directoryWatch;

    private String localPath, remotePath, filenameRegex;

    private Repository localRepo;

    private final Map<IAlgorithm, ProcessDescription> processDescriptions;

    private final Map<String, IAlgorithm> javaAlgorithms;

    private final Map<String, IAlgorithm> rAlgorithms;

    private ConfigurationModule gitAlgorithmRepoConfigModule;

    private CustomClassLoader customClassLoader;

    private List<String> changedFiles;

    public GitAlgorithmRepository() throws IOException {

        rAlgorithms = new HashMap<>();
        javaAlgorithms = new HashMap<>();
        processDescriptions = new HashMap<>();

        gitAlgorithmRepoConfigModule = WPSConfig.getInstance().getConfigurationModuleForClass(this.getClass().getName(), ConfigurationCategory.REPOSITORY);

        List<? extends ConfigurationEntry<?>> configEntries = gitAlgorithmRepoConfigModule.getConfigurationEntries();

        //TODO use specified git branch
        for (ConfigurationEntry<?> configurationEntry : configEntries) {
            if (configurationEntry.getKey().equals(GitAlgorithmRepositoryCM.localRepositoryDirectoryKey)) {
                localPath = (String) configurationEntry.getValue();
            } else if (configurationEntry.getKey().equals(GitAlgorithmRepositoryCM.repositoryURLKey)) {
                remotePath = (String) configurationEntry.getValue();
            } else if (configurationEntry.getKey().equals(GitAlgorithmRepositoryCM.fileNameRegexKey)) {
                filenameRegex = (String) configurationEntry.getValue();
            }
        }
        // set base directory of CustomClassLoader to local git repository
        // directory
        customClassLoader = new CustomClassLoader(localPath);

        String localGitRepoDirectoryPath = localPath + File.separator + ".git";

        File localGitRepoDirectory = new File(localGitRepoDirectoryPath);

        changedFiles = new ArrayList<>();

        if (localGitRepoDirectory.exists()) {
            // check for updates, fetch
            localRepo = new FileRepository(localGitRepoDirectoryPath);
            Git git = new Git(localRepo);
            logger.debug("Starting fetch from " + remotePath);
            FetchResult result;
            try {
                result = git.fetch().setCheckFetchedObjects(true).call();

                if (!result.getMessages().trim().isEmpty()) {
                    // there are changes, check messages and add changed files to list
                    //TODO
                }

            } catch (GitAPIException e) {
                logger.error("Failed to fetch from " + remotePath, e);
            }

        } else {
            // get algorithms from git
            try {
                Git.cloneRepository().setURI(remotePath).setDirectory(new File(localPath)).call();
            } catch (GitAPIException e) {
                logger.error("Could not clone remote repository: " + remotePath, e);
                // TODO exception?!
            }
        }
        // get algorithms
        File[] algorithmFiles = getFiles(localGitRepoDirectory);

        addJavaAlgorithms(algorithmFiles);
        addRAlgorithms(algorithmFiles);

        //add watcher TODO maybe make configurable
        directoryWatch = new DirectoryWatcher(localGitRepoDirectory.getParentFile(), new WatchListener() {
            @Override
            public void handleNewFile(String filename) {
                final File file = new File(filename);
                if (isJavaFile(file)) {
                    addJavaAlgorithms(new File[]{ file});
                } else if (isRFile(file)) {
                    addRAlgorithms(new File[]{ file });
                }
            }

            @Override
            public void handleDeleteFile(String filename) {
                // TODO
            }

            @Override
            public void handleModifiedFile(String filename) {
                handleNewFile(filename); // TODO sufficient?
            }
        }).start();
    }

    @Override
    public boolean containsAlgorithm(String arg0) {
        return javaAlgorithms.containsKey(arg0)
                || rAlgorithms.containsKey(arg0);
    }

    @Override
    public IAlgorithm getAlgorithm(String arg0) {
        return javaAlgorithms.containsKey(arg0)
                ? javaAlgorithms.get(arg0)
                : rAlgorithms.get(arg0);
    }

    @Override
    public Collection<String> getAlgorithmNames() {
        Collection<String> keys = new HashSet<>();
        keys.addAll(javaAlgorithms.keySet());
        keys.addAll(rAlgorithms.keySet());
        return keys;
    }

    @Override
    public ProcessDescription getProcessDescription(String arg0) {
        if ( !containsAlgorithm(arg0)) {
            throw new NullPointerException("No 'null' algorithm!");
        }
        IAlgorithm algorithm = javaAlgorithms.containsKey(arg0)
                ? javaAlgorithms.get(arg0)
                : rAlgorithms.get(arg0);
        return processDescriptions.get(algorithm);
    }

    @Override
    public void shutdown() {
        directoryWatch.stop();
        localRepo.close();
    }

    @Override
    public boolean removeAlgorithm(Object arg0) {
        // TODO remove?
        return false;
    }

    @Override
    public boolean addAlgorithm(Object processID) {
        if (!(processID instanceof String)) {
            return false;
        }
        String algorithmClassName = (String) processID;

        try {

            IAlgorithm algorithm = loadJavaAlgorithm(algorithmClassName);

            // Use fully qualified name for algorithm id
            String algorithmIdentifier = algorithm.getWellKnownName();
            processDescriptions.put(algorithm, algorithm.getDescription());
            javaAlgorithms.put(algorithmIdentifier, algorithm);
            AlgorithmEntry algorithmEntry = new AlgorithmEntry(algorithmIdentifier, true);
            gitAlgorithmRepoConfigModule.getAlgorithmEntries().add(algorithmEntry);
            logger.info("Algorithm class registered: {}" + " identifier: {}", algorithmClassName, algorithmIdentifier);

            return true;
        } catch (Exception e) {
            logger.error("Exception while trying to add algorithm {}", algorithmClassName);
            logger.error(e.getMessage());
        }

        return false;

    }

    private IAlgorithm loadJavaAlgorithm(String algorithmClassName) throws Exception {
        Class<?> algorithmClass = customClassLoader.loadClass(algorithmClassName);
        IAlgorithm algorithm = null;
        if (IAlgorithm.class.isAssignableFrom(algorithmClass)) {
            algorithm = IAlgorithm.class.cast(algorithmClass.newInstance());
        } else if (algorithmClass.isAnnotationPresent(Algorithm.class)) {
            // we have an annotated algorithm that doesn't implement IAlgorithm
            // wrap it in a proxy class
            algorithm = new AbstractAnnotatedAlgorithm.Proxy(algorithmClass);
        } else {
            throw new Exception("Could not load algorithm " + algorithmClassName + " does not implement IAlgorithm or have a Algorithm annotation.");
        }

        boolean isNoProcessDescriptionValid = false;

        for (String supportedVersion : WPSConfig.SUPPORTED_VERSIONS) {
            isNoProcessDescriptionValid = isNoProcessDescriptionValid && !algorithm.processDescriptionIsValid(supportedVersion);
        }

        if (isNoProcessDescriptionValid) {
            logger.warn("Algorithm description is not valid: " + algorithmClassName);// TOD add version to exception/log
            throw new Exception("Could not load algorithm " + algorithmClassName + ". ProcessDescription Not Valid.");
        }

        return algorithm;
    }

    private File[] getFiles(File localGitRepoDirectory){
        // compile algorithms
        File[] files = localGitRepoDirectory.getParentFile().listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir,
                    String name) {
                return name.matches(filenameRegex);
            }
        });

        return files;
    }


    private void addRAlgorithms(File[] algorithmFiles) {

        // TODO

    }


    private void addJavaAlgorithms(File[] algorithmFiles){

        for (File file : algorithmFiles) {
            if ( !isJavaFile(file)) {
                continue;
            }

            // check if class file exists
            File classFile = new File(file.getAbsolutePath().replace(".java", ".class"));

            // if nothing has changed and class file exists, skip compiling
            if (changedFiles.contains(file.getName()) | !classFile.exists()) {
                JavaProcessCompiler.compile(file.getAbsolutePath());
            }

            String plainFilename = file.getName().replace(".java", "");

            // load algorithm
            addAlgorithm(plainFilename);
        }
    }

    private boolean isJavaFile(File file) {
        return file.getAbsolutePath().endsWith(".java");
    }

    private boolean isRFile(File file) {
        return file.getAbsolutePath().toLowerCase().endsWith(".r");
    }

}
