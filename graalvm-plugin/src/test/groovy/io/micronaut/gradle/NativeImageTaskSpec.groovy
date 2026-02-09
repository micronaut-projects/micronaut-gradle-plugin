package io.micronaut.gradle


import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Requires

@Requires({ AbstractGradleBuildSpec.graalVmAvailable })
class NativeImageTaskSpec extends AbstractGradleBuildSpec {

    @IgnoreIf({ os.isWindows() })
    def "test build native image"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
            }
            
            $repositoriesBlock
            
            
            application { mainClass = "example.Application" }
            
        """
        withNativeImageDryRun()

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

        when:
        def result = build('nativeCompile')

        def task = result.task(":nativeCompile")
        then:
        result.output.contains("Native Image written to")
        task.outcome == TaskOutcome.SUCCESS
    }

    @IgnoreIf({ os.isWindows() })
    def "test build native image - custom name"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
            }
            
            $repositoriesBlock
                        
            application { mainClass = "example.Application" }

            graalvmNative {
                binaries {
                    main {
                        imageName = "basic-app"
                        buildArgs('-Dfoo=bar')
                        useArgFile = true
                    }
                }
            }            
        """
        withNativeImageDryRun()
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

        when:
        def result = build('nativeCompile', '-i', '--stacktrace')

        def task = result.task(":nativeCompile")
        then:
        result.output.contains("Native Image written to")
        argFileContentsOf(result).contains('-Dfoo=bar')
        task.outcome == TaskOutcome.SUCCESS
    }
}
