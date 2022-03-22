package io.micronaut.gradle.shadow

import io.micronaut.gradle.fixtures.AbstractFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue

class ShadowJarSpec extends AbstractFunctionalTest {

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/406")
    def "merges service files when building shadow jar"() {
        def shadowJar = file("build/libs/hello-world-1.0-all.jar")

        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "com.github.johnrengelman.shadow" version "7.1.0"
            }

            version = "1.0"
            
            micronaut {
                version "3.4.0"
                runtime "none"
                processing {
                    annotations("example.*")
                }
            }
            
            $repositoriesBlock
            mainClassName="example.Application"
            
            dependencies {
                annotationProcessor("info.picocli:picocli-codegen")
                implementation("info.picocli:picocli")
                implementation("io.micronaut.picocli:micronaut-picocli")
            }

            tasks.register("shadowRun") {
                def shadowJar = tasks.named("shadowJar")
                inputs.file(shadowJar.flatMap { it.archiveFile })
                doLast {
                    def exec = services.get(ExecOperations)
                    exec.javaexec {
                        it.classpath = files(shadowJar.flatMap { it.archiveFile }.get())
                        it.main = "example.Application"
                    }
                }
            }
        """
        testProjectDir.newFolder("src", "main", "java", "example")

        file("src/main/java/example/Application.java") << """package example;

import io.micronaut.configuration.picocli.PicocliRunner;
import picocli.CommandLine.Command;

@Command(name = "demo", description = "...", mixinStandardHelpOptions = true)
public class Application implements Runnable {

    public static void main(String[] args) {
        PicocliRunner.run(Application.class, args);
    }

    public void run() {
        System.out.println("Hello, all!");
    }
}"""

        when:
        def result = build('shadowRun')

        then:
        result.task(":shadowJar").outcome == TaskOutcome.SUCCESS
        result.task(":shadowRun").outcome == TaskOutcome.SUCCESS
        shadowJar.exists()
        result.output.contains("Hello, all!")
    }
}
