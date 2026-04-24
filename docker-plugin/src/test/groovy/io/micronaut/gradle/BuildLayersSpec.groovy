package io.micronaut.gradle

import groovy.io.FileType
import org.gradle.testkit.runner.TaskOutcome

import java.nio.file.Files

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
        def sourceJar = cachedDependency("ch.qos.logback", "logback-classic", copiedJar.name)
        def sourceMtime = Files.getLastModifiedTime(sourceJar.toPath()).toMillis()
        def firstCopiedMtime = Files.getLastModifiedTime(copiedJar.toPath()).toMillis()

        sleep(1100)
        def secondBuild = build('buildLayers', '--rerun-tasks')
        def secondCopiedMtime = Files.getLastModifiedTime(copiedJar.toPath()).toMillis()

        then:
        firstBuild.task(":buildLayers").outcome == TaskOutcome.SUCCESS
        secondBuild.task(":buildLayers").outcome == TaskOutcome.SUCCESS
        firstCopiedMtime == sourceMtime
        secondCopiedMtime == sourceMtime
        secondCopiedMtime == firstCopiedMtime
    }

    private static File cachedDependency(String group, String module, String fileName) {
        def cacheRoots = [
            System.getenv("GRADLE_USER_HOME"),
            new File(System.getProperty("java.io.tmpdir"), ".gradle-test-kit").absolutePath,
            new File(System.getProperty("user.home"), ".gradle").absolutePath
        ].findAll { it != null }
        for (def cacheRoot : cacheRoots) {
            def cacheDir = new File(cacheRoot, "caches/modules-2/files-2.1/${group}/${module}")
            if (!cacheDir.exists()) {
                continue
            }
            File dependencyJar
            cacheDir.eachFileRecurse(FileType.FILES) { file ->
                if (file.name == fileName) {
                    dependencyJar = file
                }
            }
            if (dependencyJar != null) {
                return dependencyJar
            }
        }
        assert false: "Unable to locate ${fileName} for ${group}:${module} under ${cacheRoots}"
    }
}
