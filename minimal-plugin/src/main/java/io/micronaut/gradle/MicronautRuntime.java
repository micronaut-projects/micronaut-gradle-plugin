package io.micronaut.gradle;

import io.micronaut.gradle.docker.DockerBuildStrategy;
import org.gradle.api.plugins.JavaPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    LAMBDA(DockerBuildStrategy.LAMBDA),

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
}
