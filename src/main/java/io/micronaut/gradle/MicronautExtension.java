package io.micronaut.gradle;

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

    @Inject
    public MicronautExtension(ObjectFactory objectFactory) {
        this.processing = objectFactory.newInstance(AnnotationProcessing.class);
        this.version = objectFactory.property(String.class);
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
     * Allows configuring processing.
     * @param processingAction The processing action
     * @return This extension
     */
    public MicronautExtension processing(Action<AnnotationProcessing> processingAction) {
        processingAction.execute(this.getProcessing());
        return this;
    }
}