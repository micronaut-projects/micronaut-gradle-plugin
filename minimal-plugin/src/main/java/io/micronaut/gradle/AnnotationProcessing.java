package io.micronaut.gradle;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * Configuration for annotation processing in Micronaut.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class AnnotationProcessing implements AnnotationProcessingConfigBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationProcessing.class);

    private final Property<String> group;
    private final Property<Boolean> incremental;
    private final Property<String> module;
    private final ListProperty<String> annotations;
    private final ListProperty<SourceSet> additionalSourceSets;
    private final SourceSetConfigurer sourceSetConfigurer;

    public static void showAdditionalSourceSetDeprecationWarning(SourceSet sourceSet) {
        LOGGER.warn("Source set '{}' was declared using the additionalSourceSets property. Please use sourceSet(...) instead.", sourceSet.getName());
    }

    @Inject
    public AnnotationProcessing(ObjectFactory objectFactory, SourceSetConfigurer sourceSetConfigurer) {
        this.incremental = objectFactory.property(Boolean.class).convention(true);
        this.module = objectFactory.property(String.class);
        this.group = objectFactory.property(String.class);
        this.annotations = objectFactory.listProperty(String.class);
        this.additionalSourceSets = objectFactory.listProperty(SourceSet.class);
        this.sourceSetConfigurer = sourceSetConfigurer;
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

    /**
     * @return The additional configurations to apply annotation processing to.
     * @deprecated Use {@link #sourceSets(SourceSet...)} instead.
     */
    @Deprecated
    public ListProperty<SourceSet> getAdditionalSourceSets() {
        return additionalSourceSets;
    }

    @Override
    public AnnotationProcessingConfigBuilder sourceSets(SourceSet... sourceSets) {
        this.additionalSourceSets.addAll(sourceSets);
        for (SourceSet sourceSet : sourceSets) {
            sourceSetConfigurer.onSourceSet(sourceSet);
        }
        return this;
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
        if (annotations != null) {
            for (String annotation : annotations) {
                if (annotation != null) {
                    this.annotations.add(annotation);
                }
            }
        }
        return this;
    }
}
