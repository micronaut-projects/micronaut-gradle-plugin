/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle;

import io.micronaut.gradle.internal.ConfigurableVersionProperty;
import io.micronaut.gradle.internal.Dependencies;
import org.gradle.api.GradleException;

import java.util.List;

import static io.micronaut.gradle.MicronautRuntime.JETTY;
import static io.micronaut.gradle.MicronautRuntime.NETTY;
import static io.micronaut.gradle.MicronautRuntime.NONE;
import static io.micronaut.gradle.MicronautRuntime.TOMCAT;
import static io.micronaut.gradle.MicronautRuntime.UNDERTOW;

/**
 * Resolves the dependencies for the current runtime and application type
 *
 * @author Sergio del Amo
 * @since 3.4.0
 */
public final class MicronautRuntimeDependencies {

    private static final String GROUP_MICRONAUT = "io.micronaut";
    private static final String ARTIFACT_ID_MICRONAUT_SERVER_NETTY = "micronaut-http-server-netty";
    private static final String GROUP_MICRONAUT_SERVLET = "io.micronaut.servlet";
    private static final String GROUP_MICRONAUT_AWS = "io.micronaut.aws";
    private static final String GROUP_MICRONAUT_GCP = "io.micronaut.gcp";
    private static final String GROUP_GOOGLE_CLOUD_FUNCTIONS = "com.google.cloud.functions";
    private static final String ARTIFACT_ID_FUNCTIONS_FRAMEWORK_API = "functions-framework-api";

    private static final String GROUP_MICRONAUT_ORACLE = "io.micronaut.oraclecloud";
    private static final String ARTIFACT_ID_MICRONAUT_ORACLE_HTTP = "micronaut-oraclecloud-function-http";
    private static final String ARTIFACT_ID_MICRONAUT_ORACLE_HTTP_TEST = "micronaut-oraclecloud-function-http-test";

    private static final String ARTIFACT_ID_MICRONAUT_GCP_FUNCTION_HTTP = "micronaut-gcp-function-http";
    private static final String ARTIFACT_ID_MICRONAUT_GCP_FUNCTION_HTTP_TEST = "micronaut-gcp-function-http-test";

    private static final String GROUP_MICRONAUT_AZURE = "io.micronaut.azure";
    private static final String ARTIFACT_ID_MICRONAUT_AZURE_FUNCTION_HTTP = "micronaut-azure-function-http";
    private static final String ARTIFACT_ID_MICRONAUT_AZURE_FUNCTION_HTTP_TEST = "micronaut-azure-function-http-test";

    private static final String ARTIFACT_ID_MICRONAUT_AWS_CUSTOM_RUNTIME = "micronaut-function-aws-custom-runtime";
    private static final String ARTIFACT_ID_MICRONAUT_AWS_API_PROXY = "micronaut-function-aws-api-proxy";
    private static final String ARTIFACT_ID_MICRONAUT_AWS_API_PROXY_TEST = "micronaut-function-aws-api-proxy-test";

    private static final String ARTIFACT_ID_MICRONAUT_SERVLET_JETTY = "micronaut-http-server-jetty";
    private static final String ARTIFACT_ID_MICRONAUT_SERVLET_TOMCAT = "micronaut-http-server-tomcat";
    private static final String ARTIFACT_ID_MICRONAUT_SERVLET_UNDERTOW = "micronaut-http-server-undertow";

    private static final ConfigurableVersionProperty AWS_VERSION_PROPERTY = ConfigurableVersionProperty.of("aws");
    private static final ConfigurableVersionProperty AZURE_VERSION_PROPERTY = ConfigurableVersionProperty.of("azure");
    private static final ConfigurableVersionProperty GCP_VERSION_PROPERTY = ConfigurableVersionProperty.of("gcp");
    private static final ConfigurableVersionProperty HTTP_NETTY_VERSION_PROPERTY = ConfigurableVersionProperty.of("httpNetty");
    private static final ConfigurableVersionProperty ORACLECLOUD_VERSION_PROPERTY = ConfigurableVersionProperty.of("oraclecloud");
    private static final ConfigurableVersionProperty SERVLET_VERSION_PROPERTY = ConfigurableVersionProperty.of("servlet");

    private static final String COLON = ":";

    public static final List<ConfigurableVersionProperty> KNOWN_VERSION_PROPERTIES = List.of(
            AWS_VERSION_PROPERTY,
            AZURE_VERSION_PROPERTY,
            GCP_VERSION_PROPERTY,
            HTTP_NETTY_VERSION_PROPERTY,
            ORACLECLOUD_VERSION_PROPERTY,
            SERVLET_VERSION_PROPERTY
    );

    private MicronautRuntimeDependencies() {
    }

    /**
     * @param runtime Micronaut runtime
     * @return The dependencies and scopes
     */
    public static Dependencies findApplicationPluginDependenciesByRuntime(MicronautRuntime runtime) {
        if (runtime.isLambda()) {
            Dependencies.Builder builder = Dependencies.builder()
                    .implementation(micronautAwsDependency(ARTIFACT_ID_MICRONAUT_AWS_API_PROXY), AWS_VERSION_PROPERTY)
                    .developmentOnly(micronautAwsDependency(ARTIFACT_ID_MICRONAUT_AWS_API_PROXY_TEST), AWS_VERSION_PROPERTY)
                    .testImplementation(micronautAwsDependency(ARTIFACT_ID_MICRONAUT_AWS_API_PROXY_TEST), AWS_VERSION_PROPERTY);
            if (runtime.isLambdaProvided()) {
                builder = builder.implementation(micronautAwsDependency(ARTIFACT_ID_MICRONAUT_AWS_CUSTOM_RUNTIME), AWS_VERSION_PROPERTY);
            }
            return builder.build();

        } else if (runtime == MicronautRuntime.ORACLE_FUNCTION) {
            return Dependencies.builder()
                    .implementation(micronautOracleDependency(ARTIFACT_ID_MICRONAUT_ORACLE_HTTP), ORACLECLOUD_VERSION_PROPERTY)
                    .testImplementation(micronautOracleDependency(ARTIFACT_ID_MICRONAUT_ORACLE_HTTP_TEST), ORACLECLOUD_VERSION_PROPERTY)
                    .developmentOnly(micronautOracleDependency(ARTIFACT_ID_MICRONAUT_ORACLE_HTTP_TEST), ORACLECLOUD_VERSION_PROPERTY)
                    .runtimeOnly("com.fnproject.fn:runtime")
                    .build();
        } else if (runtime == MicronautRuntime.GOOGLE_FUNCTION) {
            return Dependencies.builder()
                    .implementation(micronautGcpDependency(ARTIFACT_ID_MICRONAUT_GCP_FUNCTION_HTTP), GCP_VERSION_PROPERTY)
                    .developmentOnly(dependency(GROUP_GOOGLE_CLOUD_FUNCTIONS, ARTIFACT_ID_FUNCTIONS_FRAMEWORK_API))
                    .developmentOnly(micronautGcpDependency(ARTIFACT_ID_MICRONAUT_GCP_FUNCTION_HTTP_TEST), GCP_VERSION_PROPERTY)
                    .compileOnly(dependency(GROUP_GOOGLE_CLOUD_FUNCTIONS, ARTIFACT_ID_FUNCTIONS_FRAMEWORK_API))
                    .testImplementation(dependency(GROUP_GOOGLE_CLOUD_FUNCTIONS, ARTIFACT_ID_FUNCTIONS_FRAMEWORK_API))
                    .testImplementation(micronautGcpDependency(ARTIFACT_ID_MICRONAUT_GCP_FUNCTION_HTTP_TEST), GCP_VERSION_PROPERTY)
                    .build();
        } else if (runtime == MicronautRuntime.AZURE_FUNCTION) {
            return Dependencies.builder()
                    .implementation(micronautAzureDependency(ARTIFACT_ID_MICRONAUT_AZURE_FUNCTION_HTTP), AZURE_VERSION_PROPERTY)
                    .implementation("com.microsoft.azure.functions:azure-functions-java-library")
                    .developmentOnly(micronautAzureDependency(ARTIFACT_ID_MICRONAUT_AZURE_FUNCTION_HTTP_TEST), AZURE_VERSION_PROPERTY)
                    .testImplementation(micronautAzureDependency(ARTIFACT_ID_MICRONAUT_AZURE_FUNCTION_HTTP_TEST), AZURE_VERSION_PROPERTY)
                    .build();

        } else if (runtime == NETTY) {
            return Dependencies.builder().implementation(dependency(GROUP_MICRONAUT, ARTIFACT_ID_MICRONAUT_SERVER_NETTY), HTTP_NETTY_VERSION_PROPERTY).build();

        } else if (runtime == TOMCAT) {
            return Dependencies.builder().implementation(micronautServletDependency(ARTIFACT_ID_MICRONAUT_SERVLET_TOMCAT), SERVLET_VERSION_PROPERTY).build();

        } else if (runtime == JETTY) {
            return Dependencies.builder().implementation(micronautServletDependency(ARTIFACT_ID_MICRONAUT_SERVLET_JETTY), SERVLET_VERSION_PROPERTY).build();

        } else if (runtime == UNDERTOW) {
            return Dependencies.builder().implementation(micronautServletDependency(ARTIFACT_ID_MICRONAUT_SERVLET_UNDERTOW), SERVLET_VERSION_PROPERTY).build();
        } else if (runtime != NONE) {
            throw new GradleException("Application plugin dependencies not specified for runtime " + runtime.name());
        }
        return Dependencies.builder().build();
    }

    private static String micronautOracleDependency(String artifactId) {
        return dependency(GROUP_MICRONAUT_ORACLE, artifactId);
    }

    private static String micronautAwsDependency(String artifactId) {
        return dependency(GROUP_MICRONAUT_AWS, artifactId);
    }

    private static String micronautGcpDependency(String artifactId) {
        return dependency(GROUP_MICRONAUT_GCP, artifactId);
    }

    private static String micronautAzureDependency(String artifactId) {
        return dependency(GROUP_MICRONAUT_AZURE, artifactId);
    }

    private static String micronautServletDependency(String artifactId) {
        return dependency(GROUP_MICRONAUT_SERVLET, artifactId);
    }

    private static String dependency(String groupId, String artifactId) {
        return String.join(COLON, groupId, artifactId);
    }

}
