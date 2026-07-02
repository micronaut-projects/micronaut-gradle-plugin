package io.micronaut.gradle

import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files // Still needed for Application.java creation

class GenerateImportFactoryFunctionalTest extends AbstractGradleBuildSpec {

    void 'test generateImportFactories for real external dependencies'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"

        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
            }
            
            $repositoriesBlock
            
            dependencies {
                implementation("io.micronaut:micronaut-inject")
                implementation("jakarta.annotation:jakarta.annotation-api")
                
                // Using real, common external dependencies
                implementation("org.slf4j:slf4j-api:2.0.16")
                implementation("ch.qos.logback:logback-classic:1.5.16")
                
                testImplementation("io.micronaut:micronaut-http-client")
            }
            mainClassName="example.Application"

            tasks.named("generateImportFactories", io.micronaut.gradle.GenerateImportFactoryTask) {
                includeDependenciesFilter = '.*\\\\.jar\$' 
                excludeDependenciesFilter = '^\\\$' // Matches empty string for no exclusions
                includePackagesFilter = '.*' // Include all packages
                excludePackagesFilter = '^\\\$' // Matches empty string for no exclusions
            }
        """

        // Create a dummy Application.java for the minimal application plugin
        new File(testProjectDir.root, 'src/main/java/example').mkdirs()
        new File(testProjectDir.root, 'src/main/java/example/Application.java') << '''
            package example;
            import io.micronaut.runtime.Micronaut;
            import jakarta.inject.Singleton;
            
            public class Application {
                public static void main(String[] args) {
                    Micronaut.run(Application.class, args);
                }
            }
        '''

        when:
        def result = build('classes', '--no-build-cache', '--no-configuration-cache')

        def generateTask = result.task(":generateImportFactories")

        then:
        generateTask.outcome == TaskOutcome.SUCCESS

        // Verify that the generated sources directory exists
        def generatedSourcesDir = new File(testProjectDir.root, "build/generated-sources/importfactory")
        generatedSourcesDir.exists()

        // Verify that factory files were generated for packages from real dependencies
        // Expected packages from slf4j-api and logback-classic
        def slf4jFactory = new File(generatedSourcesDir, "org/slf4j/ImportFactory.java")
        def logbackClassicFactory = new File(generatedSourcesDir, "ch/qos/logback/classic/ImportFactory.java")
        def logbackCoreFactory = new File(generatedSourcesDir, "ch/qos/logback/core/ImportFactory.java")


        slf4jFactory.exists()
        logbackClassicFactory.exists()
        logbackCoreFactory.exists() // logback-classic depends on logback-core
    }
}
