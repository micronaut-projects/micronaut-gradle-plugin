package io.micronaut.gradle.jsonschema

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.MediaType

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response

class JsonSchemaGeneratorSpec extends AbstractGradleBuildSpec{
    def "can generate source code from URL"() {
        given:
        def mockServer = ClientAndServer.startClientAndServer()

        // Serve the FHIR JSON schema from a local file or byte array
        def jsonSchema = new File("src/test/resources/animal.schema.json").bytes

        mockServer.when(request().withPath("/animal.schema.json"))
                .respond(response()
                        .withStatusCode(200)
                        .withContentType(MediaType.JSON_UTF_8)
                        .withBody(jsonSchema))
        allowSnapshots = true
        settingsFile << "rootProject.name = 'jsonschema-url'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.jsonschema"
            }
            
            micronaut {
                version "$micronautVersion"
                jsonschema {
                    url("http://localhost:${mockServer.port}/animal.schema.json") {
                        acceptedUrlPatterns = ["^http://localhost:.*"]
                    }
                }
            }
            
            $repositoriesBlock

            dependencies {

                annotationProcessor "io.micronaut.serde:micronaut-serde-processor"
                annotationProcessor "io.micronaut.validation:micronaut-validation-processor"

                implementation "io.micronaut.serde:micronaut-serde-jackson"
                implementation "io.micronaut.validation:micronaut-validation"
            }

        """


        when:
        def result = build('test')

        then:
        result.task(":generatingSourcesFromAnimal").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated-sources/generatingSourcesFromAnimal/src/main/java/io/micronaut/jsonschema/").exists()
        file("build/generated-sources/generatingSourcesFromAnimal/src/main/java/io/micronaut/jsonschema/").list().size() == 5
        file("build/generated-sources/generatingSourcesFromAnimal/src/main/java/io/micronaut/jsonschema/Animal.java").exists()
    }

    def "can generate sources from a local file "() {
        given:
        allowSnapshots = true
        settingsFile << "rootProject.name = 'jsonschema-url'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.jsonschema"
            }
            
            micronaut {
                version "$micronautVersion"
                jsonschema {
                    fromFile(file("animal.schema.json")) {
                        outputPackageName = "com.example.animal"
                        lang = "java"
                        outputDirectory = project.getLayout().getBuildDirectory().dir("generated")
                    }
                }
            }
            
            $repositoriesBlock

            dependencies {

                annotationProcessor "io.micronaut.serde:micronaut-serde-processor"
                annotationProcessor "io.micronaut.validation:micronaut-validation-processor"

                implementation "io.micronaut.serde:micronaut-serde-jackson"
                implementation "io.micronaut.validation:micronaut-validation"
            }

        """
        withFile("animal.schema.json")

        when:
        def result = build('test')

        then:
        result.task(":generatingSourcesFromAnimal").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/generatingSourcesFromAnimal/src/main/java/com/example/animal/").exists()
        file("build/generated/generatingSourcesFromAnimal/src/main/java/com/example/animal/").list().size() == 5
        file("build/generated/generatingSourcesFromAnimal/src/main/java/com/example/animal/Animal.java").exists()
    }

    def "can generate sources from a local directory "() {
        given:
        allowSnapshots = true
        settingsFile << "rootProject.name = 'jsonschema-url'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.jsonschema"
            }
            
            micronaut {
                version "$micronautVersion"
                jsonschema {
                    fromFile(file("ref")) {
                        outputPackageName = "com.example.ref"
                    }
                }
            }
            
            $repositoriesBlock

            dependencies {

                annotationProcessor "io.micronaut.serde:micronaut-serde-processor"
                annotationProcessor "io.micronaut.validation:micronaut-validation-processor"

                implementation "io.micronaut.serde:micronaut-serde-jackson"
                implementation "io.micronaut.validation:micronaut-validation"
            }

        """
        withDirectory("/refFolder")

        when:
        def result = build('test')

        then:
        result.task(":generatingSourcesFromRef").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated-sources/generatingSourcesFromRef/src/main/java/com/example/ref/").exists()
        file("build/generated-sources/generatingSourcesFromRef/src/main/java/com/example/ref/").list().size() == 32
        file("build/generated-sources/generatingSourcesFromRef/src/main/java/com/example/ref/Cheatsheet.java").exists()
    }


    protected void withFile(String name) {
        file(name).text = this.class.getResourceAsStream("/" + name).getText("UTF-8")
    }

    protected void withDirectory(String name) {
        Path from = new File(this.class.getResource(name).toURI()).toPath()
        Files.walk(from).forEach(sourcePath -> {
            Path target = baseDir.resolve(from.relativize(sourcePath))
            if (Files.isDirectory(sourcePath)) {
                Files.createDirectories(target)
            } else {
                Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING)
            }
        })
    }

}
