package io.micronaut.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class MicronautLibraryPluginSpec extends Specification {

    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile
    File kotlinBuildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
        kotlinBuildFile = testProjectDir.newFile('build.gradle.kts')
    }

    def "test add jaxrs processing"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
            }
            
            micronaut {
                version "2.0.0.RC2"
            }
            
            repositories {
                jcenter()
                mavenCentral()
            }
            
            dependencies {
                implementation("io.micronaut.jaxrs:micronaut-jaxrs-server")
            }
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Foo.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/foo")
public class Foo {

    @GET
    @Path("/")
    public String index() {
        return "Example Response";
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('assemble', "--stacktrace")
                .withPluginClasspath()
                .build()

        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")
        new File(
                testProjectDir.getRoot(),
                'build/classes/java/main/example/$FooDefinition.class'
        ).exists()
    }

    def "test add openapi processing"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
            }
            
            micronaut {
                version "2.0.0.RC2"
                
                processing {
                    incremental true
                }
            }
            
            repositories {
                jcenter()
                mavenCentral()
            }
            
            dependencies {
                annotationProcessor "io.micronaut.configuration:micronaut-openapi"
                compileOnly "io.swagger.core.v3:swagger-annotations"
            }
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Foo.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.info.*;


@javax.inject.Singleton
@OpenAPIDefinition(
    info = @Info(
            title = "demo",
            version = "0.0"
    )
)
class Foo {}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('assemble')
                .withPluginClasspath()
                .build()

        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")
        result.output.contains("Generating OpenAPI Documentation")
        new File(
                testProjectDir.getRoot(),
                'build/classes/java/main/example/$FooDefinition.class'
        ).exists()
    }

    def "test apply defaults for micronaut-library and java"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
            }
            
            micronaut {
                version "2.0.0.RC1"
                
                processing {
                    incremental true
                }
            }
            
            repositories {
                jcenter()
                mavenCentral()
            }
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Foo.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

@javax.inject.Singleton
class Foo {}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('assemble')
                .withPluginClasspath()
                .build()

        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")
        new File(
                testProjectDir.getRoot(),
                'build/classes/java/main/example/$FooDefinition.class'
        ).exists()
    }

    def "test apply junit 5 platform is junit jupiter is present"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
            }
            
            micronaut {
                version "2.0.0.RC2"
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                testImplementation("io.micronaut.test:micronaut-test-junit5")
                testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")            
            }
            
        """
        testProjectDir.newFolder("src", "test", "java", "example")
        def javaFile = testProjectDir.newFile("src/test/java/example/Foo.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import io.micronaut.context.BeanContext;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import javax.inject.Inject;

@MicronautTest
class Foo {
    @Inject
    BeanContext context;

    @Test
    void testItWorks() {
        Assertions.assertTrue(context.isRunning());
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('test')
                .withPluginClasspath()
                .build()

        then:
        result.task(":test").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")
    }

    def "test apply defaults for micronaut-library and groovy"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
                id "groovy"
            }
            
            micronaut {
                version "2.0.0.RC1"
            }
            
            repositories {
                jcenter()
                mavenCentral()
            }
            
        """
        testProjectDir.newFolder("src", "main", "groovy", "example")
        def javaFile = testProjectDir.newFile("src/main/groovy/example/Foo.groovy")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

@javax.inject.Singleton
class Foo {}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('assemble')
                .withPluginClasspath()
                .build()

        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        new File(
                testProjectDir.getRoot(),
                'build/classes/groovy/main/example/$FooDefinition.class'
        ).exists()
    }

    def "test add openapi processing - groovy"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "groovy"
                id "io.micronaut.library"
            }
            
            micronaut {
                version "2.0.0.RC2"
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                compileOnly "io.micronaut.configuration:micronaut-openapi"
                compileOnly "io.swagger.core.v3:swagger-annotations"
            }
            
        """
        testProjectDir.newFolder("src", "main", "groovy", "example")
        def javaFile = testProjectDir.newFile("src/main/groovy/example/Foo.groovy")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example

import io.swagger.v3.oas.annotations.*
import io.swagger.v3.oas.annotations.info.*


@javax.inject.Singleton
@OpenAPIDefinition(
    info = @Info(
            title = "demo",
            version = "0.0"
    )
)
class Foo {}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('assemble')
                .withPluginClasspath()
                .build()

        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        new File(
                testProjectDir.getRoot(),
                'build/classes/groovy/main/example/$FooDefinition.class'
        ).exists()
        result.output.contains("Generating OpenAPI Documentation")
    }
}
