package io.micronaut.gradle

import spock.lang.Narrative
import spock.lang.Unroll

@Narrative("""\
Micronaut Gradle plugin adds dependencies depending on the environment being target.
""")
class RuntimeDependenciesSpec extends AbstractGradleBuildSpec {

    @Unroll
    def "#runtime runtime adds #description for the #configuration" (String runtime,
                                                                     String configuration,
                                                                     List<String> coordinates,
                                                                     String description) {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
            }
            
            micronaut {
                version "3.4.0"
                runtime "$runtime"                
            }
            
            $repositoriesBlock                                   
        """

        expect:
        for (String coordinate : coordinates) {
            assert containsDependency(coordinate, configuration)
        }

        where:
        runtime                | configuration          || coordinates
        'netty'                | 'compileClasspath'     || ['io.micronaut:micronaut-http-server-netty']
        'jetty'                | 'compileClasspath'     || ['io.micronaut.servlet:micronaut-http-server-jetty']
        'tomcat'               | 'compileClasspath'     || ['io.micronaut.servlet:micronaut-http-server-tomcat']
        'undertow'             | 'compileClasspath'     || ['io.micronaut.servlet:micronaut-http-server-undertow']
        'google_function'      | 'compileClasspath'     || ['io.micronaut.gcp:micronaut-gcp-function-http']
        'google_function'      | 'developmentOnly'      || ['com.google.cloud.functions:functions-framework-api', 'io.micronaut.gcp:micronaut-gcp-function-http-test']
        'google_function'      | 'compileOnly'          || ['com.google.cloud.functions:functions-framework-api']
        'google_function'      | 'testRuntimeClasspath' || ['com.google.cloud.functions:functions-framework-api', 'io.micronaut.gcp:micronaut-gcp-function-http-test']
        'azure_function'       | 'compileClasspath'     || ["io.micronaut.azure:micronaut-azure-function-http", "com.microsoft.azure.functions:azure-functions-java-library"]
        'azure_function'       | 'developmentOnly'      || ['io.micronaut.azure:micronaut-azure-function-http-test']
        'azure_function'       | 'testRuntimeClasspath' || ['io.micronaut.azure:micronaut-azure-function-http-test']
        'oracle_function'      | 'compileClasspath'     || ["io.micronaut.oraclecloud:micronaut-oraclecloud-function-http"]
        'oracle_function'      | 'developmentOnly'      || ["io.micronaut.oraclecloud:micronaut-oraclecloud-function-http-test"]
        'oracle_function'      | 'testRuntimeClasspath' || ["io.micronaut.oraclecloud:micronaut-oraclecloud-function-http-test"]
        'oracle_function'      | 'runtimeOnly'           || ["com.fnproject.fn:runtime"]
        'lambda'               | 'compileClasspath'     || ["io.micronaut.aws:micronaut-function-aws-api-proxy", "io.micronaut.aws:micronaut-function-aws-custom-runtime"]
        'lambda'               | 'developmentOnly'      || ["io.micronaut.aws:micronaut-function-aws-api-proxy-test"]
        'lambda'               | 'testRuntimeClasspath' || ["io.micronaut.aws:micronaut-function-aws-api-proxy-test"]

        description =  String.join(",", coordinates)
    }
}
