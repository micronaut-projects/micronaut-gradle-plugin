package io.micronaut.gradle;

import io.micronaut.gradle.docker.DockerBuildStrategy;

/**
 * The packaging kind of the application.
 *
 * @author graemerocher
 * @since 1.0
 */
public enum MicronautRuntime {
    /**
     * No specific runtime specified.
     */
    NONE(),
    /**
     * Default packaging.
     */
    NETTY,
    /**
     * Tomcat server.
     */
    TOMCAT,
    /**
     * Jetty server.
     */
    JETTY,
    /**
     * Undertow server.
     */
    UNDERTOW,

    /**
     * AWS lambda packaged as a Jar file and deploy to a Java runtime.
     */
    LAMBDA_JAVA,

    /**
     * AWS lambda deployed to a Provided runtime.
     * @deprecated Use {@link #LAMBDA_PROVIDED} instead.
     */
    @Deprecated
    LAMBDA(DockerBuildStrategy.LAMBDA),

    /**
     * AWS lambda deployed to a Provided runtime.
     */
    LAMBDA_PROVIDED(DockerBuildStrategy.LAMBDA),

    /**
     * Oracle Cloud Function, packaged as a docker container.
     */
    ORACLE_FUNCTION(DockerBuildStrategy.ORACLE_FUNCTION),
    /**
     * Google Cloud Function, packaged as a Fat JAR.
     */
    GOOGLE_FUNCTION,
    /**
     * Azure Cloud Function.
     */
    AZURE_FUNCTION;

    private final DockerBuildStrategy buildStrategy;

    MicronautRuntime() {
        this.buildStrategy = DockerBuildStrategy.DEFAULT;
    }

    MicronautRuntime(DockerBuildStrategy buildStrategy) {
        this.buildStrategy = buildStrategy;
    }

    /**
     * @return The docker build strategy
     */
    public DockerBuildStrategy getBuildStrategy() {
        return buildStrategy;
    }

    public boolean isLambdaProvided() {
        return this == LAMBDA_PROVIDED || this == LAMBDA;
    }

}