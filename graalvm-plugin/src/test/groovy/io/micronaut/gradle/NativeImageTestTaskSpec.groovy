package io.micronaut.gradle


import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Requires

@Requires({ AbstractGradleBuildSpec.graalVmAvailable && !os.windows })
@Requires({ jvm.isJava11() })
class NativeImageTestTaskSpec extends AbstractGradleBuildSpec {

    def "test execute tests against native image"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
                id "com.adarshr.test-logger" version "2.1.1"
            }
            
            micronaut {
                version "3.5.1"
                runtime "netty"
                testRuntime "junit5"
            }
            
            $repositoriesBlock
            
            dependencies {
                runtimeOnly("org.slf4j:slf4j-simple")
                testImplementation("io.micronaut:micronaut-http-client")
                testImplementation("io.micronaut.test:micronaut-test-core")
            }
            
            mainClassName="example.Application"
            
            java {
                sourceCompatibility = JavaVersion.toVersion('1.8')
                targetCompatibility = JavaVersion.toVersion('1.8')
            }            
            testlogger {
               showStandardStreams true
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
        def controllerFile = testProjectDir.newFile("src/main/java/example/FooController.java")
        controllerFile.parentFile.mkdirs()
        controllerFile << """
package example;

import io.micronaut.http.annotation.*;
import io.micronaut.context.annotation.Requires;

@Controller("/foo")
@Requires(env="test")
public class FooController {

    @Get(uri="/", produces="text/plain")
    public String index() {
        System.out.println("Executing Controller");
        return "Example Response";
    }
}
"""

        testProjectDir.newFolder("src", "test", "resources")
        def loggingConfig = testProjectDir.newFile("src/test/resources/simplelogger.properties")
        loggingConfig.parentFile.mkdirs()
        loggingConfig << '''
org.slf4j.simpleLogger.defaultLogLevel=info
org.slf4j.simpleLogger.log.io.micronaut=info
'''
        testProjectDir.newFolder("src", "test", "java", "example")
        def testJavaFile = testProjectDir.newFile("src/test/java/example/FooControllerTest.java")
        testJavaFile.parentFile.mkdirs()
        testJavaFile << """
package example;

import io.micronaut.http.*;
import io.micronaut.http.client.HttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.micronaut.http.client.annotation.*;
import jakarta.inject.Inject;
import static org.junit.jupiter.api.Assertions.*;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.support.server.TestExecutableEmbeddedServer;

@MicronautTest
public class FooControllerTest {

    @Inject
    @Client("/")
    HttpClient client;
    
    @Inject
    EmbeddedServer embeddedServer;

    @Test
    public void testIndex() throws Exception {
        assertTrue(
            embeddedServer instanceof TestExecutableEmbeddedServer
        );
        HttpResponse<String> response = client.toBlocking().exchange("/foo", String.class);
        assertEquals(HttpStatus.OK, response.status());
        assertEquals("Example Response", response.body());
    }
}
"""

        def result = build('testNativeImage', '--stacktrace')

        def task = result.task(":testNativeImage")
        expect:
        result.output.contains("Executing Controller")
        task.outcome == TaskOutcome.SUCCESS
    }
}
