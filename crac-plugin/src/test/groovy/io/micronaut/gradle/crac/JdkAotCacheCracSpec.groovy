package io.micronaut.gradle.crac

import spock.lang.IgnoreIf
import spock.lang.Issue

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

@Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/1389")
@IgnoreIf({ os.windows })
class JdkAotCacheCracSpec extends BaseCracGradleBuildSpec {

    void "the JVM images do not train a JDK AOT cache by default when the CRaC plugin is applied"() {
        given: "a Micronaut core that has the load training mode, which the plugin only looks for in the class bytes"
        settingsFile << "rootProject.name = 'hello-world'"
        def module = new File(testProjectDir.root, "fake-repo/io/micronaut/micronaut-context/99.0.0")
        module.mkdirs()
        new JarOutputStream(new File(module, "micronaut-context-99.0.0.jar").newOutputStream()).withCloseable { jar ->
            jar.putNextEntry(new JarEntry("io/micronaut/runtime/Micronaut.class"))
            jar.write("micronaut.application.training.enabled".getBytes("UTF-8"))
            jar.closeEntry()
            jar.putNextEntry(new JarEntry("io/micronaut/runtime/ApplicationConfiguration.class"))
            jar.write("micronaut.application.training.mode".getBytes("UTF-8"))
            jar.closeEntry()
        }
        new File(module, "micronaut-context-99.0.0.pom").text = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.micronaut</groupId>
  <artifactId>micronaut-context</artifactId>
  <version>99.0.0</version>
</project>
"""
        buildFile << """
            ${getPluginsBlock()}

            micronaut {
                version "$micronautVersion"
            }

            repositories {
                mavenCentral()
                maven { url = file("fake-repo") }
            }

            configurations.runtimeClasspath {
                resolutionStrategy.dependencySubstitution {
                    substitute(module("io.micronaut:micronaut-context")).using(module("io.micronaut:micronaut-context:99.0.0"))
                }
            }

            dependencies {
                runtimeOnly("io.micronaut:micronaut-context")
            }

            application { mainClass = "example.Application" }
        """
        writeJavaFile("src/main/java/example/Application.java", "package example; class Application { public static void main(String... args) { } }")

        when:
        def result = build('dockerfile', 'buildLayers', '--info')

        then:
        result.output.contains("JDK AOT cache: not trained by default, because the CRaC plugin builds its images from the layers of the main image")
        !fileTextContents("build/docker/main/Dockerfile").contains("train.sh")
        new JarFile(new File(testProjectDir.root, "build/docker/main/layers/app/application.jar")).withCloseable {
            it.manifest.mainAttributes.getValue("Class-Path")
        }.startsWith("resources/ classes/ libs/")
    }
}
