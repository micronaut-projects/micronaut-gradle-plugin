package io.micronaut.gradle

import io.micronaut.gradle.graalvm.GraalUtil
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Specification

@Requires({ GraalUtil.isGraalJVM() })
class NativeImageTaskSpec extends Specification {
    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    @IgnoreIf({ os.isWindows() })
    def "test build native image"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.3.4"
                runtime "netty"
            }
            
            repositories {
                mavenCentral()
            }
            
            
            mainClassName="example.Application"
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import io.micronaut.runtime.Micronaut;

class Application {
    public static void main(String... args) {
        Micronaut.run(args);    
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('nativeImage')
                .withPluginClasspath()
                .build()

        def task = result.task(":nativeImage")
        then:
        result.output.contains("Native Image written to")
        task.outcome == TaskOutcome.SUCCESS
    }

    def "test build native image - custom name"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.3.4"
                runtime "netty"
            }
            
            repositories {
                mavenCentral()
            }
            
            
            mainClassName="example.Application"
            nativeImage {
                imageName("basic-app")
                args('-Dfoo=bar')
            }            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import io.micronaut.runtime.Micronaut;

class Application {
    public static void main(String... args) {
        Micronaut.run(args);    
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('nativeImage', '-i', '--stacktrace')
                .withPluginClasspath()
                .build()

        def task = result.task(":nativeImage")
        then:
        result.output.contains("Native Image written to")
        result.output.contains("[basic-app:")
        result.output.contains('-Dfoo=bar')
        task.outcome == TaskOutcome.SUCCESS
    }
}
