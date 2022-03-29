package io.micronaut.gradle

import spock.lang.Narrative
import spock.lang.Unroll

@Narrative("""\
Micronaut Gradle plugin adds dependencies depending on the environment being target.
""")
class RuntimeDependenciesSpec extends AbstractGradleBuildSpec {

    @Unroll
    def "application type: #applicationType and #runtime runtime adds #description for the #configuration" (ApplicationType applicationType,
                                                                     String runtime,
                                                                     String configuration,
                                                                     List<String> coordinates,
                                                                     List<String> missingCoordinates,
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
                applicationType "${applicationType.name()}"                
            }
            
            $repositoriesBlock                                   
        """

        expect:
        for (String coordinate : coordinates) {
            assert containsDependency(coordinate, configuration)
        }
        for (String coordinate : missingCoordinates) {
            assert !containsDependency(coordinate, configuration)
        }

        where:
        applicationType         | runtime                | configuration          || coordinates                                                                                                        | missingCoordinates
        ApplicationType.DEFAULT | 'netty'                | 'compileClasspath'     || ['io.micronaut:micronaut-http-server-netty']                                                                       | []
        ApplicationType.DEFAULT | 'jetty'                | 'compileClasspath'     || ['io.micronaut.servlet:micronaut-http-server-jetty']                                                               | []
        ApplicationType.DEFAULT | 'tomcat'               | 'compileClasspath'     || ['io.micronaut.servlet:micronaut-http-server-tomcat']                                                              | []
        ApplicationType.DEFAULT | 'undertow'             | 'compileClasspath'     || ['io.micronaut.servlet:micronaut-http-server-undertow']                                                            | []
        ApplicationType.DEFAULT | 'google_function'      | 'compileClasspath'     || ['io.micronaut.gcp:micronaut-gcp-function-http']                                                                   | []
        ApplicationType.DEFAULT | 'google_function'      | 'developmentOnly'      || ['com.google.cloud.functions:functions-framework-api', 'io.micronaut.gcp:micronaut-gcp-function-http-test']        | []
        ApplicationType.DEFAULT | 'google_function'      | 'compileOnly'          || ['com.google.cloud.functions:functions-framework-api']                                                             | []
        ApplicationType.DEFAULT | 'google_function'      | 'testRuntimeClasspath' || ['com.google.cloud.functions:functions-framework-api', 'io.micronaut.gcp:micronaut-gcp-function-http-test']        | []
        ApplicationType.DEFAULT | 'azure_function'       | 'compileClasspath'     || ["io.micronaut.azure:micronaut-azure-function-http", "com.microsoft.azure.functions:azure-functions-java-library"] | []
        ApplicationType.DEFAULT | 'azure_function'       | 'developmentOnly'      || ['io.micronaut.azure:micronaut-azure-function-http-test']                                                          | []
        ApplicationType.DEFAULT | 'azure_function'       | 'testRuntimeClasspath' || ['io.micronaut.azure:micronaut-azure-function-http-test']                                                          | []
        ApplicationType.DEFAULT | 'oracle_function'      | 'compileClasspath'     || ["io.micronaut.oraclecloud:micronaut-oraclecloud-function-http"]                                                   | []
        ApplicationType.DEFAULT | 'oracle_function'      | 'developmentOnly'      || ["io.micronaut.oraclecloud:micronaut-oraclecloud-function-http-test"]                                              | []
        ApplicationType.DEFAULT | 'oracle_function'      | 'testRuntimeClasspath' || ["io.micronaut.oraclecloud:micronaut-oraclecloud-function-http-test"]                                              | []
        ApplicationType.DEFAULT | 'oracle_function'      | 'runtimeOnly'           || ["com.fnproject.fn:runtime"]                                                                                      | []
        ApplicationType.FUNCTION | 'lambda'               | 'compileClasspath'     || ["io.micronaut.aws:micronaut-function-aws-custom-runtime"]                                                        | ['io.micronaut.aws:micronaut-function-aws-api-proxy']
        ApplicationType.FUNCTION | 'lambda'               | 'testRuntimeClasspath' || []                                                                                                                | ['io.micronaut.aws:micronaut-function-aws-api-proxy-test']
        ApplicationType.FUNCTION | 'lambda'               | 'developmentOnly'      || []                                                                                                                | ['io.micronaut.aws:micronaut-function-aws-api-proxy-test']
        ApplicationType.DEFAULT  | 'lambda'               | 'compileClasspath'     || ["io.micronaut.aws:micronaut-function-aws-api-proxy", "io.micronaut.aws:micronaut-function-aws-custom-runtime"]   | []
        ApplicationType.DEFAULT  | 'lambda'               | 'developmentOnly'      || ["io.micronaut.aws:micronaut-function-aws-api-proxy-test"]                                                        | []
        ApplicationType.DEFAULT  | 'lambda'               | 'testRuntimeClasspath' || ["io.micronaut.aws:micronaut-function-aws-api-proxy-test"]                                                        | []
        ApplicationType.DEFAULT  | 'lambda_java'          | 'compileClasspath'     || []                                                                                                                | ["io.micronaut.aws:micronaut-function-aws-api-proxy", "io.micronaut.aws:micronaut-function-aws-custom-runtime"]
        ApplicationType.DEFAULT  | 'lambda_java'          | 'developmentOnly'      || []                                                                                                                | ["io.micronaut.aws:micronaut-function-aws-api-proxy-test"]
        ApplicationType.DEFAULT  | 'lambda_java'          | 'testRuntimeClasspath' || []                                                                                                                | ["io.micronaut.aws:micronaut-function-aws-api-proxy-test"]

        description =  String.join(",", coordinates) + " and does not add " + String.join(',', missingCoordinates)
    }
}
