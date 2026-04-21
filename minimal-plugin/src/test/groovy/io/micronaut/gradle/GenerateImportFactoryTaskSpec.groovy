package io.micronaut.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue

import java.nio.file.Files

@Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/960")
class GenerateImportFactoryTaskSpec extends AbstractGradleBuildSpec {

    def "import factory generation is disabled by default"() {
        given:
        File repositoryDir = publishDependencyRepository()
        settingsFile << "rootProject.name = 'import-factory-disabled'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.library"
            }

            micronaut {
                version "$micronautVersion"
                testRuntime "junit5"
            }

            repositories {
                maven { url = uri("${repositoryDir.toURI()}") }
                mavenCentral()
            }

            dependencies {
                implementation "com.example.imports:sample-alpha:1.0"
            }
        """
        writeTest("""
package example;

import example.imported.alpha.AlphaService;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
class ImportFactoryDisabledTest {
    @Inject
    ApplicationContext applicationContext;

    @Test
    void importedBeansAreNotRegistered() {
        Assertions.assertFalse(applicationContext.containsBean(AlphaService.class));
    }
}
""")

        when:
        def result = build('test')

        then:
        result.task(":test").outcome == TaskOutcome.SUCCESS
        !file("build/generated-sources/importfactory").exists()
    }

    def "enabled import factory generation honors filters"() {
        given:
        File repositoryDir = publishDependencyRepository()
        settingsFile << "rootProject.name = 'import-factory-enabled'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.library"
            }

            micronaut {
                version "$micronautVersion"
                testRuntime "junit5"
                importFactory {
                    enabled.set(true)
                    includeDependenciesFilter.set("com\\\\.example\\\\.imports:sample-.*")
                    excludeDependenciesFilter.set("com\\\\.example\\\\.imports:sample-beta")
                    includePackagesFilter.set("example\\\\.(imported\\\\.alpha|shared).*")
                    excludePackagesFilter.set("example\\\\.shared\\\\.internal.*")
                    targetPackage.set("example.generated")
                }
            }

            repositories {
                maven { url = uri("${repositoryDir.toURI()}") }
                mavenCentral()
            }

            dependencies {
                implementation "com.example.imports:sample-alpha:1.0"
                implementation "com.example.imports:sample-beta:1.0"
            }
        """
        writeTest("""
package example;

import example.imported.alpha.AlphaService;
import example.imported.beta.BetaService;
import example.shared.SharedAlphaService;
import example.shared.internal.HiddenService;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
class ImportFactoryEnabledTest {
    @Inject
    ApplicationContext applicationContext;

    @Test
    void matchingBeansAreImported() {
        Assertions.assertTrue(applicationContext.containsBean(AlphaService.class));
        Assertions.assertTrue(applicationContext.containsBean(SharedAlphaService.class));
        Assertions.assertFalse(applicationContext.containsBean(BetaService.class));
        Assertions.assertFalse(applicationContext.containsBean(HiddenService.class));
    }
}
""")

        when:
        def result = build('test')
        File generatedFactory = file("build/generated-sources/importfactory/example/generated/ImportFactory.java")

        then:
        result.task(":generateImportFactories").outcome == TaskOutcome.SUCCESS
        result.task(":test").outcome == TaskOutcome.SUCCESS
        generatedFactory.exists()
        normalizeLineEndings(generatedFactory.text).contains('"example.imported.alpha"')
        normalizeLineEndings(generatedFactory.text).contains('"example.shared"')
        !normalizeLineEndings(generatedFactory.text).contains('"example.imported.beta"')
        !normalizeLineEndings(generatedFactory.text).contains('"example.shared.internal"')
    }

    def "target package unset generates per-package factories with deduplicated output"() {
        given:
        File repositoryDir = publishDependencyRepository()
        settingsFile << "rootProject.name = 'import-factory-per-package'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.library"
            }

            micronaut {
                version "$micronautVersion"
                importFactory {
                    enabled.set(true)
                    includeDependenciesFilter.set("com\\\\.example\\\\.imports:sample-.*")
                    includePackagesFilter.set("example\\\\.(imported\\\\.(alpha|beta)|shared).*")
                    excludePackagesFilter.set("example\\\\.shared\\\\.internal.*")
                }
            }

            repositories {
                maven { url = uri("${repositoryDir.toURI()}") }
                mavenCentral()
            }

            dependencies {
                implementation "com.example.imports:sample-alpha:1.0"
                implementation "com.example.imports:sample-beta:1.0"
            }
        """

        when:
        def result = build('compileJava')
        File generatedSources = file("build/generated-sources/importfactory")
        List<String> generatedFiles = Files.walk(generatedSources.toPath())
            .filter(path -> path.fileName.toString() == "ImportFactory.java")
            .collect { generatedSources.toPath().relativize(it).toString().replace(File.separatorChar, '/' as char) }
            .sort()

        then:
        result.task(":generateImportFactories").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
        generatedFiles == [
            "example/imported/alpha/ImportFactory.java",
            "example/imported/beta/ImportFactory.java",
            "example/shared/ImportFactory.java"
        ]
        normalizeLineEndings(file("build/generated-sources/importfactory/example/shared/ImportFactory.java").text).count('"example.shared"') == 1
    }

    def "enabled import factory generation succeeds with no matching dependencies"() {
        given:
        File repositoryDir = publishDependencyRepository()
        settingsFile << "rootProject.name = 'import-factory-no-matches'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.library"
            }

            micronaut {
                version "$micronautVersion"
                importFactory {
                    enabled.set(true)
                    includeDependenciesFilter.set("com\\\\.example\\\\.imports:missing")
                }
            }

            repositories {
                maven { url = uri("${repositoryDir.toURI()}") }
                mavenCentral()
            }

            dependencies {
                implementation "com.example.imports:sample-alpha:1.0"
            }
        """

        when:
        def result = build('compileJava')
        File generatedSources = file("build/generated-sources/importfactory")

        then:
        result.task(":generateImportFactories").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.NO_SOURCE
        (!generatedSources.exists()) || generatedSources.list().length == 0
    }

    private File publishDependencyRepository() {
        File repositoryProjectDir = file("dependency-repository")
        repositoryProjectDir.mkdirs()
        new File(repositoryProjectDir, "settings.gradle") << """
            rootProject.name = 'dependency-repository'
            include 'sample-alpha', 'sample-beta'
        """
        new File(repositoryProjectDir, "build.gradle") << """
            allprojects {
                group = 'com.example.imports'
                version = '1.0'

                repositories {
                    mavenCentral()
                }
            }

            subprojects {
                apply plugin: 'java-library'
                apply plugin: 'maven-publish'

                dependencies {
                    api 'jakarta.inject:jakarta.inject-api:2.0.1'
                }

                publishing {
                    publications {
                        maven(MavenPublication) {
                            from components.java
                        }
                    }
                    repositories {
                        maven {
                            name = 'test'
                            url = uri(rootProject.layout.buildDirectory.dir('repo').get().asFile)
                        }
                    }
                }
            }
        """
        writeDependencySource(repositoryProjectDir, "sample-alpha", "example/imported/alpha/AlphaService.java", """
package example.imported.alpha;

import jakarta.inject.Singleton;

@Singleton
public class AlphaService {
}
""")
        writeDependencySource(repositoryProjectDir, "sample-alpha", "example/shared/SharedAlphaService.java", """
package example.shared;

import jakarta.inject.Singleton;

@Singleton
public class SharedAlphaService {
}
""")
        writeDependencySource(repositoryProjectDir, "sample-alpha", "example/shared/internal/HiddenService.java", """
package example.shared.internal;

import jakarta.inject.Singleton;

@Singleton
public class HiddenService {
}
""")
        writeDependencySource(repositoryProjectDir, "sample-beta", "example/imported/beta/BetaService.java", """
package example.imported.beta;

import jakarta.inject.Singleton;

@Singleton
public class BetaService {
}
""")
        writeDependencySource(repositoryProjectDir, "sample-beta", "example/shared/SharedBetaService.java", """
package example.shared;

import jakarta.inject.Singleton;

@Singleton
public class SharedBetaService {
}
""")

        GradleRunner.create()
            .withProjectDir(repositoryProjectDir)
            .withArguments("--no-watch-fs", "-S", "publishAllPublicationsToTestRepository")
            .forwardStdOutput(System.out.newWriter())
            .forwardStdError(System.err.newWriter())
            .build()

        return new File(repositoryProjectDir, "build/repo")
    }

    private void writeDependencySource(File repositoryProjectDir, String projectName, String relativePath, String source) {
        File sourceFile = new File(repositoryProjectDir, "${projectName}/src/main/java/${relativePath}")
        sourceFile.parentFile.mkdirs()
        sourceFile.text = source
    }

    private void writeTest(String source) {
        File testFile = file("src/test/java/example/ImportFactoryTest.java")
        testFile.parentFile.mkdirs()
        testFile.text = source
    }
}
