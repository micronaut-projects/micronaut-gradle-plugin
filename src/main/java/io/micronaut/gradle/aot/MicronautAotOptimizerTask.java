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
package io.micronaut.gradle.aot;

import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.util.List;

@CacheableTask
public abstract class MicronautAotOptimizerTask extends AbstractMicronautAotCliTask {

    public static final String RESOURCE_FILTER_PATH = "logs/resource-filter.txt";

    @Internal
    public Provider<Directory> getGeneratedSourcesDirectory() {
        return getOutputDirectory().map(d -> d.dir("sources"));
    }

    @Internal
    public Provider<Directory> getGeneratedClassesDirectory() {
        return getOutputDirectory().map(d -> d.dir("classes"));
    }

    @Internal
    public Provider<RegularFile> getGeneratedOutputResourceFilter() {
        return getOutputDirectory().map(d -> d.file(RESOURCE_FILTER_PATH));
    }

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    public abstract RegularFileProperty getConfigurationFile();

    @Override
    protected void configureExtraArguments(List<String> args) {
        args.add("--output");
        args.add(getOutputDirectory().get().getAsFile().toString());
        if (getConfigurationFile().isPresent()) {
            args.add("--config");
            args.add(getConfigurationFile().getAsFile().get().getAbsolutePath());
        }
    }

}
