package io.micronaut.gradle


import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue

class MicronautApplicationPluginSpec extends AbstractGradleBuildSpec {

    def "test junit 5 test runtime"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "3.5.1"
                runtime "netty"
                testRuntime "junit5"
            }
            
            $repositoriesBlock
            mainClassName="example.Application"
        """
        testProjectDir.newFolder("src", "test", "java", "example")
        def javaFile = testProjectDir.newFile("src/test/java/example/ExampleTest.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import jakarta.inject.Inject;

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

        where:
        plugins << [
                'id "io.micronaut.application"',
                'id "io.micronaut.minimal.application"',
        ]
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
//                version "3.5.1"
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
//import io.micronaut.test.extensions.spock.annotation.MicronautTest
//import spock.lang.Specification
//import jakarta.inject.Inject
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

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/292")
    def "Groovy sources are found when configuring watch paths"() {
                given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
                id 'groovy'
            }

            micronaut {
                version "3.0.1"
                runtime "netty"
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation "org.codehaus.groovy:groovy:3.0.5"
            }
            mainClassName="example.Application"
        """

        testProjectDir.newFolder("src", "main", "groovy", "example")
        def groovyApp = testProjectDir.newFile("src/main/groovy/example/Application.groovy")

        groovyApp << """package example

            println "Watch paths: \${System.getProperty('micronaut.io.watch.paths')}"
        """

        when:
        def result = build('run', "-D${MicronautMinimalApplicationPlugin.INTERNAL_CONTINUOUS_FLAG}=true")
        def task = result.task(":run")
        def output = result.output.readLines()
        def watchLine = output.find { it.startsWith("Watch paths: ") }
            .replace(File.separatorChar, (char) '/')

        then:
        task.outcome == TaskOutcome.SUCCESS
        watchLine.contains 'src/main/groovy'
    }
}
