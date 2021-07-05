package io.micronaut.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

abstract class AbstractGradleBuildSpec extends Specification {
    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile
    File kotlinBuildFile

    // This can be used during development to add statements like includeBuild
    final List<String> postSettingsStatements = []

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
        kotlinBuildFile = testProjectDir.newFile('build.gradle.kts')
    }

    private void prepareBuild() {
        if (postSettingsStatements) {
            postSettingsStatements.each {
                settingsFile << "\n$it\n"
            }
            postSettingsStatements.clear()
        }
    }

    BuildResult build(String... args) {
        prepareBuild()
        GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments(args)
                .withPluginClasspath()
                .build()
    }

}
