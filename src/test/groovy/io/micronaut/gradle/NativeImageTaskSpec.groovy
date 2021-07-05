package io.micronaut.gradle

import io.micronaut.gradle.graalvm.GraalUtil
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Requires

@Requires({ GraalUtil.isGraalJVM() })
class NativeImageTaskSpec extends AbstractGradleBuildSpec {

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
        def result = build('nativeImage')

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
        def result = build('nativeImage', '-i', '--stacktrace')

        def task = result.task(":nativeImage")
        then:
        result.output.contains("Native Image written to")
        result.output.contains("[basic-app:")
        result.output.contains('-Dfoo=bar')
        task.outcome == TaskOutcome.SUCCESS
    }
}
