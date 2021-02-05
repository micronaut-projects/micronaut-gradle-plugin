package io.micronaut.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class LambdaNativeImageSpec extends Specification {

    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    void 'mainclass is set correctly for an application deployed as GraalVM and Lambda'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.3.0"
                runtime "netty"
            }
            
            repositories {
                mavenCentral()
            }
            
            application {
                mainClass.set("com.example.Application")
            }
            
            java {
                sourceCompatibility = JavaVersion.toVersion('8')
                targetCompatibility = JavaVersion.toVersion('8')
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('dockerfileNative', '-Pmicronaut.runtime=lambda')
            .withPluginClasspath()
            .build()

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find() { it.contains('-H:Class=io.micronaut.function.aws.runtime.MicronautLambdaRuntime')}
        !dockerFileNative.find() { it.contains('com.example.Application')}
    }

    void 'it is possible to define the mainclass for a dockerfile native'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.3.0"
                runtime "netty"
            }
            
            repositories {
                mavenCentral()
            }
            
            application {
                mainClass.set("com.example.Application")
            }
            
            java {
                sourceCompatibility = JavaVersion.toVersion('8')
                targetCompatibility = JavaVersion.toVersion('8')
            }
            
            nativeImage {
                main("my.own.main.class")
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('dockerfileNative')
            .withPluginClasspath()
            .build()

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find() { it.contains('my.own.main.class')}
        !dockerFileNative.find() { it.contains('com.example.Application')}
    }
}
