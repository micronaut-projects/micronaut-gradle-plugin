package io.micronaut.gradle.kotlin

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Shared

class KotlinLibraryFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    @Shared
    private final String kotlinVersion = System.getProperty("kotlinVersion");

    @Shared
    private final String kspVersion = System.getProperty("kspVersion");


    def "test apply defaults for micronaut-library and KSP with kotlin DSL for #plugin"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile.delete()
        kotlinBuildFile << """
            plugins {
                id("org.jetbrains.kotlin.jvm") version("$kotlinVersion")
                id("com.google.devtools.ksp") version "$kspVersion"
                id("io.micronaut.$plugin")
            }
            
            micronaut {
                version("$micronautVersion")
                processing {
                    incremental(true)
                }
            }
            
            ${getRepositoriesBlock('kotlin')}
            
        """
        testProjectDir.newFolder("src", "main", "kotlin", "example")
        def javaFile = testProjectDir.newFile("src/main/kotlin/example/Foo.kt")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example

@jakarta.inject.Singleton
class Foo {}
"""

        when:
        def result = build('assemble')

        println result.output
        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        new File(testProjectDir.root, "build/generated/ksp/main/classes/example")
                .listFiles()
                ?.find { it.name.endsWith(".class") && it.name.contains('$Definition')}

        where:
        plugin << ['library', 'minimal.library']
    }

    def "test apply defaults for micronaut-library and kotlin with kotlin DSL for #plugin"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile.delete()
        kotlinBuildFile << """
            plugins {
                id("org.jetbrains.kotlin.jvm") version("$kotlinVersion")
                id("org.jetbrains.kotlin.kapt") version("$kotlinVersion")
                id("org.jetbrains.kotlin.plugin.allopen") version("$kotlinVersion")
                id("io.micronaut.$plugin")
            }
            
            micronaut {
                version("$micronautVersion")
                processing {
                    incremental(true)
                }
            }   
            
            ${getRepositoriesBlock('kotlin')}
            
        """
        testProjectDir.newFolder("src", "main", "kotlin", "example")
        def javaFile = testProjectDir.newFile("src/main/kotlin/example/Foo.kt")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example

@jakarta.inject.Singleton
class Foo {}
"""

        when:
        def result = build('assemble')

        println result.output
        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")

        where:
        plugin << ['library', 'minimal.library']
    }

    def "test custom sourceSet for micronaut-library and kotlin with kotlin DSL for #plugin"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile.delete()
        kotlinBuildFile << """
            plugins {
                id("org.jetbrains.kotlin.jvm") version("$kotlinVersion")
                id("org.jetbrains.kotlin.kapt") version("$kotlinVersion")
                id("org.jetbrains.kotlin.plugin.allopen") version("$kotlinVersion")
                id("io.micronaut.$plugin")
            }
            
            val custom by sourceSets.creating
            
            dependencies {
                "customImplementation"(project)
            }
                        
            micronaut {
                version("$micronautVersion")
                processing {
                    incremental(true)
                    sourceSets(
                        sourceSets["custom"]        
                    )                    
                }
            }            
            
            ${getRepositoriesBlock('kotlin')}
            
        """
        testProjectDir.newFolder("src", "custom", "kotlin", "example")
        def javaFile = testProjectDir.newFile("src/custom/kotlin/example/Foo.kt")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example

@jakarta.inject.Singleton
class Foo {}
"""

        when:
        def result = build('compileCustomKotlin')

        println result.output
        then:
        result.task(":compileCustomKotlin").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")

        where:
        plugin << ['library', 'minimal.library']
    }
}
