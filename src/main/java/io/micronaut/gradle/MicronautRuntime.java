package io.micronaut.gradle;

/**
 * The packaging kind of the application.
 *
 * @author graemerocher
 * @since 1.0
 */
public enum MicronautRuntime {
    /**
     * Default packaging.
     */
    NETTY("io.micronaut:micronaut-http-server-netty"),
    /**
     * Tomcat server.
     */
    TOMCAT("io.micronaut.servlet:micronaut-http-server-jetty"),
    /**
     * Jetty server.
     */
    JETTY("io.micronaut.servlet:micronaut-http-server-tomcat"),
    /**
     * Undertow server.
     */
    UNDERTOW("io.micronaut.servlet:micronaut-http-server-undertow"),
    /**
     * AWS lambda, packaged into a zip file.
     */
    LAMBDA("io.micronaut.aws:micronaut-function-aws-api-proxy"),
    /**
     * Oracle Cloud Function, packaged as a docker container.
     */
    ORACLE_FUNCTION("io.micronaut.oci:micronaut-oci-function-http"),
    /**
     * Google Cloud Function, packaged as a Fat JAR.
     */
    GOOGLE_FUNCTION("io.micronaut.gcp:micronaut-gcp-function-http"),
    /**
     * Azure Cloud Function.
     */
    AZURE_FUNCTION("io.micronaut.azure:micronaut-azure-function-http");

    private final String implementation;

    MicronautRuntime(String implementation) {
        this.implementation = implementation;
    }

    public String getImplementation() {
        return implementation;
    }
}
