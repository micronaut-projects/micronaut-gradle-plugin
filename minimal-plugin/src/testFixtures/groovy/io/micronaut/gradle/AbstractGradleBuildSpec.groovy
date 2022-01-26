package io.micronaut.gradle

import io.micronaut.gradle.graalvm.GraalUtil
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.util.environment.Jvm

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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

    protected void withSample(String name) {
        File sampleDir = new File("../samples/$name").canonicalFile
        copySample(sampleDir.toPath(), testProjectDir.root.toPath())
    }

    private static void copySample(Path from, Path into) {
        Files.walk(from).forEach(sourcePath -> {
            Path target = into.resolve(from.relativize(sourcePath))
            if (Files.isDirectory(sourcePath)) {
                Files.createDirectories(target)
            } else {
                Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING)
            }
        })
    }

    File file(String relativePath) {
        testProjectDir.root.toPath().resolve(relativePath).toFile()
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

    protected GradleRunner newRunner() {
        GradleRunner.create()
                .withPluginClasspath()
    }

    BuildResult build(String... args) {
        prepareBuild()
        def runner = newRunner()
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
                                "-S",
                                "-Porg.gradle.java.installations.auto-download=false",
                                "-Porg.gradle.java.installations.auto-detect=false",
                                "-Porg.gradle.java.installations.fromEnv=GRAALVM_HOME",
                                *args])
                .forwardStdOutput(System.out.newWriter())
                .forwardStdError(System.err.newWriter())
                .withDebug(true)
                .build()
    }

    static String normalizeLineEndings(String s) {
        s.replaceAll("\\r\\n?", "\n")
    }
}
