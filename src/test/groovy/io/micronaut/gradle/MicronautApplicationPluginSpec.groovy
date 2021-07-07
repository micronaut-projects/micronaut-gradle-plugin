package io.micronaut.gradle


import org.gradle.testkit.runner.TaskOutcome

class MicronautApplicationPluginSpec extends AbstractGradleBuildSpec {

    def "test junit 5 test runtime"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.3.3"
                runtime "netty"
                testRuntime "junit5"
            }
            
            repositories {
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
        def result = build('test')

        def task = result.task(":test")
        println result.output
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
//                version "2.3.3"
//                runtime "netty"
//                testRuntime "spock"
//            }
//
//            repositories {
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
