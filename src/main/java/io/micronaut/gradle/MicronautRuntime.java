package io.micronaut.gradle;

import org.gradle.api.plugins.JavaPlugin;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

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
    LAMBDA_NATIVE("io.micronaut.aws:micronaut-function-aws-api-proxy", "io.micronaut.aws:micronaut-function-aws-custom-runtime"),
    /**
     * Oracle Cloud Function, packaged as a docker container.
     */
    // TODO: remove hard coded version
    ORACLE_FUNCTION("io.micronaut.oraclecloud:micronaut-oraclecloud-function-http:1.0.0.M3"),
    /**
     * Google Cloud Function, packaged as a Fat JAR.
     */
    GOOGLE_FUNCTION("io.micronaut.gcp:micronaut-gcp-function-http"),
    /**
     * Azure Cloud Function.
     */
    AZURE_FUNCTION("io.micronaut.azure:micronaut-azure-function-http");

    private final Map<String, String> implementation;

    MicronautRuntime(String... dependencies) {
        this.implementation = Arrays.stream(dependencies).collect(
                Collectors.toMap((v)-> JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, (v) -> v)
        );
    }

    public Map<String, String> getDependencies() {
        return implementation;
    }
}
