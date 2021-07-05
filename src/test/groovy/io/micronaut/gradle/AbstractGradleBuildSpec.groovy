package io.micronaut.gradle

import org.gradle.internal.jvm.Jvm
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
        List<String> allArgs = []
        if (Jvm.current.java16Compatible) {
            allArgs.addAll([
                    '--illegal-access=permit',
                    '--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED'
            ])
        }
        Collections.addAll(allArgs, args)
        GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments(allArgs)
                .withPluginClasspath()
                .build()
    }

}
