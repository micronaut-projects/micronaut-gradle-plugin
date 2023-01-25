package io.micronaut.gradle.kotlin

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf

class KotlinLibraryFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    def "test apply defaults for micronaut-library and KSP with kotlin DSL for #plugin"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile.delete()
        kotlinBuildFile << """
            plugins {
                id("org.jetbrains.kotlin.jvm") version("1.8.0")
                id("com.google.devtools.ksp") version "1.8.0-1.0.8"
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
                id("org.jetbrains.kotlin.jvm") version("1.8.0")
                id("org.jetbrains.kotlin.kapt") version("1.8.0")
                id("org.jetbrains.kotlin.plugin.allopen") version("1.8.0")
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
                id("org.jetbrains.kotlin.jvm") version("1.8.0")
                id("org.jetbrains.kotlin.kapt") version("1.8.0")
                id("org.jetbrains.kotlin.plugin.allopen") version("1.8.0")
                id("io.micronaut.$plugin")
            }
            
            sourceSets {
                val custom by creating {
                    compileClasspath += sourceSets["main"].output
                    runtimeClasspath += sourceSets["main"].output
                }
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
