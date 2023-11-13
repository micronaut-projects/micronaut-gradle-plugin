package io.micronaut.gradle

import org.gradle.testkit.runner.TaskOutcome

class MicronautDeprecationsPluginSpec extends AbstractGradleBuildSpec {

    def "shows deprecation warning when using additionalSourceSets"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
            }

            sourceSets {
                custom {
                    java.srcDir('src/custom/java')
                }
            }            

            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
                processing {
                    additionalSourceSets.add(sourceSets.custom)
                }
            }
            
            $repositoriesBlock
            mainClassName="example.Application"

            $withSerde
        """
        when:
        def result = build('assemble')

        def task = result.task(":assemble")

        then:
        result.output.contains("Source set 'custom' was declared using the additionalSourcesets property. Please use sourceSet(...) instead.\n")
        task.outcome == TaskOutcome.SUCCESS
    }

    def "doesn't show deprecation warning when using sourceSet(...)"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
            }

            sourceSets {
                custom {
                    java.srcDir('src/custom/java')
                }
            }            

            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
                processing {
                    sourceSets(sourceSets.custom)
                }
            }
            
            $repositoriesBlock
            mainClassName="example.Application"

            $withSerde
        """
        when:
        def result = build('assemble')

        def task = result.task(":assemble")

        then:
        !result.output.contains("Source set 'custom' was declared using the additionalSourcesets property. Please use sourceSet(...) instead.\n")
        task.outcome == TaskOutcome.SUCCESS
    }
}
