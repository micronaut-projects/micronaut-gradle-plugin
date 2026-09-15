package io.micronaut.gradle.docker

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome

class DockerNativeImageOptionsFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    def "dockerfileNative native image options delegate the layer options"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }

            micronaut {
                version "$micronautVersion"
                runtime "netty"
            }

            $repositoriesBlock

            application { mainClass = "example.Application" }

            graalvmNative.binaries.main {
                layerNames.add("base-layer")
            }

            tasks.named("dockerfileNative") {
                doFirst {
                    def options = nativeImageOptions.get()
                    options.layers { }
                    println "layers=\${options.layers.size()}"
                    println "layerCreate=\${options.layerCreate.present}"
                    println "layerFiles=\${options.layerFiles.files.size()}"
                    println "layerNames=\${options.layerNames.get()}"
                    println "layer=\${options.layer}"
                }
            }
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        testProjectDir.newFile("src/main/java/example/Application.java") << """
package example;

class Application {
    public static void main(String... args) {
    }
}
"""

        when:
        def result = build('dockerfileNative')

        then:
        result.task(":dockerfileNative").outcome == TaskOutcome.SUCCESS
        result.output.contains("layers=0")
        result.output.contains("layerCreate=false")
        result.output.contains("layerFiles=0")
        result.output.contains("layerNames=[base-layer]")
        result.output.contains("layer=null")

        when:
        def nativeImageLine = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative')
                .readLines('UTF-8')
                .find { it.startsWith('RUN native-image ') }

        then:
        nativeImageLine.contains('--no-fallback')
        !nativeImageLine.contains('--color')
        nativeImageLine.endsWith('example.Application')
    }
}
