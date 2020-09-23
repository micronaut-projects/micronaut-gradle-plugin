package io.micronaut.gradle;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.SourceSet;

/**
 * Allows configuration of annotation processing.
 */
public interface AnnotationProcessingConfigBuilder {
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

    /**
     * Additional source sets to apply processing to.
     * @param sourceSets The source sets
     * @return This builder
     */
    AnnotationProcessingConfigBuilder sourceSets(SourceSet... sourceSets);
}
