package io.micronaut.gradle

import io.micronaut.gradle.graalvm.GraalUtil
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
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

    boolean containsDependency(String mavenCoordinate, String configuration) {
        BuildResult result = build('dependencies', "--configuration", configuration)
        BuildTask task = result.task(":dependencies")
        assert task.outcome == TaskOutcome.SUCCESS
        result.output.contains(mavenCoordinate)
    }

    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()
    protected Path baseDir

    File getSettingsFile() {
        baseDir.resolve("settings.gradle").toFile()
    }

    File getBuildFile() {
        baseDir.resolve("build.gradle").toFile()
    }

    File getKotlinBuildFile() {
        baseDir.resolve("build.gradle.kts").toFile()
    }

    // This can be used during development to add statements like includeBuild
    final List<String> postSettingsStatements = [
    ]

    def setup() {
        baseDir = testProjectDir.root.toPath()
    }

    void withSpacesInTestDir() {
        baseDir = testProjectDir.newFolder("with spaces").toPath()
    }

    protected void withSample(String name) {
        File sampleDir = new File("../samples/$name").canonicalFile
        copySample(sampleDir.toPath(), baseDir)

        def jacocoConf = AbstractGradleBuildSpec.classLoader.getResourceAsStream("testkit-gradle.properties")?.text
        if (jacocoConf) {
            println "Configuring Code Coverage support: ${jacocoConf}"
            file("gradle.properties") << jacocoConf
        }
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
        baseDir.resolve(relativePath).toFile()
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
        configureRunner(args)
                .build()
    }

    BuildResult fails(String... args) {
        configureRunner(args)
            .buildAndFail()
    }

    GradleRunner configureRunner(String... args) {
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
        runner.withProjectDir(baseDir.toFile())
                .withArguments(["--no-watch-fs",
                                "-S",
                                "-Porg.gradle.java.installations.auto-download=false",
                                "-Porg.gradle.java.installations.auto-detect=false",
                                "-Porg.gradle.java.installations.fromEnv=GRAALVM_HOME",
                                "-Dio.micronaut.graalvm.rich.output=false",
                                *args])
                .forwardStdOutput(System.out.newWriter())
                .forwardStdError(System.err.newWriter())
                .withDebug(true)
    }

    static String normalizeLineEndings(String s) {
        s.replaceAll("\\r\\n?", "\n")
    }

    static String argFileContentsOf(BuildResult result) {
        result.output.lines().filter {
            it.contains('Starting process') && it.contains('bin/native-image')
        }.map {
            int workingDirIdx = it.indexOf('Working directory: ')
            int commandIdx = it.indexOf('Command: ')
            String workingDirectory = it.substring(workingDirIdx + 'Working directory: '.length(), commandIdx).trim()
            new File(new File(workingDirectory), it.substring(it.lastIndexOf('@') + 1))
        }.findFirst()
                .map { it.text }
                .orElse("")
    }
}
