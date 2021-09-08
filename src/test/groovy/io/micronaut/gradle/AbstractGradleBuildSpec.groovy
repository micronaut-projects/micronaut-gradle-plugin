package io.micronaut.gradle

import io.micronaut.gradle.graalvm.GraalUtil
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.util.environment.Jvm

abstract class AbstractGradleBuildSpec extends Specification {
    static boolean isGraalVmAvailable() {
        return GraalUtil.isGraalJVM() || System.getenv("GRAALVM_HOME")
    }

    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile
    File kotlinBuildFile

    // This can be used during development to add statements like includeBuild
    final List<String> postSettingsStatements = [
    ]

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
        kotlinBuildFile = testProjectDir.newFile('build.gradle.kts')
    }

    def getRepositoriesBlock(String dsl = 'groovy') {
        """repositories {
    mavenCentral()
}"""
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
        def runner = GradleRunner.create()
        if (Jvm.current.java16Compatible) {
            if (Jvm.current.java16Compatible) {
                runner = runner.withJvmArguments(
                        '--illegal-access=permit',
                        '--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED'
                )
            }
        }
        runner.withProjectDir(testProjectDir.root)
                .withArguments(["--no-watch-fs",
                                "-Porg.gradle.java.installations.auto-download=false",
                                "-Porg.gradle.java.installations.auto-detect=false",
                                "-Porg.gradle.java.installations.fromEnv=GRAALVM_HOME",
                                *args])
                .forwardStdOutput(System.out.newWriter())
                .forwardStdError(System.err.newWriter())
                .withPluginClasspath()
                .build()
    }

}
