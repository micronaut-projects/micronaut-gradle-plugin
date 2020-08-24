package io.micronaut.gradle


import io.micronaut.gradle.docker.DockerSettings
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class DockerBuildTaskSpec extends Specification {

    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def "test build docker image"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.0.1"
                
                docker {
                    tag "my-tag"
                }
            }
            
            repositories {
                jcenter()
                mavenCentral()
            }
            
            
            mainClassName="example.Application"
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

class Application {
    public static void main(String... args) {
    
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('buildDockerImage')
                .withPluginClasspath()
                .build()

        def task = result.task(":buildDockerImage")
        then:
        result.output.contains("Successfully tagged my-tag:latest")
        task.outcome == TaskOutcome.SUCCESS
    }

    def "test build docker native image"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.0.1"
                
                docker {
                    tag "my-tag"
                }
            }
            
            repositories {
                jcenter()
                mavenCentral()
            }
            
            
            mainClassName="example.Application"
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

class Application {
    public static void main(String... args) {
    
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('buildDockerNativeImage')
                .withPluginClasspath()
                .build()

        def task = result.task(":buildDockerNativeImage")
        then:
        result.output.contains("Successfully tagged my-tag:latest")
        task.outcome == TaskOutcome.SUCCESS
    }

}
