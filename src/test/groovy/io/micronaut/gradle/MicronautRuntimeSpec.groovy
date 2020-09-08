package io.micronaut.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

class MicronautRuntimeSpec extends Specification {
    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    @Unroll
    def "test execute tests for application for runtime: #runtime"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.1.0.BUILD-SNAPSHOT"
                runtime "$runtime"
                testRuntime "junit5"
            }
            
            repositories {
                jcenter()
                mavenCentral()
                maven { url "https://oss.jfrog.org/oss-snapshot-local" }
            }
            
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
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.micronaut.http.client.annotation.*;
import javax.inject.Inject;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class FooControllerTest {

    @Inject
    @Client("/")
    RxHttpClient client;

    @Test
    public void testIndex() throws Exception {
        assertEquals(HttpStatus.OK, client.toBlocking().exchange("/foo").status());
    }
}
"""

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('test')
                .withPluginClasspath()
                .build()

        def task = result.task(":test")
        expect:
        task.outcome == TaskOutcome.SUCCESS

        where:
        runtime << ["netty", "lambda", "jetty", "oracle_function", "tomcat", "google_function"]
    }
}
