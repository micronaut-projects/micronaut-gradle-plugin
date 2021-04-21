package io.micronaut.gradle

import io.micronaut.gradle.graalvm.GraalUtil
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Requires
import spock.lang.Specification

@Requires({ GraalUtil.isGraalJVM() && !os.windows })
class NativeImageTestTaskSpec extends Specification {
    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def "test execute tests against native image"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
                id "com.adarshr.test-logger" version "2.1.1"
            }
            
            micronaut {
                version "2.3.4"
                runtime "netty"
                testRuntime "junit5"
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                runtimeOnly("org.slf4j:slf4j-simple")
                testImplementation("io.micronaut:micronaut-http-client")
                testImplementation("io.micronaut.test:micronaut-test-core:2.2.1") {
                    version {
                        strictly("2.2.1")
                    }
                }
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
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.micronaut.http.client.annotation.*;
import javax.inject.Inject;
import static org.junit.jupiter.api.Assertions.*;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.support.server.TestExecutableEmbeddedServer;

@MicronautTest
public class FooControllerTest {

    @Inject
    @Client("/")
    RxHttpClient client;
    
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

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('testNativeImage', '--stacktrace')
                .withPluginClasspath()
                .build()

        def task = result.task(":testNativeImage")
        expect:
        result.output.contains("Executing Controller")
        task.outcome == TaskOutcome.SUCCESS
    }
}
