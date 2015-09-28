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
package org.n52.wps.repository.git;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.n52.wps.algorithm.annotation.Algorithm;
import org.n52.wps.algorithm.util.CustomClassLoader;
import org.n52.wps.algorithm.util.JavaProcessCompiler;
import org.n52.wps.commons.WPSConfig;
import org.n52.wps.repository.git.module.GitAlgorithmRepositoryCM;
import org.n52.wps.server.AbstractAnnotatedAlgorithm;
import org.n52.wps.server.IAlgorithm;
import org.n52.wps.server.IAlgorithmRepository;
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
public class GitAlgorithmRepository implements IAlgorithmRepository {

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

    public GitAlgorithmRepository() throws UpdateGitAlgorithmsRepositoryException, GitAlgorithmsRepositoryConfigException {

        rAlgorithms = new HashMap<>();
        javaAlgorithms = new HashMap<>();
        processDescriptions = new HashMap<>();
        changedFiles = new ArrayList<>();

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
        // set base directory of CustomClassLoader to local git repository directory
        customClassLoader = new CustomClassLoader(localPath);

        String localGitRepoDirectoryPath = localPath + File.separator + ".git";

        File localGitRepoDirectory = new File(localGitRepoDirectoryPath);
        initGitRepository(localGitRepoDirectoryPath, localGitRepoDirectory);

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

    private void initGitRepository(String pathWorkingCopy, File localGitRepoDirectory) throws GitAlgorithmsRepositoryConfigException, UpdateGitAlgorithmsRepositoryException {
        try {
            localRepo = new FileRepository(pathWorkingCopy);
            if (localGitRepoDirectory.exists()) {
                ObjectId old = currentHeadToObjectId();
                changedFiles = updateLocalRepository(old);
            } else {
                cloneToLocalRepository();
            }
        } catch (IOException e) {
            throw new GitAlgorithmsRepositoryConfigException("Could not create local repository with: " + pathWorkingCopy, e);
        }
    }

    private void cloneToLocalRepository() throws GitAlgorithmsRepositoryConfigException {
        try {
            Git.cloneRepository().setURI(remotePath).setDirectory(new File(localPath)).call();
        } catch (GitAPIException e) {
            throw new GitAlgorithmsRepositoryConfigException("Cloning failed: " + remotePath, e);
        }
    }

    private Collection<DiffEntry> updateLocalRepository(ObjectId old) throws UpdateGitAlgorithmsRepositoryException {
        try {
            Git git = new Git(localRepo);
            logger.debug("Starting pulling from {} ({})", remotePath, old);
            PullResult result = git.pull().call();
            if ( !result.isSuccessful()) {
                printMergeSummary(result);
                rollbackFromFailedMerge(git, old);
                return Collections.emptyList();
            }
            logger.info("Successfully pulled changes.");
            ObjectId current = currentHeadToObjectId();
            return getDiffEntries(git, old, current);

        } catch (IOException | GitAPIException e) {
            throw new UpdateGitAlgorithmsRepositoryException("Failed to pull from " + remotePath, e);
        }
    }

    private void printMergeSummary(PullResult pullResult) {
        StringBuilder sb = new StringBuilder();
        MergeResult result = pullResult.getMergeResult();
        sb.append("Merge failed with status ").append(result.getMergeStatus().name());
        result.getFailingPaths().entrySet()
                .stream()
                .forEach(failed -> sb
                        .append(failed.getKey())
                        .append(" -> ")
                        .append(failed.getValue())
                        .append("\n"));
    }

    private void rollbackFromFailedMerge(Git git, ObjectId old) throws GitAPIException {
        // old objectId is the one before merge so we should be safe to have the right rollback id
        logger.warn("Doing a `reset --hard {}' from faild merge. Pull and merge manually!", old);
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef(old.toString()).call();
    }

    private Collection<DiffEntry> getDiffEntries(Git git, ObjectId old, ObjectId newer) throws IOException, GitAPIException {
        ObjectReader reader = localRepo.newObjectReader();
        CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
        oldTreeIter.reset(reader, old);
        CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
        newTreeIter.reset(reader, newer);
        return git.diff()
                .setNewTree(newTreeIter)
                .setOldTree(oldTreeIter)
                .call();
    }

    private ObjectId currentHeadToObjectId() throws RevisionSyntaxException, IOException {
        return localRepo.resolve("HEAD^{tree}");
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
            try {
                IAlgorithm algorithm = loadJavaAlgorithm(plainFilename);
                String algorithmIdentifier = algorithm.getWellKnownName();
                javaAlgorithms.put(algorithmIdentifier, algorithm);
                registerCommonDescriptions(algorithm, plainFilename);
            } catch (Exception e) {
                // TODO refine control flow here!
                logger.error("Exception while trying to add algorithm {}", plainFilename);
                logger.error(e.getMessage());
            }
        }
    }

    private IAlgorithm loadJavaAlgorithm(String algorithmClassName) throws Exception {
        Class<?> algorithmClass = customClassLoader.loadClass(algorithmClassName);
        IAlgorithm algorithm;
        if (IAlgorithm.class.isAssignableFrom(algorithmClass)) {
            algorithm = IAlgorithm.class.cast(algorithmClass.newInstance());
        } else if (algorithmClass.isAnnotationPresent(Algorithm.class)) {
            // we have an annotated algorithm that doesn't implement IAlgorithm
            // wrap it in a proxy class
            algorithm = new AbstractAnnotatedAlgorithm.Proxy(algorithmClass);
        } else {
            // algorithms can have helper classes
            throw new Exception(algorithmClassName + " non assignable to IAlgorithm.");
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

    private void addRAlgorithms(File[] algorithmFiles) {

        // TODO

    }

    private void registerCommonDescriptions(IAlgorithm algorithm, String plainFilename) {
        String algorithmIdentifier = algorithm.getWellKnownName();
        processDescriptions.put(algorithm, algorithm.getDescription());
        AlgorithmEntry algorithmEntry = new AlgorithmEntry(algorithmIdentifier, true);
        gitAlgorithmRepoConfigModule.getAlgorithmEntries().add(algorithmEntry);
        logger.info("Algorithm class registered: {}" + " identifier: {}", plainFilename, algorithmIdentifier);
    }

    private File[] getFiles(File localGitRepoDirectory){
        final File workingCopy = localGitRepoDirectory.getParentFile();
        return workingCopy.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.matches(filenameRegex);
            }
        });
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

    boolean removeAlgorithm(Object arg0) {
        // TODO remove?
        return false;
    }

    private boolean isJavaFile(File file) {
        return file.getAbsolutePath().endsWith(".java");
    }

    private boolean isRFile(File file) {
        return file.getAbsolutePath().toLowerCase().endsWith(".r");
    }

    @Override
    public void shutdown() {
        directoryWatch.stop();
        localRepo.close();
    }

}
