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
    NETTY("io.micronaut:micronaut-http-server-netty"),
    /**
     * Tomcat server.
     */
    TOMCAT("io.micronaut.servlet:micronaut-http-server-tomcat"),
    /**
     * Jetty server.
     */
    JETTY("io.micronaut.servlet:micronaut-http-server-jetty"),
    /**
     * Undertow server.
     */
    UNDERTOW("io.micronaut.servlet:micronaut-http-server-undertow"),
    /**
     * AWS lambda packaged as a Jar file.
     */
    LAMBDA(DockerBuildStrategy.LAMBDA, MicronautExtension.mapOf(
            JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
            Arrays.asList("io.micronaut.aws:micronaut-function-aws-api-proxy", "io.micronaut.aws:micronaut-function-aws-custom-runtime"),
            "developmentOnly",
            Collections.singletonList("io.micronaut.aws:micronaut-function-aws-api-proxy-test"),
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
            Collections.singletonList("io.micronaut.aws:micronaut-function-aws-api-proxy-test")
    )),
    /**
     * Oracle Cloud Function, packaged as a docker container.
     */
    ORACLE_FUNCTION(DockerBuildStrategy.ORACLE_FUNCTION, MicronautExtension.mapOf(
            JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
            Collections.singletonList("io.micronaut.oraclecloud:micronaut-oraclecloud-function-http"),
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
            Collections.singletonList("io.micronaut.oraclecloud:micronaut-oraclecloud-function-http-test"),
            "developmentOnly",
            Collections.singletonList("io.micronaut.oraclecloud:micronaut-oraclecloud-function-http-test"),
            JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME,
            Collections.singletonList("com.fnproject.fn:runtime")
    )),
    /**
     * Google Cloud Function, packaged as a Fat JAR.
     */
    GOOGLE_FUNCTION(MicronautExtension.mapOf(
            JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
            Collections.singletonList("io.micronaut.gcp:micronaut-gcp-function-http"),
            "developmentOnly",
            Arrays.asList("com.google.cloud.functions:functions-framework-api", "io.micronaut.gcp:micronaut-gcp-function-http-test"),
            JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME,
            Collections.singletonList("com.google.cloud.functions:functions-framework-api"),
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
            Arrays.asList("com.google.cloud.functions:functions-framework-api", "io.micronaut.gcp:micronaut-gcp-function-http-test")
    )),
    /**
     * Azure Cloud Function.
     */
    AZURE_FUNCTION(MicronautExtension.mapOf(
            JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
            Arrays.asList("io.micronaut.azure:micronaut-azure-function-http", "com.microsoft.azure.functions:azure-functions-java-library"),
            "developmentOnly",
            Collections.singletonList("io.micronaut.azure:micronaut-azure-function-http-test"),
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
            Collections.singletonList("io.micronaut.azure:micronaut-azure-function-http-test")
    ));

    private final Map<String, List<String>> implementation;
    private final DockerBuildStrategy buildStrategy;

    MicronautRuntime(String... dependencies) {
        this.implementation = Collections.singletonMap(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, Arrays.asList(dependencies));
        this.buildStrategy = DockerBuildStrategy.DEFAULT;
    }

    MicronautRuntime(Map<String, List<String>> implementation) {
        this.implementation = implementation;
        this.buildStrategy = DockerBuildStrategy.DEFAULT;
    }

    MicronautRuntime(DockerBuildStrategy buildStrategy, Map<String, List<String>> implementation) {
        this.implementation = implementation;
        this.buildStrategy = buildStrategy;
    }

    /**
     * A map of dependencies and scopes
     * @return The dependencies and scopes
     */
    public Map<String, List<String>> getDependencies() {
        return implementation;
    }

    /**
     * @return The docker build strategy
     */
    public DockerBuildStrategy getBuildStrategy() {
        return buildStrategy;
    }
}
