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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

abstract class AbstractMicronautAotCliTask extends DefaultTask implements OptimizerIO {

    @Classpath
    public abstract ConfigurableFileCollection getOptimizerClasspath();

    @Inject
    protected abstract FileOperations getFileOperations();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Input
    public abstract Property<Boolean> getDebug();

    @Input
    public abstract Property<String> getAotVersion();

    @Input
    @Optional
    public abstract MapProperty<String, String> getEnvironmentVariables();

    @Input
    @Optional
    public abstract ListProperty<String> getJvmArgs();

    protected AbstractMicronautAotCliTask() {
        getDebug().convention(false);
    }

    protected void configureExtraArguments(List<String> args) {

    }

    protected void onSuccess(File outputDir) {

    }

    @TaskAction
    public final void execute() throws IOException {
        File outputDir = getOutputDirectory().getAsFile().get();
        getFileOperations().delete(outputDir);
        File argFile = Files.createTempFile("aot", "args").toFile();
        try {
            ExecResult javaexec = getExecOperations().javaexec(spec -> {
                FileCollection aotClasspath = getOptimizerClasspath();
                FileCollection classpath = getOptimizerClasspath().plus(getClasspath());
                spec.setClasspath(aotClasspath);
                spec.getMainClass().set("io.micronaut.aot.cli.Main");
                var args = new ArrayList<>(Arrays.asList(
                    "--classpath", classpath.getAsPath(),
                    "--runtime", getTargetRuntime().get().name().toUpperCase(),
                    "--package", getTargetPackage().get()
                ));
                maybeAddOptimizerClasspath(args, getClasspath());
                configureExtraArguments(args);
                boolean useArgFile = true;
                try (var wrt = new PrintWriter(new FileWriter(argFile))) {
                    args.forEach(arg -> wrt.println(escapeArg(arg)));
                } catch (IOException e) {
                    useArgFile = false;
                }
                if (useArgFile) {
                    spec.args("@" + argFile.getAbsolutePath());
                } else {
                    spec.args(args);
                }
                getLogger().info("Running AOT optimizer {} with parameters: {}", useArgFile ? "using arg file" : "directly", args);
                var jvmArgs = new ArrayList<String>();
                if (Boolean.TRUE.equals(getDebug().get())) {
                    getLogger().info("Running with debug enabled");
                    jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
                }
                if (getJvmArgs().isPresent()) {
                    jvmArgs.addAll(getJvmArgs().get());
                }
                if (!jvmArgs.isEmpty()) {
                    spec.jvmArgs(jvmArgs);
                }
                if (getEnvironmentVariables().isPresent()) {
                    spec.environment(getEnvironmentVariables().get());
                }
            });
            if (javaexec.getExitValue() != 0) {
                throw new GradleException("AOT analysis failed");
            }
        } finally {
            onSuccess(outputDir);
            Files.delete(argFile.toPath());
        }
    }

    private void maybeAddOptimizerClasspath(List<String> args, ConfigurableFileCollection classpath) {
        String version = getAotVersion().get();
        if (version.startsWith("1.0.0-M")) {
            int milestone = Integer.parseInt(version.substring(version.indexOf("-M") + 2));
            if (milestone < 6) {
                args.add("--optimizer-classpath");
                args.add(classpath.getAsPath());
            }
        }
    }

    private static String escapeArg(String arg) {
        arg = arg.replace("\\", "\\\\");
        if (arg.contains(" ")) {
            arg = "\"" + arg + "\"";
        }
        return arg;
    }

}
