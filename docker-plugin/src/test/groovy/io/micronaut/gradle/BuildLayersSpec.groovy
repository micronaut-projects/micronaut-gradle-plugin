package io.micronaut.gradle

import org.gradle.testkit.runner.TaskOutcome

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
        new File(testProjectDir.root, "build-custom/docker/main/layers/app/classes").exists()
        !new File(testProjectDir.root, "build-custom/docker/main/layers/app/application.jar").exists()
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

    void 'test native app layers keep application jar layout'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            import io.micronaut.gradle.docker.model.LayerKind
            import io.micronaut.gradle.docker.model.RuntimeKind
            import io.micronaut.gradle.docker.tasks.BuildLayersTask
            import org.gradle.api.tasks.bundling.Zip

            plugins {
                id "base"
                id "io.micronaut.docker"
            }

            def nativeLayer = objects.newInstance(io.micronaut.gradle.docker.model.Layer)
            def appArchive = tasks.register("appArchive", Zip) {
                archiveFileName = "runner.jar"
                destinationDirectory = layout.buildDirectory.dir("native-input")
                from(layout.projectDirectory.file("seed.txt"))
            }

            tasks.register("buildNativeLayersTask", BuildLayersTask) {
                dependsOn(appArchive)
                layers.set([nativeLayer])
                outputDir = layout.buildDirectory.dir("docker/native-main/layers")
            }

            nativeLayer.layerKind.set(LayerKind.APP)
            nativeLayer.runtimeKind.set(RuntimeKind.NATIVE)
            nativeLayer.files.from(appArchive.flatMap { it.archiveFile })
        """
        file("seed.txt").text = "native"

        when:
        def result = build('buildNativeLayersTask')

        then:
        def task = result.task(":buildNativeLayersTask")
        task.outcome == TaskOutcome.SUCCESS
        new File(testProjectDir.root, "build/docker/native-main/layers/app/application.jar").exists()
        !new File(testProjectDir.root, "build/docker/native-main/layers/app/classes").exists()
    }
}
