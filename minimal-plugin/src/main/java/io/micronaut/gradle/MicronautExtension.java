package io.micronaut.gradle;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Configuration for the Micronaut extension.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public abstract class MicronautExtension implements ExtensionAware {

    private final AnnotationProcessing processing;
    private final Property<String> version;
    private final Property<Boolean> enableNativeImage;
    private final Property<MicronautRuntime> runtime;
    private final Property<MicronautTestRuntime> testRuntime;
    private final ListProperty<MicronautTestRuntime> testRuntimes;

    /**
     * If set to false, then the Micronaut Gradle plugins will not automatically
     * add the Micronaut Platform BOM to your dependencies. It becomes your
     * responsibility to add it directly, or to provide explicit versions for
     * Micronaut modules.
     * @return the import platform flag. Defaults to true.
     */
    public abstract Property<Boolean> getImportMicronautPlatform();

    /**
     * The Micronaut plugins can automatically add dependencies to your project.
     * If, for some reason, a dependency shouldn't be automatically added, you can
     * add its coordinates to this set. The format is "group:name". It must not include
     * the version.
     *
     * @return the set of ignored automatic dependencies, as group:name strings.
     */
    public abstract SetProperty<String> getIgnoredAutomaticDependencies();

    @Inject
    public MicronautExtension(ObjectFactory objectFactory, SourceSetConfigurer sourceSetConfigurer) {
        this.processing = objectFactory.newInstance(AnnotationProcessing.class, sourceSetConfigurer);
        this.version = objectFactory.property(String.class);
        this.enableNativeImage = objectFactory.property(Boolean.class)
                                    .convention(true);
        this.runtime = objectFactory.property(MicronautRuntime.class)
                                    .convention(MicronautRuntime.NONE);
        this.testRuntime = objectFactory.property(MicronautTestRuntime.class)
                                        .convention(MicronautTestRuntime.NONE);
        this.testRuntimes = objectFactory.listProperty(MicronautTestRuntime.class)
                                         .convention(Collections.emptyList());
        getImportMicronautPlatform().convention(true);
    }

    /**
     * @return The test runtime to use.
     */
    public Property<MicronautTestRuntime> getTestRuntime() {
        return testRuntime;
    }

    /**
     * @return The test runtimes to use in addition to the legacy single-value path.
     */
    public ListProperty<MicronautTestRuntime> getTestRuntimes() {
        return testRuntimes;
    }

    /**
     * @return The packaging type to use for the micronaut application.
     */
    public Property<MicronautRuntime> getRuntime() {
        return runtime;
    }

    /**
     * Whether native image is enabled
     * @return True if it is
     */
    public Property<Boolean> getEnableNativeImage() {
        return enableNativeImage;
    }

    /**
     * Sets whether native image is enabled.
     *
     * @param b Whether native image is enabled.
     * @return This extension
     */
    public MicronautExtension enableNativeImage(boolean b) {
        this.enableNativeImage.set(b);
        return this;
    }

    /**
     * Configures the Micronaut version.
     *
     * @param version The micronaut version
     * @return This extension
     */
    public MicronautExtension version(String version) {
        this.version.set(version);
        return this;
    }

    /**
     * Configures the packaging type.
     *
     * @param runtime The micronaut packaging type
     * @return This extension
     */
    public MicronautExtension runtime(String runtime) {
        if (runtime != null) {
            this.runtime.set(MicronautRuntime.valueOf(runtime.toUpperCase(Locale.ENGLISH)));
        }
        return this;
    }

    /**
     * Configures the packaging type.
     *
     * @param micronautRuntime The micronaut runtime type
     * @return This extension
     */
    public MicronautExtension runtime(MicronautRuntime micronautRuntime) {
        if (micronautRuntime != null) {
            this.runtime.set(micronautRuntime);
        }
        return this;
    }

    /**
     * Configures the test runtime to use.
     *
     * @param runtime The micronaut packaging type
     * @return This extension
     */
    public MicronautExtension testRuntime(String runtime) {
        if (runtime != null) {
            this.testRuntime.set(MicronautTestRuntime.parse(runtime));
        }
        return this;
    }

    /**
     * Configures the test runtime to use.
     *
     * @param testRuntime The micronaut runtime type
     * @return This extension
     */
    public MicronautExtension testRuntime(MicronautTestRuntime testRuntime) {
        if (testRuntime != null) {
            this.testRuntime.set(testRuntime);
        }
        return this;
    }

    /**
     * Configures multiple test runtimes to use.
     *
     * @param runtimes The micronaut test runtime types
     * @return This extension
     * @since 5.0.0
     */
    public MicronautExtension testRuntimes(MicronautTestRuntime... runtimes) {
        if (runtimes != null) {
            for (MicronautTestRuntime runtime : runtimes) {
                if (runtime != null) {
                    this.testRuntimes.add(runtime);
                }
            }
        }
        return this;
    }

    /**
     * Configures multiple test runtimes to use.
     *
     * @param runtimes The micronaut test runtime types
     * @return This extension
     * @since 5.0.0
     */
    public MicronautExtension testRuntimes(String... runtimes) {
        if (runtimes != null) {
            for (String runtime : runtimes) {
                if (runtime != null) {
                    this.testRuntimes.add(MicronautTestRuntime.parse(runtime));
                }
            }
        }
        return this;
    }

    List<MicronautTestRuntime> resolveTestRuntimes() {
        List<MicronautTestRuntime> configured = new ArrayList<>();
        configured.add(testRuntime.get());
        configured.addAll(testRuntimes.getOrElse(Collections.emptyList()));

        boolean hasSelectedRuntime = configured.stream().anyMatch(runtime -> runtime != null && runtime != MicronautTestRuntime.NONE);
        var resolved = new LinkedHashSet<MicronautTestRuntime>();
        for (MicronautTestRuntime runtime : configured) {
            if (runtime == null) {
                continue;
            }
            if (runtime == MicronautTestRuntime.NONE && hasSelectedRuntime) {
                continue;
            }
            resolved.add(runtime);
        }
        return List.copyOf(resolved);
    }

    /**
     * @return The micronaut version.
     */
    public Property<String> getVersion() {
        return version;
    }

    public AnnotationProcessing getProcessing() {
        return processing;
    }

    /**
     * Property which drives if incremental native builds should be enabled.
     * @return the incremental property
     */
    public abstract Property<Boolean> getIncrementalNativeBuild();

    /**
     * Allows configuring processing.
     * @param processingAction The processing action
     * @return This extension
     */
    public MicronautExtension processing(Action<AnnotationProcessing> processingAction) {
        processingAction.execute(this.getProcessing());
        return this;
    }

    static Map<String, List<String>> mapOf(Object... values) {
        int len = values.length;
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Number of arguments should be an even number representing the keys and values");
        }

        var answer = new LinkedHashMap<String, List<String>>(len / 2);
        int i = 0;
        while (i < values.length - 1) {
            answer.put(values[i++].toString(), (List<String>) values[i++]);
        }
        return answer;
    }
}
