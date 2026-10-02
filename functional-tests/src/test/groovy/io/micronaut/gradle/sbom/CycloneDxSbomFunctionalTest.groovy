package io.micronaut.gradle.sbom

import groovy.json.JsonSlurper
import io.micronaut.gradle.fixtures.AbstractFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

import java.util.jar.JarFile

class CycloneDxSbomFunctionalTest extends AbstractFunctionalTest {

    def "CycloneDX tasks are absent when the CycloneDX plugin is not applied"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id 'io.micronaut.application'
            }

            $repositoriesBlock

            micronaut {
                version "$micronautVersion"
            }
        """

        when:
        def result = build("tasks")

        then:
        result.task(":tasks").outcome == TaskOutcome.SUCCESS
        !result.output.contains("cyclonedxDirectBom")
        !file("build/reports/micronaut/sbom/application.cdx.json").exists()
    }

    def "no CycloneDX build is compatible with configuration cache"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id 'io.micronaut.application'
            }

            $repositoriesBlock

            micronaut {
                version "$micronautVersion"
            }
        """

        when:
        def result = build("tasks", "--configuration-cache")

        then:
        result.output.contains("Configuration cache entry stored")
        !result.output.contains("cyclonedxDirectBom")
    }

    @Unroll
    def "configures CycloneDX SBOM defaults when plugins are applied in #order order"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                ${plugins}
            }

            group = 'example'
            version = '1.0.0'

            $repositoriesBlock

            micronaut {
                version "$micronautVersion"
            }

            dependencies {
                runtimeOnly 'com.fasterxml.jackson.core:jackson-databind:2.17.2'
                testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.3'
            }
        """

        when:
        def result = build("cyclonedxDirectBom")
        def sbom = file("build/reports/micronaut/sbom/application.cdx.json")
        def json = new JsonSlurper().parse(sbom)
        def text = sbom.text

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        sbom.exists()
        !file("build/reports/micronaut/sbom/application.cdx.xml").exists()
        json.bomFormat == "CycloneDX"
        json.metadata.component.name == "hello-world"
        text.contains("jackson-databind")
        !text.contains("junit-jupiter-api")

        where:
        order                | plugins
        "Micronaut first"    | "id 'io.micronaut.application'\n                id 'org.cyclonedx.bom' version '3.2.4'"
        "CycloneDX first"    | "id 'org.cyclonedx.bom' version '3.2.4'\n                id 'io.micronaut.application'"
    }

    def "packages CycloneDX SBOM in the JVM jar when enabled"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id 'io.micronaut.application'
                id 'org.cyclonedx.bom' version '3.2.4'
            }

            group = 'example'
            version = '1.0.0'

            $repositoriesBlock

            micronaut {
                version "$micronautVersion"
                sbom {
                    includeInJar.set(true)
                }
            }
        """

        when:
        def result = build("jar")
        def jarFile = file("build/libs/hello-world-1.0.0.jar")
        def jar = new JarFile(jarFile)
        def manifest = jar.manifest.mainAttributes

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        jar.getEntry("META-INF/sbom/application.cdx.json") != null
        manifest.getValue("Micronaut-SBOM-Format") == "CycloneDX JSON"
        manifest.getValue("Micronaut-SBOM-Location") == "META-INF/sbom/application.cdx.json"

        cleanup:
        jar?.close()
    }

    def "packages CycloneDX SBOM in runner jar when enabled"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id 'io.micronaut.application'
                id 'org.cyclonedx.bom' version '3.2.4'
            }

            group = 'example'
            version = '1.0.0'

            $repositoriesBlock

            micronaut {
                version "$micronautVersion"
                sbom {
                    includeInJar.set(true)
                }
            }

            application {
                mainClass.set("example.Application")
            }
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        testProjectDir.newFile("src/main/java/example/Application.java") << """
package example;

public class Application {
    public static void main(String... args) {
    }
}
"""

        when:
        def result = build("runnerJar")
        def jarFile = file("build/libs/hello-world-1.0.0-runner.jar")
        def jar = new JarFile(jarFile)
        def manifest = jar.manifest.mainAttributes

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        result.task(":runnerJar").outcome == TaskOutcome.SUCCESS
        jar.getEntry("META-INF/sbom/application.cdx.json") != null
        manifest.getValue("Micronaut-SBOM-Format") == "CycloneDX JSON"
        manifest.getValue("Micronaut-SBOM-Location") == "META-INF/sbom/application.cdx.json"

        cleanup:
        jar?.close()
    }

    def "does not package or generate an SBOM from jar by default"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id 'io.micronaut.application'
                id 'org.cyclonedx.bom' version '3.2.4'
            }

            group = 'example'
            version = '1.0.0'

            $repositoriesBlock

            micronaut {
                version "$micronautVersion"
            }
        """

        when:
        def result = build("jar")
        def jarFile = file("build/libs/hello-world-1.0.0.jar")
        def jar = new JarFile(jarFile)
        def manifest = jar.manifest.mainAttributes

        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        result.task(":cyclonedxDirectBom") == null
        jar.getEntry("META-INF/sbom/application.cdx.json") == null
        manifest.getValue("Micronaut-SBOM-Format") == null
        manifest.getValue("Micronaut-SBOM-Location") == null

        cleanup:
        jar?.close()
    }

    def "CycloneDX generation is compatible with configuration cache"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id 'io.micronaut.application'
                id 'org.cyclonedx.bom' version '3.2.4'
            }

            group = 'example'
            version = '1.0.0'

            $repositoriesBlock

            micronaut {
                version "$micronautVersion"
            }
        """

        when:
        def result = build("cyclonedxDirectBom", "--configuration-cache")

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        result.output.contains("Configuration cache entry stored")
        file("build/reports/micronaut/sbom/application.cdx.json").exists()
    }

    def "can configure CycloneDX XML output directly"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id 'io.micronaut.application'
                id 'org.cyclonedx.bom' version '3.2.4'
            }

            group = 'example'
            version = '1.0.0'

            $repositoriesBlock

            micronaut {
                version "$micronautVersion"
            }

            tasks.named("cyclonedxDirectBom") {
                xmlOutput.set(layout.buildDirectory.file("reports/micronaut/sbom/application.cdx.xml"))
            }
        """

        when:
        def result = build("cyclonedxDirectBom")

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        file("build/reports/micronaut/sbom/application.cdx.json").exists()
        file("build/reports/micronaut/sbom/application.cdx.xml").exists()
    }

    def "can disable Micronaut CycloneDX defaults"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id 'io.micronaut.application'
                id 'org.cyclonedx.bom' version '3.2.4'
            }

            $repositoriesBlock

            micronaut {
                version "$micronautVersion"
                sbom {
                    enabled.set(false)
                }
            }
        """

        when:
        def result = build("cyclonedxDirectBom")

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        !file("build/reports/micronaut/sbom/application.cdx.json").exists()
    }
}
