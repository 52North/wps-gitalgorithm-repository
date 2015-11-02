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
package org.n52.wps.repository.git.module;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.n52.wps.repository.git.GitAlgorithmRepository;
import org.n52.wps.webapp.api.AlgorithmEntry;
import org.n52.wps.webapp.api.ClassKnowingModule;
import org.n52.wps.webapp.api.ConfigurationCategory;
import org.n52.wps.webapp.api.ConfigurationKey;
import org.n52.wps.webapp.api.FormatEntry;
import org.n52.wps.webapp.api.types.ConfigurationEntry;
import org.n52.wps.webapp.api.types.StringConfigurationEntry;

/**
 * Configuration module for the GitAlgorithmRepository
 * 
 * @author Benjamin Pross
 *
 */
public class GitAlgorithmRepositoryCM extends ClassKnowingModule {

    public static final String repositoryURLKey = "repository_url";

    public static final String branchNameKey = "branch_name";

    public static final String fileNameRegexKey = "file_name_regex";

    public static final String localRepositoryDirectoryKey = "local_repository_directory";

    private ConfigurationEntry<String> repositoryURLEntry = new StringConfigurationEntry(repositoryURLKey, "Remote repository URL",
            "URL of remote repository, e.g. 'https://github.com/username/repository.git'.", true, "-");

    private ConfigurationEntry<String> branchNameEntry = new StringConfigurationEntry(branchNameKey, "Branch name", "Name of branch to checkout.", true, "master");

    private ConfigurationEntry<String> fileNameRegexEntry = new StringConfigurationEntry(fileNameRegexKey, "Filename REGEX ",
            "REGEX to specify which directories or files to choose from the repository.", true, "^.*\\.java$|^.*\\.R$");

    private ConfigurationEntry<String> localRepositoryDirectoryEntry = new StringConfigurationEntry(localRepositoryDirectoryKey, "Local repository directory",
            "Path to the local repository directory.", true, "d:\\tmp\\gitrepositories");

    private List<? extends ConfigurationEntry<?>> configurationEntries = Arrays.asList(repositoryURLEntry, branchNameEntry, fileNameRegexEntry, localRepositoryDirectoryEntry);

    private String repositoryURL;

    private String branchName;

    private String fileNameRegex;

    private String localRepositoryDirectory;

    private boolean isActive = false;

    private List<AlgorithmEntry> algorithmEntries;

    public GitAlgorithmRepositoryCM() {
        algorithmEntries = new ArrayList<>();
    }

    @Override
    public String getModuleName() {
        return "GitAlgorithmRepository";
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public void setActive(boolean active) {
        this.isActive = active;
    }

    public String getRepositoryURL() {
        return repositoryURL;
    }

    @ConfigurationKey(
            key = repositoryURLKey)
    public void setRepositoryURL(String repositoryURL) {
        this.repositoryURL = repositoryURL;
    }

    public String getBranchName() {
        return branchName;
    }

    @ConfigurationKey(
            key = branchNameKey)
    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getFileNameRegex() {
        return fileNameRegex;
    }

    @ConfigurationKey(
            key = fileNameRegexKey)
    public void setFileNameRegex(String fileNameRegex) {
        this.fileNameRegex = fileNameRegex;
    }

    public String getLocalRepositoryDirectory() {
        return localRepositoryDirectory;
    }

    @ConfigurationKey(
            key = localRepositoryDirectoryKey)
    public void setLocalRepositoryDirectory(String localRepositoryDirectory) {
        this.localRepositoryDirectory = localRepositoryDirectory;
    }

    @Override
    public ConfigurationCategory getCategory() {
        return ConfigurationCategory.REPOSITORY;
    }

    @Override
    public List<? extends ConfigurationEntry<?>> getConfigurationEntries() {
        return configurationEntries;
    }

    @Override
    public List<AlgorithmEntry> getAlgorithmEntries() {
        return algorithmEntries;
    }

    @Override
    public List<FormatEntry> getFormatEntries() {
        return null;
    }

    @Override
    public String getClassName() {
        return GitAlgorithmRepository.class.getName();
    }

}
