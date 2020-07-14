package io.micronaut.gradle;

import io.micronaut.gradle.docker.DockerSettings;
import io.micronaut.gradle.graalvm.GraalUtil;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Configuration for the Micronaut extension.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MicronautExtension {

    private final AnnotationProcessing processing;
    private final Property<String> version;
    private final Property<Boolean> enableNativeImage;
    private final DockerSettings docker;

    @Inject
    public MicronautExtension(ObjectFactory objectFactory) {
        this.processing = objectFactory.newInstance(AnnotationProcessing.class);
        this.version = objectFactory.property(String.class);
        this.docker = objectFactory.newInstance(DockerSettings.class);
        this.enableNativeImage = objectFactory.property(Boolean.class)
                                    .convention(GraalUtil.isGraalJVM());
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
     * @return The micronaut version.
     */
    public Property<String> getVersion() {
        return version;
    }

    public AnnotationProcessing getProcessing() {
        return processing;
    }

    /**
     * @return The docker settings
     */
    public DockerSettings getDocker() {
        return docker;
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

    /**
     * Allows configuring docker builds.
     * @param dockerSettings The processing action
     * @return This extension
     */
    public MicronautExtension docker(Action<DockerSettings> dockerSettings) {
        dockerSettings.execute(this.getDocker());
        return this;
    }
}