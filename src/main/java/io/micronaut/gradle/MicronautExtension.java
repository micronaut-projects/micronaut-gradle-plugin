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

    private final Processing processing;

    @Inject
    public MicronautExtension(ObjectFactory objectFactory) {
        this.processing = new Processing(objectFactory);
    }

    /**
     * Configures processing.
     * @param action The action
     */
    public void processing(Action<AnnotationProcessingConfigBuilder> action) {
        action.execute(processing);
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
         * The module name being processed
         * @return The module name
         */
        Property<String> getModule();

        /**
         * The group name being processed.
         * @return The group name
         */
        Property<String> getGroup();
    }

    /**
     * Allows configuration of annotation processing.
     */
    interface AnnotationProcessingConfigBuilder {
        /**
         * The module name to use. Default to the gradle project name.
         * @param name The module name
         */
        void module(String name);

        /**
         * The group name being used. Defaults to the gradle group name.
         * @param name The group name
         */
        void group(String name);
    }

    private static class Processing implements AnnotationProcessingConfigBuilder, AnnotationProcessingConfig {
        private final Property<String> group;
        private final Property<String> module;

        Processing(ObjectFactory objectFactory) {
            this.module = objectFactory.property(String.class);
            this.group = objectFactory.property(String.class);
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
        public void module(String name) {
            if (name != null) {
                this.module.set(name);
            }
        }

        @Override
        public void group(String name) {
            if (name != null) {
                this.group.set(name);
            }
        }
    }
}
