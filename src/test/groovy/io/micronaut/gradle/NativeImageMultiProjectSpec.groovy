package io.micronaut.gradle

import io.micronaut.gradle.graalvm.GraalUtil
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Requires
import spock.lang.Specification

@Requires({ GraalUtil.isGraalJVM() })
class NativeImageMultiProjectSpec extends Specification {
    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        settingsFile << '''
rootProject.name="test-multi-project"

include "one"
include "two"

'''
        def projectFolder = testProjectDir.newFolder("two")
        buildFile = testProjectDir.newFile('two/build.gradle')
        buildFile << """
            plugins {
                id("com.github.johnrengelman.shadow") version "6.1.0"
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.3.4"
                runtime "netty"
            }
            
            repositories {
                mavenCentral()
            }
            
            
            application {
                mainClass.set("example.Application")
            }
            
        """

        def javaFile = new File(projectFolder, "src/main/java/example/Application.java")
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
    }

    void 'test build native image in subproject'() {
        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('two:nativeImage', '-i', '--stacktrace')
                .withPluginClasspath()
                .build()

        def task = result.task(":two:nativeImage")
        then:
        result.output.contains("Native Image written to")
        task.outcome == TaskOutcome.SUCCESS
    }
}
