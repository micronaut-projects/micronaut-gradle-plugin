/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;

/**
 * Rewrites a runtime class path with Micronaut AOT's {@code ClassPathTransform}, on the project's Java
 * toolchain. Its output directory holds the rewritten copies and {@value #CLASS_PATH_FILE}, which maps each
 * entry of the class path to what is packaged in its place.
 *
 * @since 5.1.0
 */
@CacheableTask
public abstract class ClassPathTransformTask extends DefaultTask {

    /** The file of the output directory that maps each class path entry to what is packaged. */
    public static final String CLASS_PATH_FILE = "class-path.txt";

    /** The file of the output directory that holds what the steps did. */
    public static final String REPORT_FILE = "report.txt";

    /**
     * The application's own output, in class path order: never stripped.
     *
     * @return the application entries
     */
    @Classpath
    public abstract ConfigurableFileCollection getApplication();

    /**
     * The runtime dependencies, in class path order.
     *
     * @return the dependency entries
     */
    @Classpath
    public abstract ConfigurableFileCollection getDependencies();

    /**
     * The third-party JARs among the dependencies, which are the only entries that are stripped.
     *
     * @return the third-party JARs
     */
    @Classpath
    public abstract ConfigurableFileCollection getThirdPartyJars();

    /**
     * The class path of {@code micronaut-aot-bytecode}.
     *
     * @return the transform class path
     */
    @Classpath
    public abstract ConfigurableFileCollection getTransformClasspath();

    /**
     * Whether lambdas are desugared.
     *
     * @return the desugar property
     */
    @Input
    public abstract Property<Boolean> getDesugarLambdas();

    /**
     * Whether the local-variable tables of the third-party JARs are stripped.
     *
     * @return the strip property
     */
    @Input
    public abstract Property<Boolean> getStripLocalVariables();

    /**
     * The launcher of the project's Java toolchain, which runs the transform.
     *
     * @return the launcher
     */
    @Internal
    public abstract Property<JavaLauncher> getJavaLauncher();

    /**
     * The runtime version of the launcher: the rewritten bytes depend on the JDK build.
     *
     * @return the runtime version
     */
    @Input
    public Provider<String> getJavaRuntimeVersion() {
        return getJavaLauncher().map(launcher -> launcher.getMetadata().getJavaRuntimeVersion());
    }

    /**
     * The directory that holds the copies, {@value #CLASS_PATH_FILE} and {@value #REPORT_FILE}.
     *
     * @return the output directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @TaskAction
    void transform() {
        JavaLauncher launcher = getJavaLauncher().get();
        int feature = launcher.getMetadata().getLanguageVersion().asInt();
        if (feature < 25) {
            throw new GradleException("Rewriting the class path (micronaut.classPathTransform) needs a Java toolchain of"
                + " version 25 or later, but the project's toolchain is version " + feature + ".");
        }
        File output = getOutputDirectory().get().getAsFile();
        getFileSystemOperations().delete(spec -> spec.delete(output));
        WorkQueue queue;
        if (Runtime.version().feature() == feature
            && System.getProperty("java.runtime.version").equals(launcher.getMetadata().getJavaRuntimeVersion())) {
            queue = getWorkerExecutor().classLoaderIsolation(spec -> spec.getClasspath().from(getTransformClasspath()));
        } else {
            queue = getWorkerExecutor().processIsolation(spec -> {
                spec.getClasspath().from(getTransformClasspath());
                spec.getForkOptions().setExecutable(launcher.getExecutablePath().getAsFile());
            });
        }
        queue.submit(ClassPathTransformAction.class, parameters -> {
            parameters.getApplication().from(getApplication());
            parameters.getDependencies().from(getDependencies());
            parameters.getThirdPartyJars().from(getThirdPartyJars());
            parameters.getDesugarLambdas().set(getDesugarLambdas());
            parameters.getStripLocalVariables().set(getStripLocalVariables());
            parameters.getOutputDirectory().set(getOutputDirectory());
        });
    }
}
