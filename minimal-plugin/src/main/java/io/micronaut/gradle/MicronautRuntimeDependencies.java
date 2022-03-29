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

import org.gradle.api.plugins.JavaPlugin;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.micronaut.gradle.MicronautRuntime.*;

/**
 * Resolves the dependencies for the current runtime and application type
 * @author Sergio del Amo
 * @since 3.3.2
 */
public final class MicronautRuntimeDependencies {

    private final static String GROUP_MICRONAUT = "io.micronaut";
    private final static String ARTIFACT_ID_MICRONAUT_SERVER_NETTY = "micronaut-http-server-netty";
    private final static String GROUP_MICRONAUT_SERVLET = "io.micronaut.servlet";
    private final static String GROUP_MICRONAUT_AWS = "io.micronaut.aws";
    private final static String GROUP_MICRONAUT_GCP = "io.micronaut.gcp";
    private final static String GROUP_GOOGLE_CLOUD_FUNCTIONS = "com.google.cloud.functions";
    private final static String ARTIFACT_ID_FUNCTIONS_FRAMEWORK_API = "functions-framework-api";

    private final static String GROUP_MICRONAUT_ORACLE = "io.micronaut.oraclecloud";
    private final static String ARTIFACT_ID_MICRONAUT_ORACLE_HTTP = "micronaut-oraclecloud-function-http";
    private final static String ARTIFACT_ID_MICRONAUT_ORACLE_HTTP_TEST = "micronaut-oraclecloud-function-http-test";

    private final static String ARTIFACT_ID_MICRONAUT_GCP_FUNCTION_HTTP = "micronaut-gcp-function-http";
    private final static String ARTIFACT_ID_MICRONAUT_GCP_FUNCTION_HTTP_TEST = "micronaut-gcp-function-http-test";

    private final static String GROUP_MICRONAUT_AZURE = "io.micronaut.azure";
    private final static String ARTIFACT_ID_MICRONAUT_AZURE_FUNCTION_HTTP = "micronaut-azure-function-http";
    private final static String ARTIFACT_ID_MICRONAUT_AZURE_FUNCTION_HTTP_TEST = "micronaut-azure-function-http-test";

    private final static String ARTIFACT_ID_MICRONAUT_AWS_CUSTOM_RUNTIME = "micronaut-function-aws-custom-runtime";
    private final static String ARTIFACT_ID_MICRONAUT_AWS_API_PROXY = "micronaut-function-aws-api-proxy";
    private final static String ARTIFACT_ID_MICRONAUT_AWS_API_PROXY_TEST = "micronaut-function-aws-api-proxy-test";

    private final static String ARTIFACT_ID_MICRONAUT_SERVLET_JETTY = "micronaut-http-server-jetty";
    private final static String ARTIFACT_ID_MICRONAUT_SERVLET_TOMCAT = "micronaut-http-server-tomcat";
    private final static String ARTIFACT_ID_MICRONAUT_SERVLET_UNDERTOW = "micronaut-http-server-undertow";
    private final static String COLON = ":";
    private static final String DEVELOPMENT_ONLY = "developmentOnly";

    private MicronautRuntimeDependencies() {
    }

    /**
     * A map of dependencies and scopes
     * @return The dependencies and scopes
     */
    public static Map<String, List<String>> findDependenciesFor(MicronautRuntime runtime, ApplicationType applicationType) {

        if (runtime == MicronautRuntime.LAMBDA) {
            if (applicationType == ApplicationType.DEFAULT) {
                return MicronautExtension.mapOf(
                        JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                        Arrays.asList(micronautAwsDependency(ARTIFACT_ID_MICRONAUT_AWS_CUSTOM_RUNTIME), micronautAwsDependency(ARTIFACT_ID_MICRONAUT_AWS_API_PROXY)),
                        DEVELOPMENT_ONLY,
                        Collections.singletonList(micronautAwsDependency(ARTIFACT_ID_MICRONAUT_AWS_API_PROXY_TEST)),
                        JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
                        Collections.singletonList(micronautAwsDependency(ARTIFACT_ID_MICRONAUT_AWS_API_PROXY_TEST))
                );
            } else if (applicationType == ApplicationType.FUNCTION) {
                return MicronautExtension.mapOf(
                        JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                        Collections.singletonList(micronautAwsDependency(ARTIFACT_ID_MICRONAUT_AWS_CUSTOM_RUNTIME)));
            }
        } else if (runtime == LAMBDA_JAVA && applicationType == ApplicationType.DEFAULT) {
            return MicronautExtension.mapOf(
                    JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                    Collections.singletonList(micronautAwsDependency(ARTIFACT_ID_MICRONAUT_AWS_API_PROXY)),
                    DEVELOPMENT_ONLY,
                    Collections.singletonList(micronautAwsDependency(ARTIFACT_ID_MICRONAUT_AWS_API_PROXY_TEST)),
                    JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
                    Collections.singletonList(micronautAwsDependency(ARTIFACT_ID_MICRONAUT_AWS_API_PROXY_TEST)));

        } else if(runtime == MicronautRuntime.ORACLE_FUNCTION) {
            return MicronautExtension.mapOf(
                    JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                    Collections.singletonList(micronautOracleDependency(ARTIFACT_ID_MICRONAUT_ORACLE_HTTP)),
                    JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
                    Collections.singletonList(micronautOracleDependency(ARTIFACT_ID_MICRONAUT_ORACLE_HTTP_TEST)),
                    DEVELOPMENT_ONLY,
                    Collections.singletonList(micronautOracleDependency(ARTIFACT_ID_MICRONAUT_ORACLE_HTTP_TEST)),
                    JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME,
                    Collections.singletonList("com.fnproject.fn:runtime"));
        } else if(runtime == MicronautRuntime.GOOGLE_FUNCTION) {
            return MicronautExtension.mapOf(
                    JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                    Collections.singletonList(micronautGcpDependency(ARTIFACT_ID_MICRONAUT_GCP_FUNCTION_HTTP)),
                    DEVELOPMENT_ONLY,
                    Arrays.asList(dependency(GROUP_GOOGLE_CLOUD_FUNCTIONS, ARTIFACT_ID_FUNCTIONS_FRAMEWORK_API), micronautGcpDependency(ARTIFACT_ID_MICRONAUT_GCP_FUNCTION_HTTP_TEST)),
                    JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME,
                    Collections.singletonList(dependency(GROUP_GOOGLE_CLOUD_FUNCTIONS, ARTIFACT_ID_FUNCTIONS_FRAMEWORK_API)),
                    JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
                    Arrays.asList(dependency(GROUP_GOOGLE_CLOUD_FUNCTIONS, ARTIFACT_ID_FUNCTIONS_FRAMEWORK_API), micronautGcpDependency(ARTIFACT_ID_MICRONAUT_GCP_FUNCTION_HTTP_TEST)));
        } else if (runtime == MicronautRuntime.AZURE_FUNCTION) {
            return MicronautExtension.mapOf(
                    JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                    Arrays.asList(micronautAzureDependency(ARTIFACT_ID_MICRONAUT_AZURE_FUNCTION_HTTP), "com.microsoft.azure.functions:azure-functions-java-library"),
                    DEVELOPMENT_ONLY,
                    Collections.singletonList(micronautAzureDependency(ARTIFACT_ID_MICRONAUT_AZURE_FUNCTION_HTTP_TEST)),
                    JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
                    Collections.singletonList(micronautAzureDependency(ARTIFACT_ID_MICRONAUT_AZURE_FUNCTION_HTTP_TEST))
            );
        } else if (runtime == NETTY) {
            return MicronautExtension.mapOf(
                    JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, Collections.singletonList(dependency(GROUP_MICRONAUT, ARTIFACT_ID_MICRONAUT_SERVER_NETTY)));
        } else if (runtime == TOMCAT) {
            return MicronautExtension.mapOf(
                    JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, Collections.singletonList(micronautServletDependency(ARTIFACT_ID_MICRONAUT_SERVLET_TOMCAT)));
        } else if (runtime == JETTY) {
            return MicronautExtension.mapOf(
                    JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, Collections.singletonList(micronautServletDependency(ARTIFACT_ID_MICRONAUT_SERVLET_JETTY)));
        } else if (runtime == UNDERTOW) {
            return MicronautExtension.mapOf(
                    JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, Collections.singletonList(micronautServletDependency(ARTIFACT_ID_MICRONAUT_SERVLET_UNDERTOW)));
        }
        return Collections.emptyMap();
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
