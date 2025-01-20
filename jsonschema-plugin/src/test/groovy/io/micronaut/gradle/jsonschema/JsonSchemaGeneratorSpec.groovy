package io.micronaut.gradle.jsonschema

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class JsonSchemaGeneratorSpec extends AbstractGradleBuildSpec{
    def "can generate source code from URL"() {
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
                    url("https://www.hl7.org/fhir/fhir.schema.json") {
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
        result.task(":generatingSourcesFromFhir").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/jsonschema/generatingSourcesFromFhir/src/main/java/io/micronaut/jsonschema/generated").exists()
        file("build/generated/jsonschema/generatingSourcesFromFhir/src/main/java/io/micronaut/jsonschema/generated").list().size() == 838
        file("build/generated/jsonschema/generatingSourcesFromFhir/src/main/java/io/micronaut/jsonschema/generated/Fhir.java").exists()
    }

    def "can generate source code from URL with output settings"() {
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
                    url("https://www.hl7.org/fhir/fhir.schema.json") {
                        lang = "java"
                        outputPackageName = "com.example.fhir"
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


        when:
        def result = build('test')

        then:
        result.task(":generatingSourcesFromFhir").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/generatingSourcesFromFhir/src/main/java/com/example/fhir/").exists()
        file("build/generated/generatingSourcesFromFhir/src/main/java/com/example/fhir/").list().size() == 838
        file("build/generated/generatingSourcesFromFhir/src/main/java/com/example/fhir/Fhir.java").exists()
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
                    file(file("animal.schema.json")) {
                        outputPackageName = "com.example.animal"
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
        file("build/generated/jsonschema/generatingSourcesFromAnimal/src/main/java/com/example/animal/").exists()
        file("build/generated/jsonschema/generatingSourcesFromAnimal/src/main/java/com/example/animal/").list().size() == 5
        file("build/generated/jsonschema/generatingSourcesFromAnimal/src/main/java/com/example/animal/Animal.java").exists()
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
                    folder(file("ref")) {
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
        file("build/generated/jsonschema/generatingSourcesFromRef/src/main/java/com/example/ref/").exists()
        file("build/generated/jsonschema/generatingSourcesFromRef/src/main/java/com/example/ref/").list().size() == 32
        file("build/generated/jsonschema/generatingSourcesFromRef/src/main/java/com/example/ref/Cheatsheet.java").exists()
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
