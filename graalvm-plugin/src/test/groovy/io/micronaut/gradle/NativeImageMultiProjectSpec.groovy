package io.micronaut.gradle


import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Requires

@Requires({ AbstractGradleBuildSpec.graalVmAvailable })
@IgnoreIf({ os.isWindows() })
@Requires({ jvm.isJava11() })
class NativeImageMultiProjectSpec extends AbstractGradleBuildSpec {

    def setup() {
        settingsFile << '''
rootProject.name="test-multi-project"

include "two"
include "one"

'''
        def oneFolder = testProjectDir.newFolder("one")
        def oneBuildFile = testProjectDir.newFile('one/build.gradle')
        oneBuildFile << """
            plugins {
                id "java-library"
            }

            $repositoriesBlock
            
            dependencies {
                implementation("io.micronaut:micronaut-core:3.5.1")
            }
 
        """
        def twoFolder = testProjectDir.newFolder("two")
        def twoBuildFile = testProjectDir.newFile('two/build.gradle')
        twoBuildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
            }
            
            micronaut {
                version "3.5.1"
            }
            
            $repositoriesBlock
            
            dependencies {
                implementation project(":one")
                implementation "io.micronaut:micronaut-runtime"
            }
            
            application {
                mainClass.set("example.Application")
            }
            
        """

        def introspectedFile = new File(oneFolder, "src/main/java/other/Foo.java")
        introspectedFile.parentFile.mkdirs()
        introspectedFile << '''
package other;

import io.micronaut.core.annotation.*;

@Introspected
public class Foo {
}
'''
        def javaFile = new File(twoFolder, "src/main/java/example/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import io.micronaut.runtime.Micronaut;
import io.micronaut.core.beans.*;

class Application {
    public static void main(String... args) {
        Micronaut.run(args);
        try {
            other.Foo foo = new other.Foo();
            System.out.println("Good! " + foo);
            System.exit(0);
        
        } catch(Throwable e) {
            System.out.println("Bad: " + e.getMessage());
            System.exit(1);                    
        }    
    }
}
"""
    }

    void 'test build native image in subproject'() {
        when:
        def result = build('javaToolchains', 'two:nativeCompile', '-i', '--stacktrace')

        def task = result.task(":two:nativeCompile")
        println result.output
        def binary = new File("$testProjectDir.root/two/build/native/nativeCompile/two")

        then:
        result.output.contains("Native Image written to")
        task.outcome == TaskOutcome.SUCCESS
        binary.canExecute()

        when:
        def process = binary.absolutePath.execute()
        def exitCode = process.waitFor()

        def output = process.text

        then:
        output.contains("Good!")
        exitCode == 0
    }
}
