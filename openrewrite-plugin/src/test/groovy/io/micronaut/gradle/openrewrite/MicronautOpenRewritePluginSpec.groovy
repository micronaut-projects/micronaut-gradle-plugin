package io.micronaut.gradle.openrewrite

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.TempDir
import spock.lang.Specification
import org.gradle.testkit.runner.GradleRunner


class MicronautOpenRewritePluginSpec extends Specification {

    @TempDir
    File testProjectDir

    File buildFile
    File settingsFile

    def setup() {
        buildFile = new File(testProjectDir, "build.gradle")
        settingsFile = new File(testProjectDir, "settings.gradle")
    }

    def "applies micronaut-openrewrite plugin and runs dry run"() {
        given:
        settingsFile << "rootProject.name = 'rewrite-test'"
        buildFile << """
            plugins {
                id 'io.micronaut.minimal.application'
                id 'io.micronaut.openrewrite'
            }

            micronaut {
                version "4.8.2"
            }

            rewrite {
                activeRecipe("org.openrewrite.java.format.AutoFormat")
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation("io.micronaut:micronaut-runtime")
            }
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("rewriteDryRun")
                .withPluginClasspath()
                .build()

        then:
        result.task(":rewriteDryRun").outcome == TaskOutcome.SUCCESS
    }
}
