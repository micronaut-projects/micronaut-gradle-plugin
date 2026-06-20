package io.micronaut.gradle

import org.gradle.testkit.runner.TaskOutcome

class MicronautMixedTestRuntimeSpec extends AbstractGradleBuildSpec {

    def "legacy single junit 5 runtime still works"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.library"
            }

            micronaut {
                version "$micronautVersion"
                testRuntime "junit5"
            }

            $repositoriesBlock
        """
        writeSupportBean()
        writeJunitTest()

        when:
        def result = build('test')

        then:
        result.task(":test").outcome == TaskOutcome.SUCCESS
        containsDependency("io.micronaut.test:micronaut-test-junit5", "testImplementation")
        containsDependency("org.junit.jupiter:junit-jupiter-engine", "testRuntimeOnly")
    }

    def "test mixed junit 5 and spock test runtimes"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.library"
                id "groovy"
            }

            micronaut {
                version "$micronautVersion"
                testRuntime "junit5"
                testRuntimes "spock2"
            }

            $repositoriesBlock
        """
        writeSupportBean()
        writeJunitTest()
        writeSpockTest()

        when:
        def result = build('test')

        then:
        result.task(":test").outcome == TaskOutcome.SUCCESS
        file("build/test-results/test/TEST-example.ExampleJUnitTest.xml").exists()
        file("build/test-results/test/TEST-example.ExampleSpockSpec.xml").exists()
        containsDependency("io.micronaut.test:micronaut-test-junit5", "testImplementation")
        containsDependency("org.junit.jupiter:junit-jupiter-api", "testImplementation")
        containsDependency("org.spockframework:spock-core", "testImplementation")
        containsDependency("io.micronaut.test:micronaut-test-spock", "testImplementation")
        containsDependency("org.junit.jupiter:junit-jupiter-engine", "testRuntimeOnly")
        containsDependency("org.junit.platform:junit-platform-launcher", "testRuntimeOnly")
    }

    def "test mixed test runtimes with kotlin dsl"() {
        given:
        settingsFile << "rootProject.name = \"hello-world\""
        kotlinBuildFile << """
            import io.micronaut.gradle.MicronautTestRuntime

            plugins {
                id("io.micronaut.minimal.library")
                id("groovy")
            }

            micronaut {
                version.set("$micronautVersion")
                testRuntimes(MicronautTestRuntime.JUNIT_5, MicronautTestRuntime.SPOCK_2)
            }

            ${getRepositoriesBlock('kotlin')}
        """

        when:
        def result = build('dependencies', '--configuration', 'testRuntimeClasspath')

        then:
        result.task(":dependencies").outcome == TaskOutcome.SUCCESS
        containsDependency("io.micronaut.test:micronaut-test-junit5", "testImplementation")
        containsDependency("org.spockframework:spock-core", "testImplementation")
        containsDependency("org.junit.jupiter:junit-jupiter-engine", "testRuntimeOnly")
    }

    private void writeSupportBean() {
        def javaFile = file("src/main/java/example/GreetingService.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import jakarta.inject.Singleton;

@Singleton
public class GreetingService {

    public String greet() {
        return "ok";
    }
}
"""
    }

    private void writeJunitTest() {
        def javaFile = file("src/test/java/example/ExampleJUnitTest.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
public class ExampleJUnitTest {

    @Inject
    GreetingService greetingService;

    @Test
    void testItWorks() {
        Assertions.assertEquals("ok", greetingService.greet());
    }
}
"""
    }

    private void writeSpockTest() {
        def groovyFile = file("src/test/groovy/example/ExampleSpockSpec.groovy")
        groovyFile.parentFile.mkdirs()
        groovyFile << """
package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class ExampleSpockSpec extends Specification {

    @Inject
    GreetingService greetingService

    def "test spock works"() {
        expect:
        greetingService.greet() == "ok"
    }
}
"""
    }
}
