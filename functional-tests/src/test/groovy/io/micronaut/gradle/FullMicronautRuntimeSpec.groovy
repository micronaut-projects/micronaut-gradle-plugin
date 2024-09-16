package io.micronaut.gradle

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

class FullMicronautRuntimeSpec extends AbstractEagerConfiguringFunctionalTest {

    @Unroll
    def "test execute tests for application for runtime: #runtime"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
                id("com.github.johnrengelman.shadow") version("$shadowVersion")
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "$runtime"
                testRuntime "junit5"
            }
            
            $repositoriesBlock
            
            dependencies {
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }
            
            mainClassName="example.Application"
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/FooController.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import io.micronaut.http.annotation.*;

@Controller("/foo")
public class FooController {

    @Get(uri="/", produces="text/plain")
    public String index() {
        return "Example Response";
    }
}
"""

        testProjectDir.newFolder("src", "test", "java", "example")
        def testJavaFile = testProjectDir.newFile("src/test/java/example/FooControllerTest.java")
        testJavaFile.parentFile.mkdirs()
        testJavaFile << """
package example;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.micronaut.http.client.annotation.*;
import jakarta.inject.Inject;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class FooControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    public void testIndex() throws Exception {
        assertEquals(HttpStatus.OK, client.toBlocking().exchange("/foo").status());
    }
}
"""

        def result = build('testClasses')

        def task = result.task(":testClasses")
        expect:
        task.outcome == TaskOutcome.SUCCESS

        where:
        runtime << ["netty", "lambda_java", "lambda_provided", "jetty", "oracle_function", "tomcat", "google_function", "http_poja"]
    }
}
