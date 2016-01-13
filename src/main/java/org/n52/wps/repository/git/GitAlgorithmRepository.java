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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.n52.wps.algorithm.annotation.Algorithm;
import org.n52.wps.algorithm.util.CustomClassLoader;
import org.n52.wps.algorithm.util.JavaProcessCompiler;
import org.n52.wps.commons.WPSConfig;
import org.n52.wps.repository.git.module.GitAlgorithmRepositoryCM;
import org.n52.wps.server.AbstractAnnotatedAlgorithm;
import org.n52.wps.server.IAlgorithm;
import org.n52.wps.server.IAlgorithmRepository;
import org.n52.wps.server.ProcessDescription;
import org.n52.wps.server.RepositoryManager;
import org.n52.wps.server.RepositoryManagerSingletonWrapper;
import org.n52.wps.webapp.api.AlgorithmEntry;
import org.n52.wps.webapp.api.ConfigurationCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GitAlgorithmRepository
 *
 * @author Benjamin Pross, Henning Bredel, Daniel Nüst
 *
 */
public class GitAlgorithmRepository implements IAlgorithmRepository {

    private static final Logger logger = LoggerFactory.getLogger(GitAlgorithmRepository.class);

    private DirectoryWatcher repositoryWatcher;

    private String localPath;

    private String remotePath;

    private String filenameRegex;

    private String branchName;

    private Repository localRepo;

    private final Map<IAlgorithm, ProcessDescription> processDescriptions;

    private final Map<String, IAlgorithm> javaAlgorithms;

    private final Map<File, String> file2Wkn;

    private GitAlgorithmRepositoryCM configuration;

    private CustomClassLoader customClassLoader;

    private Collection<DiffEntry> changedFiles;

    private RepositoryManager repoMgr;

    /**
     *
     * @throws UpdateGitAlgorithmsRepositoryException on errors pulling changes into the local repository
     * @throws GitAlgorithmsRepositoryConfigException on configuration errors
     */
    protected GitAlgorithmRepository() throws UpdateGitAlgorithmsRepositoryException, GitAlgorithmsRepositoryConfigException {
        this(true);
    }

    /**
     *
     * @param startRepositoryWatcher start a watcher thread for the repository directory, so that changes to local files trigger a re-deploy of the algorithms
     * @throws UpdateGitAlgorithmsRepositoryException on errors pulling changes into the local repository
     * @throws GitAlgorithmsRepositoryConfigException on configuration errors
     */
    protected GitAlgorithmRepository(boolean startRepositoryWatcher) throws UpdateGitAlgorithmsRepositoryException, GitAlgorithmsRepositoryConfigException {
        this(startRepositoryWatcher, (GitAlgorithmRepositoryCM) WPSConfig.getInstance()
                .getConfigurationModuleForClass(GitAlgorithmRepository.class.getName(), ConfigurationCategory.REPOSITORY),
                RepositoryManagerSingletonWrapper.getInstance());
    }

    /**
     *
     * @param startRepositoryWatcher start a watcher thread for the repository directory, so that changes to local files trigger a re-deploy of the algorithms
     * @param configuration the configuration to use
     * @throws UpdateGitAlgorithmsRepositoryException on errors pulling changes into the local repository
     * @throws GitAlgorithmsRepositoryConfigException on configuration errors
     */
    protected GitAlgorithmRepository(boolean startRepositoryWatcher, GitAlgorithmRepositoryCM configuration, RepositoryManager repositoryManager) throws UpdateGitAlgorithmsRepositoryException, GitAlgorithmsRepositoryConfigException {
        javaAlgorithms = new HashMap<>();
        file2Wkn = new HashMap<>();
        processDescriptions = new HashMap<>();
        changedFiles = new ArrayList<>();

        this.configuration = configuration;
        localPath = configuration.getLocalRepositoryDirectory();
        remotePath = configuration.getRepositoryURL();
        filenameRegex = configuration.getFileNameRegex();
        branchName = configuration.getBranchName();

        repoMgr = repositoryManager;

        logger.info("Initalized GitAlgorithmRepository with settings [local={}, remote={}, reges={}, branch={}]",
                localPath, remotePath, filenameRegex, branchName);

        // set base directory of CustomClassLoader to local git repository directory
        customClassLoader = new CustomClassLoader(localPath);

        File gitDirectory = new File(localPath); // + File.separator + ".git");
        boolean readyToGo = initGitRepository(gitDirectory);

        if (readyToGo) {
            addAlgorithms(gitDirectory, startRepositoryWatcher);
        } else {
            logger.error("git repository not ready to go.");
        }
    }

    private void addAlgorithms(File gitDirectory, boolean startRepositoryWatcher) {
        logger.debug("Adding algorithms based on git directory {} (watching: {})", gitDirectory, startRepositoryWatcher);

        List<Path> algorithmFiles = Collections.emptyList();
        Path p = gitDirectory.toPath();
        try {
            algorithmFiles = findAllFilesInDirectory(p);
        } catch (IOException e) {
            logger.error("Could not find any files in the provided git directory {}", p, e);
        }
        logger.trace("Found {} files.", algorithmFiles.size());

        addJavaAlgorithms(algorithmFiles);
        addRAlgorithms(algorithmFiles);

        //add watcher TODO maybe make configurable
        final File workingCopy = gitDirectory.getParentFile();
        repositoryWatcher = new DirectoryWatcher(workingCopy, new WatchListener() {
            @Override
            public void handleNewFile(String filename) {
                logger.debug("adding/overriding algorithm '{}'", filename);
                final File file = new File(filename);
                Set<Path> algorithm = Collections.singleton(file.toPath());
                if (isJavaFile(file)) {
                    addJavaAlgorithms(algorithm);
                } else if (isRFile(file)) {
                    addRAlgorithms(algorithm);
                }
            }

            @Override
            public void handleDeleteFile(String filename) {

                // TODO untested
                logger.debug("deleting algorithm '{}'", filename);
                File file = new File(filename);
                if (file2Wkn.containsKey(file)) {
                    String wkn = file2Wkn.get(file);
                    if (isJavaFile(file)) {
                        unregisterAlgorithm(wkn, javaAlgorithms);
                    }
                } else if (isRFile(file)) {
                    removeRAlgorithmGlobally(file);
                }
            }

            private void unregisterAlgorithm(String wkn, Map<String, IAlgorithm> algorithms) {
                IAlgorithm algorithm = algorithms.get(wkn);
                processDescriptions.remove(algorithm);
                algorithms.remove(wkn);
            }

            @Override
            public void handleModifiedFile(String filename) {
                logger.debug("modified algorithm '{}'", filename);
                handleNewFile(filename); // TODO sufficient?
            }
        });

        if (startRepositoryWatcher) {
            repositoryWatcher.start();
            logger.trace("Repository watcher started: {}", repositoryWatcher);
        }
    }

    private boolean initGitRepository(File gitDirectory) {
        logger.trace("Initialize git repository at {}", gitDirectory);
        try {
            localRepo = new FileRepository(gitDirectory);
            if (localRepo.getRef("HEAD") == null) {
                logger.trace("HEADless repository, cloning the repository...");
                cloneToLocalRepository();
            } else {
                logger.trace("Repository already exists, updating it...");
                ObjectId old = currentHeadToObjectId();
                changedFiles = updateLocalRepository(old);
            }
            return true;
        } catch (IOException e) {
            logger.error("Couldn't create git directory: {}", gitDirectory, e);
            return false;
        } catch (GitAlgorithmsRepositoryConfigException | UpdateGitAlgorithmsRepositoryException e) {
            logger.error("Could not initialize Git repository!", e);
            return false;
        }
    }

    private Collection<Path> cloneToLocalRepository() throws GitAlgorithmsRepositoryConfigException {
        try (Git git = Git.cloneRepository()
                .setDirectory(new File(localPath))
                .setURI(remotePath)
                .setCloneAllBranches(true)
                .setBranch(branchName)
                .setProgressMonitor(new TextProgressMonitor(new LogWriter(logger)))
                .call()) {
            Path workTree = git.getRepository().getWorkTree().toPath();
            List<Path> files = findAllFilesInDirectory(workTree);
            String filenames = files.stream()
                    .map(f -> f.toAbsolutePath().toString())
                    .collect(Collectors.joining());
            logger.trace("Cloned repository. Local file list: {}", filenames);

            return files;
        } catch (IOException | GitAPIException e) {
            throw new GitAlgorithmsRepositoryConfigException("Cloning failed: " + remotePath, e);
        }
    }

    private Collection<DiffEntry> updateLocalRepository(ObjectId old) throws UpdateGitAlgorithmsRepositoryException {
        Git git = null;
        try {
            git = new Git(localRepo);
            logger.debug("Updating localrepository {} with status {}", git, git.status().call().toString());
            git.checkout().setName(branchName).call();
        } catch (GitAPIException e) {
            throw new UpdateGitAlgorithmsRepositoryException(
                    String.format("Failed to create repo and set branch to %s", branchName), e);
        }

        try {
            logger.debug("Starting pulling from {} ({})", remotePath, old);
            PullResult result = git.pull().call();
            if (!result.isSuccessful()) {
                logger.warn("PULL not successful: {}", createMergeSummaryString(result));
                rollbackFromFailedMerge(git, old);
                return Collections.emptyList();
            }
            ObjectId current = currentHeadToObjectId();
            logger.info("Successfully pulled changes, head now at {}", current);
            return getDiffEntries(git, old, current);

        } catch (IOException | GitAPIException e) {
            throw new UpdateGitAlgorithmsRepositoryException("Failed to pull from " + remotePath, e);
        }
    }

    private String createMergeSummaryString(PullResult pullResult) {
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
        return sb.toString();
    }

    private void rollbackFromFailedMerge(Git git, ObjectId old) throws GitAPIException {
        // old objectId is the one before merge so we should be safe to have the right rollback id
        logger.error("Doing a `reset --hard {}' from failed merge. Pull and merge manually!", old);
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

    private void addJavaAlgorithms(Collection<Path> algorithmFiles) {
        logger.trace("Adding Java algorithms from {} files: {}", algorithmFiles.size(), Arrays.toString(algorithmFiles.toArray()));
        for (Path path : algorithmFiles) {
            File file = path.toFile();
            if (!isJavaFile(file)) {
                logger.debug("Current path is not a java source file: {}", file);
                continue;
            }

            // check if class file exists
            File classFile = new File(file.getAbsolutePath().replace(".java", ".class"));

            // if nothing has changed and class file exists, skip compiling
            if (hasDiffEntry(file) || !classFile.exists()) {
                logger.debug("Compiling Java file: {}", path);
                JavaProcessCompiler.compile(file.getAbsolutePath());
            } else {
                logger.trace("Not compiling file, because it has not changed ({}) or does not exist ({})",
                        hasDiffEntry(file), !classFile.exists());
            }

            String plainFilename = path.getFileName().toString().replace(".java", "");
            try {
                IAlgorithm algorithm = loadJavaAlgorithm(plainFilename);
                String algorithmIdentifier = algorithm.getWellKnownName();
                javaAlgorithms.put(algorithmIdentifier, algorithm);
                file2Wkn.put(file, algorithmIdentifier);
                registerCommonDescriptions(algorithm, plainFilename);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                // TODO refine control flow here!
                logger.error("Exception while trying to add algorithm {}", plainFilename, e);
            }
        }
    }

    private boolean hasDiffEntry(File file) {
        return changedFiles.stream()
                .anyMatch(diff
                        -> diff.getChangeType() == DiffEntry.ChangeType.ADD
                                ? isDiffEntryOf(file, diff.getNewPath())
                                : isDiffEntryOf(file, diff.getOldPath())
                );
    }

    private boolean isDiffEntryOf(File file, String relPathOfDiff) {
        try {
            final String canonicalFilePath = file.getCanonicalPath();
            final Path absoluteFilePath = Paths.get(localPath).resolve(relPathOfDiff);
            final String canonicalDiffEntryPath = absoluteFilePath.toFile().getCanonicalPath();
            return canonicalFilePath.equals(canonicalDiffEntryPath);
        } catch (IOException e) {
            logger.error("Couldn't determine if {} is equal to {}.", file.getAbsoluteFile(), relPathOfDiff, e);
            return false;
        }
    }

    private IAlgorithm loadJavaAlgorithm(String algorithmClassName) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        logger.debug("Loading Java algorithms from file {}", algorithmClassName);
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
            throw new RuntimeException(algorithmClassName + " non assignable to IAlgorithm.");
        }

        boolean isNoProcessDescriptionValid = false;

        for (String supportedVersion : WPSConfig.SUPPORTED_VERSIONS) {
            isNoProcessDescriptionValid = isNoProcessDescriptionValid && !algorithm.processDescriptionIsValid(supportedVersion);
        }

        if (isNoProcessDescriptionValid) {
            logger.warn("Algorithm description is not valid: {}", algorithmClassName);// TODO add version to exception/log
            throw new RuntimeException("Could not load algorithm " + algorithmClassName + ". ProcessDescription Not Valid.");
        }

        return algorithm;
    }

    private void addRAlgorithms(Collection<Path> algorithmFiles) {
        logger.debug("Loading R algorithms from files {}", Arrays.toString(algorithmFiles.toArray()));
        algorithmFiles.stream().filter((file) -> !(!isRFile(file.toFile())))
                .forEach((file) -> {
                    repoMgr.addAlgorithm(file);
                });
    }

    private void registerCommonDescriptions(IAlgorithm algorithm, String plainFilename) {
        String algorithmIdentifier = algorithm.getWellKnownName();
        processDescriptions.put(algorithm, algorithm.getDescription());
        AlgorithmEntry algorithmEntry = new AlgorithmEntry(algorithmIdentifier, true);
        configuration.getAlgorithmEntries().add(algorithmEntry);
        logger.info("Algorithm class registered: {}" + " identifier: {}", plainFilename, algorithmIdentifier);
    }

    private File[] getFileSiblings(File file) {
        logger.trace("Looking for algorithms in the same directory as {} using regex {}", file, filenameRegex);
        final File workingCopy = file.getParentFile();
        File[] files = workingCopy.listFiles((File dir, String name) -> {
            boolean matches = name.matches(filenameRegex);
            // FIXME traverse all directories
            logger.trace("{}dding file {}/{} from local repository.", matches ? "A" : "NOT a", dir, name);
            return matches;
        });
        logger.debug("Found {} files that are candidates for algorithms next to {}", files.length, file);

        return files;
    }

    private List<Path> findAllFilesInDirectory(Path pathToDir) throws IOException {
        List<Path> pathsToFiles = Files.walk(pathToDir)
                .filter(Files::isRegularFile)
                .filter(path -> !path.toString().contains(File.separator + ".git" + File.separator))
                .collect(Collectors.toList());

        return pathsToFiles;
    }

    @Override
    public boolean containsAlgorithm(String arg0) {
        return javaAlgorithms.containsKey(arg0);
    }

    @Override
    public IAlgorithm getAlgorithm(String arg0) {
        return javaAlgorithms.get(arg0);
    }

    @Override
    public Collection<String> getAlgorithmNames() {
        Collection<String> keys = new HashSet<>();
        keys.addAll(javaAlgorithms.keySet());
        return keys;
    }

    @Override
    public ProcessDescription getProcessDescription(String arg0) {
        if (!containsAlgorithm(arg0)) {
            throw new NullPointerException("No 'null' algorithm!");
        }
        IAlgorithm algorithm = getAlgorithm(arg0);
        return processDescriptions.get(algorithm);
    }

    boolean removeRAlgorithmGlobally(File file) {
        if (!isRFile(file)) {
            return false;
        }
        return repoMgr.removeAlgorithm(file);
    }

    private boolean isJavaFile(File file) {
        return file.getAbsolutePath().endsWith(".java");
    }

    private boolean isRFile(File file) {
        return file.getAbsolutePath().toLowerCase().endsWith(".r");
    }

    @Override
    public void shutdown() {
        repositoryWatcher.stop();
        localRepo.close();
    }

    protected static class LogWriter extends PrintWriter {

        public LogWriter(Logger log) {
            super(new InternalWriter(log), true);
        }

    }

    protected static class InternalWriter extends Writer {

        private boolean closed;

        private final Logger internalLog;

        public InternalWriter(Logger log) {
            this.internalLog = log;
        }

        @Override
        public void write(char[] cbuf, int off, int len)
                throws IOException {
            if (closed) {
                throw new IOException("Writer is closed (called write)");
            }
            if (len > 0 && internalLog.isDebugEnabled()) {
                String s = String.copyValueOf(cbuf, off, len);
                s = s.replaceAll("\n", "");
                internalLog.debug(s);
            }
        }

        @Override
        public void flush()
                throws IOException {
            if (closed) {
                throw new IOException("Writer is closed (called flush)");
            }
        }

        @Override
        public void close() {
            closed = true;
        }
    }

}
