package io.micronaut.gradle

import io.micronaut.gradle.graalvm.GraalUtil
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Specification

@Requires({ GraalUtil.isGraalJVM() })
@IgnoreIf({ os.isWindows() })
class NativeImageMultiProjectSpec extends Specification {
    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
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

            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation("io.micronaut:micronaut-core:2.4.2")
            }
 
        """
        def twoFolder = testProjectDir.newFolder("two")
        def twoBuildFile = testProjectDir.newFile('two/build.gradle')
        twoBuildFile << """
            plugins {
                id("com.github.johnrengelman.shadow") version "6.1.0"
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.4.2"
            }
            
            repositories {
                mavenCentral()
            }
            
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
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('two:nativeImage', '-i', '--stacktrace')
                .withPluginClasspath()
                .build()

        def task = result.task(":two:nativeImage")
        println result.output
        then:
        result.output.contains("Native Image written to")
        task.outcome == TaskOutcome.SUCCESS
        new File("$testProjectDir.root/two/build/native-image/application").canExecute()



        when:
        def process = "$testProjectDir.root/two/build/native-image/application".execute()
        def exitCode = process.waitFor()

        def output = process.text

        then:
        output.contains("Good!")
        exitCode == 0
    }
}
