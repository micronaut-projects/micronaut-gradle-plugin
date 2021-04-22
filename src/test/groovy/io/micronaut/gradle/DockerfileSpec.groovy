package io.micronaut.gradle

import io.micronaut.gradle.graalvm.GraalUtil
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Requires
import spock.lang.Specification

class DockerfileSpec extends Specification {

    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    @Requires({ GraalUtil.isGraalJVM() })
    void 'test build native docker file'() {
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
            
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('dockerfileNative')
                .withPluginClasspath()
                .build()

        def task = result.task(":dockerfileNative")
        println(result.output)
        
        then:
        task.outcome == TaskOutcome.SUCCESS
        result.output.contains("Dockerfile written to")
        result.output.contains("build/docker/DockerfileNative")

        new File("$testProjectDir.root/build/docker/DockerfileNative").text.count("-cp") == 1
    }
}
