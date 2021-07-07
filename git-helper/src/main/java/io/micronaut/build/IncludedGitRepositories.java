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

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class IncludedGitRepositories {
    private final List<GitRepositorySpec> repositories = new ArrayList<>();

    @Inject
    protected abstract ObjectFactory getObjects();

    /**
     * The directory where git repositories will be checked out
     */
    public abstract DirectoryProperty getCheckoutDirectory();

    void checkoutDirectory(Provider<File> provider) {
        DirectoryProperty directoryProperty = getObjects().directoryProperty();
        directoryProperty.fileProvider(provider);
        getCheckoutDirectory().set(directoryProperty);
    }

    public void repo(Action<? super GitRepositorySpec> spec) {
        GitRepositorySpec repo = getObjects().newInstance(GitRepositorySpec.class);
        repo.getBranch().convention("master");
        spec.execute(repo);
        repositories.add(repo);
    }

    public List<GitRepositorySpec> getRepositories() {
        return repositories;
    }
}
