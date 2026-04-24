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

    void 'test build layers excludes configured resources'() {
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

            tasks.withType(io.micronaut.gradle.docker.tasks.BuildLayersTask).configureEach {
                excludes.add("**/application-dev.properties")
            }
        """
        file("src/main/resources").mkdirs()
        file("src/main/resources/application.properties") << "micronaut.application.name=test-app"
        file("src/main/resources/application-dev.properties") << "micronaut.server.port=8181"

        when:
        def result = build('buildLayers')

        def task = result.task(":buildLayers")

        then:
        task.outcome == TaskOutcome.SUCCESS
        new File(testProjectDir.root, "build/docker/main/layers/resources/application.properties").exists()
        !new File(testProjectDir.root, "build/docker/main/layers/resources/application-dev.properties").exists()
    }
}
