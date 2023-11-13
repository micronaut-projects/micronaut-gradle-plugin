package io.micronaut.gradle;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
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

    /**
     * If set to false, then the Micronaut Gradle plugins will not automatically
     * add the Micronaut Platform BOM to your dependencies. It becomes your
     * responsibility to add it directly, or to provide explicit versions for
     * Micronaut modules.
     * @return the import platform flag. Defaults to true.
     */
    public abstract Property<Boolean> getImportMicronautPlatform();

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
        getImportMicronautPlatform().convention(true);
    }

    /**
     * @return The test runtime to use.
     */
    public Property<MicronautTestRuntime> getTestRuntime() {
        return testRuntime;
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
     * @return The micronaut version.
     */
    public Property<String> getVersion() {
        return version;
    }

    public AnnotationProcessing getProcessing() {
        return processing;
    }

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

        Map<String, List<String>> answer = new LinkedHashMap<>(len / 2);
        int i = 0;
        while (i < values.length - 1) {
            answer.put(values[i++].toString(), (List<String>) values[i++]);
        }
        return answer;
    }
}
