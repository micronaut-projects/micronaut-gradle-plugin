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
    AZURE_FUNCTION,
    /**
     * Plain old Java application based on Apache libraries.
     */
    HTTP_POJA;

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

    public boolean isLambda() {
        return this == LAMBDA_JAVA || isLambdaProvided();
    }
    public boolean isLambdaProvided() {
        return this == LAMBDA_PROVIDED;
    }

}
