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
package io.micronaut.gradle.dev;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Writes the manifest of test mode, {@code ./gradlew mnTest}: everything the development mode manifest
 * holds, plus {@code micronaut.dev.mode=test}, the test sources, resources and compilations, and how the
 * tests run, under {@code micronaut.dev.test.}.
 *
 * @since 5.0.3
 */
public abstract class MicronautTestManifest extends MicronautDevManifest {

    private static final String TEST_PREFIX = PREFIX + "test.";

    /**
     * @return the Java test source directories
     */
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getTestJavaSources();

    /**
     * @return the Kotlin test source directories
     */
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getTestKotlinSources();

    /**
     * @return the Groovy test source directories
     */
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getTestGroovySources();

    /**
     * @return the test resource directories, read ahead of the application's
     */
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getTestResourceSources();

    /**
     * @return the test compile classpath
     */
    @Classpath
    public abstract ConfigurableFileCollection getTestCompileClasspath();

    /**
     * @return the test annotation and symbol processor path
     */
    @Classpath
    public abstract ConfigurableFileCollection getTestProcessorPath();

    /**
     * @return the class output of the Java test compilation, as a path
     */
    @Input
    @Optional
    public abstract Property<String> getTestJavaOutput();

    /**
     * @return the generated sources output of the Java test compilation, as a path
     */
    @Input
    @Optional
    public abstract Property<String> getTestJavaGeneratedSources();

    /**
     * @return the class output of the Kotlin test compilation, as a path
     */
    @Input
    @Optional
    public abstract Property<String> getTestKotlinOutput();

    /**
     * @return the class output of the Groovy test compilation, as a path
     */
    @Input
    @Optional
    public abstract Property<String> getTestGroovyOutput();

    /**
     * @return the javac options of the test compilation
     */
    @Input
    public abstract ListProperty<String> getTestJavaOptions();

    /**
     * @return the kotlinc options of the test compilation
     */
    @Input
    public abstract ListProperty<String> getTestKotlinOptions();

    /**
     * @return the groovyc options of the test compilation
     */
    @Input
    public abstract ListProperty<String> getTestGroovyOptions();

    /**
     * @return the test runner, {@code junit-platform}
     */
    @Input
    public abstract Property<String> getTestRunner();

    /**
     * @return which tests a change runs: {@code affected} or {@code all}
     */
    @Input
    public abstract Property<String> getTestSelection();

    /**
     * @return whether every test runs once at startup
     */
    @Input
    public abstract Property<Boolean> getTestInitialRun();

    /**
     * @return the directory of the JUnit XML reports, as a path
     */
    @Input
    public abstract Property<String> getTestReports();

    /**
     * @return the directory of the HTML report, as a path
     */
    @Input
    public abstract Property<String> getTestHtmlReport();

    /**
     * @return the path the LiveReload server serves the HTML report at
     */
    @Input
    public abstract Property<String> getTestHtmlReportPath();

    /**
     * @return the JUnit Platform configuration parameters
     */
    @Input
    public abstract MapProperty<String, String> getTestParameters();

    @Override
    void putEntries(Map<String, String> entries, Path directory) {
        entries.put(PREFIX + "mode", "test");
        putSources(entries, TEST_PREFIX, "java", getTestJavaSources().getFiles());
        putSources(entries, TEST_PREFIX, "kotlin", getTestKotlinSources().getFiles());
        putSources(entries, TEST_PREFIX, "groovy", getTestGroovySources().getFiles());
        Set<File> resources = existing(getTestResourceSources().getFiles());
        if (!resources.isEmpty()) {
            entries.put(TEST_PREFIX + "resources.config", join(resources));
        }
        // written even when it equals the application's: the tests then compile exactly as the build compiles them
        entries.put(TEST_PREFIX + "compile-classpath", "@" + argfile(directory, "test-compile.argfile", getTestCompileClasspath().getFiles()));
        entries.put(TEST_PREFIX + "processor-path", "@" + argfile(directory, "test-processors.argfile", getTestProcessorPath().getFiles()));
        putCompilation(entries, directory, TEST_PREFIX, "test-", "java", getTestJavaSources().getFiles(), getTestJavaOutput(), getTestJavaGeneratedSources(), getTestJavaOptions().get());
        putCompilation(entries, directory, TEST_PREFIX, "test-", "kotlin", getTestKotlinSources().getFiles(), getTestKotlinOutput(), null, getTestKotlinOptions().get());
        putCompilation(entries, directory, TEST_PREFIX, "test-", "groovy", getTestGroovySources().getFiles(), getTestGroovyOutput(), null, getTestGroovyOptions().get());
        if (getKotlinCompileMode().isPresent()) {
            // the tests compile as their own manifest, which takes no per-language entry of the application's
            entries.put(TEST_PREFIX + "compile.kotlin.mode", getKotlinCompileMode().get());
        }
        entries.put(TEST_PREFIX + "runner", getTestRunner().get());
        entries.put(TEST_PREFIX + "selection", getTestSelection().get());
        entries.put(TEST_PREFIX + "initial-run", String.valueOf(getTestInitialRun().get()));
        entries.put(TEST_PREFIX + "reports", getTestReports().get());
        entries.put(TEST_PREFIX + "html-report", getTestHtmlReport().get());
        entries.put(TEST_PREFIX + "html-report-path", getTestHtmlReportPath().get());
        getTestParameters().get().forEach((key, value) -> entries.put(TEST_PREFIX + "parameters." + key, value));
    }
}
