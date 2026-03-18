package io.micronaut.gradle.kotlin

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Shared

class KotlinLibraryFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    @Shared
    private final String kotlinVersion = System.getProperty("kotlinVersion")
    @Shared
    private final String kotlin2Version = System.getProperty("kotlin2Version")

    @Shared
    private final String kspVersion = System.getProperty("kspVersion")
    @Shared
    private final String ksp2Version = System.getProperty("ksp2Version")


    def "test apply defaults for micronaut-library and KSP with kotlin DSL for #plugin (Kotlin #kotlin, KSP #ksp)"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile.delete()
        kotlinBuildFile << """
            plugins {
                id("org.jetbrains.kotlin.jvm") version("$kotlin")
                id("com.google.devtools.ksp") version "$ksp"
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
                ?.find { it.name.endsWith(".class") && it.name.contains('$Definition') }

        where:
        plugin            | kotlin         | ksp
        'library'         | kotlin2Version | ksp2Version
        'minimal.library' | kotlin2Version | ksp2Version
    }

    def "test apply defaults for micronaut-library and kotlin with kotlin DSL for #plugin (Kotlin #kotlin)"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile.delete()
        kotlinBuildFile << """
            plugins {
                id("org.jetbrains.kotlin.jvm") version("$kotlin")
                id("org.jetbrains.kotlin.kapt") version("$kotlin")
                id("org.jetbrains.kotlin.plugin.allopen") version("$kotlin")
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
        testProjectDir.root.toPath()
                .resolve('build/tmp/kapt3/classes/main/example/$Foo$Definition.class').toFile().exists()

        where:
        plugin            | kotlin
        'library'         | kotlin2Version
        'minimal.library' | kotlin2Version
    }

    def "test custom sourceSet for micronaut-library and kotlin with kotlin DSL for #plugin (Kotlin #kotlin)"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile.delete()
        kotlinBuildFile << """
            plugins {
                id("org.jetbrains.kotlin.jvm") version("$kotlin")
                id("org.jetbrains.kotlin.kapt") version("$kotlin")
                id("org.jetbrains.kotlin.plugin.allopen") version("$kotlin")
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
        testProjectDir.root.toPath()
                .resolve('build/tmp/kapt3/classes/custom/example/$Foo$Definition.class').toFile().exists()

        where:
        plugin            | kotlin
        'library'         | kotlin2Version
        'minimal.library' | kotlin2Version
    }
}
