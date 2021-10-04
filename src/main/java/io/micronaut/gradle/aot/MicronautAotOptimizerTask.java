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

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@CacheableTask
public abstract class MicronautAotOptimizerTask extends DefaultTask implements OptimizerIO {

    public static final String RESOURCE_FILTER_PATH = "logs/resource-filter.txt";

    @Classpath
    public abstract ConfigurableFileCollection getOptimizerClasspath();

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

    @Inject
    protected abstract FileOperations getFileOperations();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void execute() {
        File outputDir = getOutputDirectory().getAsFile().get();
        getFileOperations().delete(outputDir);
        ExecResult javaexec = getExecOperations().javaexec(spec -> {
            FileCollection classpath = getOptimizerClasspath().plus(getClasspath());
            spec.setClasspath(classpath);
            spec.getMainClass().set("io.micronaut.aot.cli.Main");
            List<String> args = new ArrayList<>(Arrays.asList(
                    "--optimizer-classpath", getOptimizerClasspath().getAsPath(),
                    "--classpath", getClasspath().getAsPath(),
                    "--runtime", getTargetRuntime().get().name().toUpperCase(),
                    "--package", getTargetPackage().get(),
                    "--output", getOutputDirectory().get().getAsFile().toString()
            ));
            if (getConfigurationFile().isPresent()) {
                args.add("--config");
                args.add(getConfigurationFile().getAsFile().get().getAbsolutePath());
            }
            spec.args(args);
        });
        if (javaexec.getExitValue() != 0) {
            throw new GradleException("AOT analysis failed");
        }
    }

}
