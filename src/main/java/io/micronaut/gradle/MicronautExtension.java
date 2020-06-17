package io.micronaut.gradle;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Configuration for the Micronaut extension.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MicronautExtension {

    private final Processing processing;
    private final Property<String> version;

    @Inject
    public MicronautExtension(ObjectFactory objectFactory) {
        this.processing = new Processing(objectFactory);
        this.version = objectFactory.property(String.class);
    }

    /**
     * Configures the Micronaut version.
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

    /**
     * Configures processing.
     * @param action The action
     */
    public MicronautExtension processing(Action<AnnotationProcessingConfigBuilder> action) {
        action.execute(processing);
        return this;
    }

    /**
     * @return The processing config
     */
    public AnnotationProcessingConfig getProcessingConfig() {
        return processing;
    }

    /**
     * Configures annotation processing.
     */
    interface AnnotationProcessingConfig {

        /**
         * Whether incremental processing is enabled.
         * @return The module name
         */
        Property<Boolean> isIncremental();

        /**
         * The module name being processed
         * @return The module name
         */
        Property<String> getModule();

        /**
         * The group name being processed.
         * @return The group name
         */
        Property<String> getGroup();

        /**
         * Annotation patterns to include in annotation processing.
         * @return The annotation patterns
         */
        ListProperty<String> getAnnotations();
    }

    /**
     * Allows configuration of annotation processing.
     */
    interface AnnotationProcessingConfigBuilder {
        /**
         * Whether incremental processing is enabled.
         * @param incremental True if incremental processing is enabled
         * @return This builder
         */
        AnnotationProcessingConfigBuilder incremental(boolean incremental);

        /**
         * The module name to use. Default to the gradle project name.
         * @param name The module name
         * @return This builder
         */
        AnnotationProcessingConfigBuilder module(String name);

        /**
         * The group name being used. Defaults to the gradle group name.
         * @param name The group name
         * @return This builder
         */
        AnnotationProcessingConfigBuilder group(String name);

        /**
         * The annotation patterns to include in processing.
         * @param annotations The annotation patterns
         * @return This builder
         */
        AnnotationProcessingConfigBuilder annotations(String...annotations);
    }

    private static class Processing implements AnnotationProcessingConfigBuilder, AnnotationProcessingConfig {
        private final Property<String> group;
        private final Property<Boolean> incremental;
        private final Property<String> module;
        private final ListProperty<String> annotations;

        Processing(ObjectFactory objectFactory) {
            this.incremental = objectFactory.property(Boolean.class);
            this.incremental.set(true);
            this.module = objectFactory.property(String.class);
            this.group = objectFactory.property(String.class);
            this.annotations = objectFactory.listProperty(String.class);
        }

        @Override
        public Property<Boolean> isIncremental() {
            return incremental;
        }

        @Override
        public Property<String> getModule() {
            return module;
        }

        @Override
        public Property<String> getGroup() {
            return group;
        }

        @Override
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
}
