package io.micronaut.gradle.docker;

/**
 * Different strategies for building docker images.
 *
 * @author graemerocher
 * @since 1.0.0
 *
 */
public enum DockerBuildStrategy {
    /**
     * An oracle function
     */
    ORACLE_FUNCTION,
    /**
     * An AWS Lambda
     */
    LAMBDA,
    /**
     * Default docker build strategy
     */
    DEFAULT
}
