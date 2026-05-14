package io.micronaut.openapi.gradle

import org.gradle.testkit.runner.TaskOutcome

class OpenApiGenericGeneratorSpec extends AbstractOpenApiGeneratorSpec {

    def "can generate sources with a generic OpenAPI generator"() {
        given:
        settingsFile << "rootProject.name = 'openapi-generic'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.openapi"
            }

            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
                openapi {
                    generic("pet", file("petstore.json")) {
                        generatorClassName = "io.micronaut.openapi.generator.JavaMicronautClientCodegen"
                        outputKinds = ["models"]
                        generatorProperties.put("retryable", "true")
                        generatorProperties.put("retryableAttempts", "5")
                        generatorProperties.put("clientId", "pet-client")
                    }
                }
            }

            $repositoriesBlock
            application { mainClass = "example.Application" }
        """

        withPetstore()

        when:
        def result = build('generatePetOpenApi')

        then:
        result.task(":generatePetOpenApi").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/generatePetOpenApi/src/main/java/io/micronaut/openapi/model/Pet.java").exists()
    }

    def "generic OpenAPI generated source directory is wired into the main source set"() {
        given:
        settingsFile << "rootProject.name = 'openapi-generic-source-set'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.openapi"
            }

            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
                openapi {
                    generic("pet", file("petstore.json")) {
                        generatorClassName = "io.micronaut.openapi.generator.JavaMicronautClientCodegen"
                    }
                }
            }

            $repositoriesBlock
            application { mainClass = "example.Application" }

            tasks.register("printMainJavaSourceDirs") {
                doLast {
                    sourceSets.main.java.srcDirs.each {
                        println("sourceDir=" + it.path)
                    }
                }
            }
        """

        withPetstore()

        when:
        def result = build('printMainJavaSourceDirs')

        then:
        result.output.contains("build/generated/openapi/generatePetOpenApi/src/main/java")
    }
}
