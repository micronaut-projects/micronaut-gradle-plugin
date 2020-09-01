package io.micronaut.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class MicronautApplicationPluginSpec extends Specification {

    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def "test junit 5 test runtime"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.0.1"
                runtime "netty"
                testRuntime "junit5"
            }
            
            repositories {
                jcenter()
                mavenCentral()
            }
            mainClassName="example.Application"
        """
        testProjectDir.newFolder("src", "test", "java", "example")
        def javaFile = testProjectDir.newFile("src/test/java/example/ExampleTest.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import javax.inject.Inject;

@MicronautTest
public class ExampleTest {

    @Inject
    EmbeddedApplication application;

    @Test
    void testItWorks() {
        Assertions.assertTrue(application.isRunning());
    }

}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('test')
                .withPluginClasspath()
                .build()

        def task = result.task(":test")
        then:
        result.output.contains('Creating bean classes for 1 type elements')
        task.outcome == TaskOutcome.SUCCESS
    }

//    def "test spock test runtime"() {
//        given:
//        settingsFile << "rootProject.name = 'hello-world'"
//        buildFile << """
//            plugins {
//                id "io.micronaut.application"
//                id 'groovy'
//            }
//
//            micronaut {
//                version "2.0.1"
//                runtime "netty"
//                testRuntime "spock"
//            }
//
//            repositories {
//                jcenter()
//                mavenCentral()
//            }
//
//            dependencies {
//                testImplementation "org.codehaus.groovy:groovy:3.0.5"
//            }
//            mainClassName="example.Application"
//        """
//        testProjectDir.newFolder("src", "test", "groovy", "example")
//        def javaFile = testProjectDir.newFile("src/test/groovy/example/ExampleSpec.groovy")
//        javaFile.parentFile.mkdirs()
//        javaFile << """
//package example
//
//import io.micronaut.runtime.EmbeddedApplication
//import io.micronaut.test.annotation.MicronautTest
//import spock.lang.Specification
//import javax.inject.Inject
//
//@MicronautTest
//class ExampleSpec extends Specification {
//
//    @Inject
//    EmbeddedApplication application
//
//    void 'test it works'() {
//        expect:
//        application.running
//    }
//
//}
//"""
//
//        when:
//        def result = GradleRunner.create()
//                .withProjectDir(testProjectDir.root)
//                .withArguments('test')
//                .withPluginClasspath()
//                .build()
//
//        def task = result.task(":test")
//        then:
//        result.output.contains('Creating bean classes for 1 type elements')
//        task.outcome == TaskOutcome.SUCCESS
//    }

}
