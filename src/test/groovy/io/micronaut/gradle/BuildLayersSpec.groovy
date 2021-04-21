package io.micronaut.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class BuildLayersSpec extends Specification {

    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    void 'test build layers with custom build directory'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.4.2"
                runtime "netty"
                testRuntime "junit5"
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }
            mainClassName="example.Application"
            
            project.buildDir = "build-custom"
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('buildLayers')
                .withPluginClasspath()
                .build()

        def task = result.task(":buildLayers")

        then:
        task.outcome == TaskOutcome.SUCCESS
        new File(testProjectDir.root, "build-custom/layers").exists()
        !new File(testProjectDir.root, "build/layers").exists()
    }
}
