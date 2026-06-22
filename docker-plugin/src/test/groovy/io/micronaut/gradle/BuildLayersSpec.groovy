package io.micronaut.gradle

import org.gradle.testkit.runner.TaskOutcome

import java.nio.file.Files
import java.nio.file.attribute.FileTime

class BuildLayersSpec extends AbstractGradleBuildSpec {

    void 'test build layers with custom build directory'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
            }
            
            $repositoriesBlock
            
            dependencies {
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }
            application { mainClass = "example.Application" }
            
            project.buildDir = "build-custom"
        """

        when:
        def result = build('buildLayers')

        def task = result.task(":buildLayers")

        then:
        task.outcome == TaskOutcome.SUCCESS
        new File(testProjectDir.root, "build-custom/docker/main/layers").exists()
        !new File(testProjectDir.root, "build/docker/main/layers").exists()
    }

    void 'test build layers with duplicates strategy'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
            }
            
            $repositoriesBlock
            
            dependencies {
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }
            application { mainClass = "example.Application" }

            def jar1 = tasks.register("otherJar", Jar) {
                destinationDirectory = file("build/libs1")
                archiveBaseName = 'toto'
                from sourceSets.main.output
            }
            def jar2 = tasks.register("otherJar2", Jar) {
                destinationDirectory = file("build/libs2")
                archiveBaseName = 'toto'
                from sourceSets.main.output
            }

            configurations.runtimeOnly.dependencies.add(dependencies.create(files(jar1, jar2)))            

            tasks.withType(io.micronaut.gradle.docker.tasks.BuildLayersTask) {
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }

        """

        when:
        def result = build('buildLayers')

        def task = result.task(":buildLayers")

        then:
        task.outcome == TaskOutcome.SUCCESS
        new File(testProjectDir.root, "build/docker/main/layers").exists()
    }

    void 'test build layers preserves duplicate winner mtime with exclude strategy'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
            }

            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
            }

            $repositoriesBlock

            dependencies {
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }
            application { mainClass = "example.Application" }

            def jar1 = tasks.register("otherJar", Jar) {
                destinationDirectory = file("build/libs1")
                archiveBaseName = 'toto'
                from sourceSets.main.output
                doLast {
                    archiveFile.get().asFile.setLastModified(1000L)
                }
            }
            def jar2 = tasks.register("otherJar2", Jar) {
                destinationDirectory = file("build/libs2")
                archiveBaseName = 'toto'
                from sourceSets.main.output
                doLast {
                    archiveFile.get().asFile.setLastModified(2000L)
                }
            }

            configurations.runtimeOnly.dependencies.add(dependencies.create(files(jar1, jar2)))

            tasks.withType(io.micronaut.gradle.docker.tasks.BuildLayersTask) {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
        """

        when:
        def result = build('buildLayers')

        then:
        result.task(":buildLayers").outcome == TaskOutcome.SUCCESS
        Files.getLastModifiedTime(new File(testProjectDir.root, "build/docker/main/layers/libs/toto.jar").toPath()).toMillis() == 1000L
    }

    void 'test build layers preserves dependency mtimes across reruns'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
            }

            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
            }

            $repositoriesBlock

            dependencies {
                runtimeOnly("ch.qos.logback:logback-classic")
            }
            application { mainClass = "example.Application" }
        """

        when:
        def firstBuild = build('buildLayers')
        def copiedJar = new File(testProjectDir.root, "build/docker/main/layers/libs").listFiles().find {
            it.name.startsWith("logback-classic-") && it.name.endsWith(".jar")
        }
        assert copiedJar != null
        def firstCopiedMtime = Files.getLastModifiedTime(copiedJar.toPath()).toMillis()

        Files.setLastModifiedTime(copiedJar.toPath(), FileTime.fromMillis(1))
        def secondBuild = build('buildLayers', '--rerun-tasks')
        def secondCopiedMtime = Files.getLastModifiedTime(copiedJar.toPath()).toMillis()

        then:
        firstBuild.task(":buildLayers").outcome == TaskOutcome.SUCCESS
        secondBuild.task(":buildLayers").outcome == TaskOutcome.SUCCESS
        secondCopiedMtime == firstCopiedMtime
    }
}
