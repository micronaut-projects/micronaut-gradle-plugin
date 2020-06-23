package io.micronaut.gradle;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Configuration for annotation processing in Micronaut.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class AnnotationProcessing implements AnnotationProcessingConfigBuilder {
    private final Property<String> group;
    private final Property<Boolean> incremental;
    private final Property<String> module;
    private final ListProperty<String> annotations;

    @Inject
    public AnnotationProcessing(ObjectFactory objectFactory) {
        this.incremental = objectFactory.property(Boolean.class).convention(true);
        this.module = objectFactory.property(String.class);
        this.group = objectFactory.property(String.class);
        this.annotations = objectFactory.listProperty(String.class);
    }

    /**
     * @return The Maven group ID
     */
    public Property<String> getGroup() {
        return group;
    }

    /**
     * @return Whether processing is incremental or not
     */
    public Property<Boolean> getIncremental() {
        return incremental;
    }

    /**
     * @return The Maven artifactId
     */
    public Property<String> getModule() {
        return module;
    }

    /**
     * @return Additional annotation patterns to include in processing
     */
    public ListProperty<String> getAnnotations() {
        return annotations;
    }

    @Override
    public AnnotationProcessingConfigBuilder incremental(boolean incremental) {
        this.incremental.set(incremental);
        return this;
    }

    @Override
    public AnnotationProcessingConfigBuilder module(String name) {
        if (name != null) {
            this.module.set(name);
        }
        return this;
    }

    @Override
    public AnnotationProcessingConfigBuilder group(String name) {
        if (name != null) {
            this.group.set(name);
        }
        return this;
    }

    @Override
    public AnnotationProcessingConfigBuilder annotations(String... annotations) {
        if (annotations != null && annotations.length > 0) {
            for (String annotation : annotations) {
                if (annotation != null) {
                    this.annotations.add(annotation);
                }
            }
        }
        return this;
    }
}
