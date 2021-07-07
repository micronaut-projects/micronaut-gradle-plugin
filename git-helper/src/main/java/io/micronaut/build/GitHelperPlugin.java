/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class GitHelperPlugin implements Plugin<Settings> {
    private IncludedGitRepositories gitRepositories;
    private StartParameter startParameter;

    @Override
    public void apply(Settings target) {
        startParameter = target.getGradle().getStartParameter();
        gitRepositories = target.getExtensions().create("includedGitRepositories", IncludedGitRepositories.class);
        gitRepositories.checkoutDirectory(
                target.getProviders().provider(() -> {
                    File settingsDir = target.getSettingsDir();
                    return new File(settingsDir, ".gradle/checkouts");
                })
        );
        target.getGradle().settingsEvaluated(unused -> {
            List<GitRepositorySpec> repositories = gitRepositories.getRepositories();
            for (GitRepositorySpec repository : repositories) {
                includeRepository(repository, target);
            }
        });
    }

    private void includeRepository(GitRepositorySpec repository, Settings target) {
        String repoUrl = repository.getUrl().get();
        String repoName = extractRepoName(repoUrl);
        String branch = repository.getBranch().get();
        File checkoutDir = new File(gitRepositories.getCheckoutDirectory().get().getAsFile(), repoName);
        if (!startParameter.isOffline()) {
            try {
                if (!checkoutDir.exists()) {
                    System.out.println("Checking out " + repoUrl + " branch " + branch);
                    Git.cloneRepository()
                            .setURI(repoUrl)
                            .setDirectory(checkoutDir)
                            .setBranch(branch)
                            .call();
                } else {
                    System.out.println("Updating " + repoUrl + " branch " + branch);
                    Git.open(checkoutDir)
                            .fetch()
                            .call();
                    Git.open(checkoutDir)
                            .checkout()
                            .setName(branch)
                            .call();
                    Git.open(checkoutDir)
                            .pull()
                            .call();
                }
            } catch (GitAPIException | IOException e) {
                throw new GradleException("Unable to checkout repository '" + repoName + "'", e);
            }
            File includedDir = checkoutDir;
            if (repository.getDirectory().isPresent()) {
                includedDir = new File(includedDir, repository.getDirectory().get());
            }
            target.includeBuild(includedDir);
        }
    }

    private String extractRepoName(String repoUrl) {
        String repoName = repoUrl.substring(repoUrl.lastIndexOf("/"));
        if (repoName.endsWith(".git")) {
            repoName = repoName.substring(0, repoName.indexOf(".git"));
        }
        return repoName;
    }
}
