package io.micronaut.gradle.crac

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.intellij.lang.annotations.Language

abstract class BaseCracGradleBuildSpec extends AbstractGradleBuildSpec {

    File writeGroovyFile(String fileName, @Language("java") String content) {
        writeFile(fileName, content)
    }

    File writeJavaFile(String fileName, @Language("java") String content) {
        writeFile(fileName, content)
    }

    File writeXmlFile(String fileName, @Language("xml") String content) {
        writeFile(fileName, content)
    }

    File writeYamlFile(String fileName, @Language("yaml") String content) {
        writeFile(fileName, content)
    }

    File writeFile(String filename, String content) {
        String[] pathSegments = filename.split('/').dropRight(1)
        if (!new File(testProjectDir.root, pathSegments.join('/')).exists()) {
            testProjectDir.newFolder(pathSegments)
        }
        testProjectDir.newFile(filename).with {
            parentFile.mkdirs()
            it << content
            it
        }
    }

    String fileTextContents(String filename) {
        new File(testProjectDir.root, filename).text
    }

    String getPluginsBlock(boolean switchOrder = false) {
        switchOrder ? """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.crac"
            }""".stripIndent() : """
            plugins {
                id "io.micronaut.crac"
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
            }""".stripIndent()
    }

    String getRepositoriesBlock(boolean allowSnapshots = true) {
        """
            repositories {
                mavenCentral()
                ${allowSnapshots ? 'maven { url = "https://s01.oss.sonatype.org/content/repositories/snapshots" }' : ""}
            }""".stripIndent()
    }

    String getDependenciesBlock() {
        """
            dependencies {
                implementation("io.micronaut.crac:micronaut-crac")
            }""".stripIndent()
    }

    String getMicronautConfigBlock(String cracConfig = '') {
        """
            micronaut {
                version "3.6.1"
                runtime("netty")
                testRuntime("junit5")
                processing {
                    incremental(true)
                    annotations("example.*")
                }
${cracConfig.readLines().collect {"                ${it}"}.join("\n")}
            }""".stripIndent()
    }

    String getBuildFileBlockWithMicronautConfig(String micronautConfig) {
        getBuildFileBlock(false, micronautConfig)
    }

    String getBuildFileBlock(boolean switchPluginOrder = false, String micronautConfig = getMicronautConfigBlock()) {
        """${getPluginsBlock(switchPluginOrder)}
          |$repositoriesBlock
          |$dependenciesBlock
          |$micronautConfig
          |mainClassName="example.Application"
          |""".stripMargin()
    }
}
