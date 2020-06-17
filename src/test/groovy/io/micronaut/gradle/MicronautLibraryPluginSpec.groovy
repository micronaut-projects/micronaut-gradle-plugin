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

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def "test apply defaults for micronaut-library"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.micronaut-library"
            }
            
            micronaut {
                version "2.0.0.RC1"
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
    }
}
