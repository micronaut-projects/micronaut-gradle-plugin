package io.micronaut.gradle;

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
    LAMBDA("io.micronaut.aws:micronaut-function-aws-api-proxy"),
    /**
     * AWS native lambda, packaged into a zip file.
     */
    LAMBDA_NATIVE(
            "io.micronaut.aws:micronaut-function-aws-api-proxy",
            "io.micronaut.aws:micronaut-function-aws-custom-runtime"),
    /**
     * Oracle Cloud Function, packaged as a docker container.
     */
    // TODO: remove hard coded versions
    ORACLE_FUNCTION(MicronautExtension.mapOf(
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
            JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME,
            Collections.singletonList("com.google.cloud.functions:functions-framework-api"),
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
            Collections.singletonList("com.google.cloud.functions:functions-framework-api")
    )),
    /**
     * Azure Cloud Function.
     */
    AZURE_FUNCTION("io.micronaut.azure:micronaut-azure-function-http", "com.microsoft.azure.functions:azure-functions-java-library");

    private final Map<String, List<String>> implementation;

    MicronautRuntime(String... dependencies) {
        this.implementation = Collections.singletonMap(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, Arrays.asList(dependencies));
    }

    MicronautRuntime(Map<String, List<String>> implementation) {
        this.implementation = implementation;
    }

    public Map<String, List<String>> getDependencies() {
        return implementation;
    }
}
