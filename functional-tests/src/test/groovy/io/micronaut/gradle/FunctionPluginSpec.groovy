package io.micronaut.gradle

import io.micronaut.gradle.fixtures.AbstractFunctionalTest
import org.gradle.testkit.runner.TaskOutcome

class FunctionPluginSpec extends AbstractFunctionalTest {

    def "function plugin keeps lambda java builds off the application plugin path"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.function"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_java"
            }

            $repositoriesBlock

            tasks.register("verifyFunctionPlugin") {
                doLast {
                    assert !project.pluginManager.hasPlugin("application")
                    assert project.tasks.findByName("run") == null
                    assert project.tasks.findByName("dockerfileNative") != null
                    assert project.tasks.findByName("nativeCompile") != null
                }
            }
        """

        when:
        def result = build('verifyFunctionPlugin')

        then:
        result.task(':verifyFunctionPlugin').outcome == TaskOutcome.SUCCESS
    }

    def "function plugin applies application support for lambda provided"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.function"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            tasks.register("verifyFunctionPlugin") {
                doLast {
                    assert project.pluginManager.hasPlugin("application")
                    assert project.tasks.findByName("run") != null
                }
            }

            application {
                mainClass.set("com.example.Function")
            }
        """

        when:
        def result = build('verifyFunctionPlugin')

        then:
        result.task(':verifyFunctionPlugin').outcome == TaskOutcome.SUCCESS
    }

    def "function plugin accepts application block before lambda provided runtime"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.function"
            }

            application {
                mainClass.set("com.example.Function")
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            tasks.register("verifyFunctionPlugin") {
                doLast {
                    assert project.pluginManager.hasPlugin("application")
                    assert project.tasks.findByName("run") != null
                    assert project.extensions.getByType(org.gradle.api.plugins.JavaApplication).mainClass.get() == "com.example.Function"
                }
            }
        """

        when:
        def result = build('verifyFunctionPlugin')

        then:
        result.task(':verifyFunctionPlugin').outcome == TaskOutcome.SUCCESS
    }

    def "function plugin supports Kotlin DSL main class configuration for lambda provided"() {
        given:
        settingsFile << "rootProject.name = \"hello-world\""
        kotlinBuildFile << """
            plugins {
                id("io.micronaut.function")
            }

            micronaut {
                version("$micronautVersion")
                runtime("lambda_provided")
                function {
                    mainClass.set("com.example.Function")
                }
            }

            ${getRepositoriesBlock('kotlin')}

            tasks.register("verifyFunctionPlugin") {
                doLast {
                    check(project.pluginManager.hasPlugin("application"))
                    check(project.tasks.findByName("run") != null)
                    check(project.extensions.getByType(org.gradle.api.plugins.JavaApplication::class.java).mainClass.get() == "com.example.Function")
                }
            }
        """.stripIndent()

        when:
        def result = build('verifyFunctionPlugin')

        then:
        result.task(':verifyFunctionPlugin').outcome == TaskOutcome.SUCCESS
    }

    def "full function plugin applies docker and graalvm support"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.function"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            application {
                mainClass.set("com.example.Function")
            }
        """

        when:
        def result = build('tasks', '--all')

        then:
        result.output.contains('dockerfile')
        result.output.contains('dockerfileNative')
        result.output.contains('nativeCompile')
    }

    def "function plugin keeps an explicit runner jar main class for lambda provided"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.function"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            application {
                mainClass.set("com.example.BookLambdaRuntime")
            }
        """

        when:
        def result = build('runnerJar')
        def manifest = file('build/tmp/runnerJar/MANIFEST.MF').text

        then:
        result.task(':runnerJar').outcome == TaskOutcome.SUCCESS
        manifest.contains('Main-Class: com.example.BookLambdaRuntime')
        !manifest.contains('Main-Class: io.micronaut.function.aws.runtime.MicronautLambdaRuntime')
    }
}
