package io.micronaut.gradle.crac

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.intellij.lang.annotations.Language
import spock.lang.IgnoreIf

@IgnoreIf({ os.windows })
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

    String getPluginsBlock() {
        """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.crac"
            }""".stripIndent()
    }

    String getRepositoriesBlock(boolean allowSnapshots = true) {
        """
            repositories {
                mavenCentral()
                ${allowSnapshots ? 'maven { url = "https://s01.oss.sonatype.org/content/repositories/snapshots" }' : ""}
            }""".stripIndent()
    }

    String getDependenciesBlock(String cracVersion = '1.0.0-SNAPSHOT') {
        """
            dependencies {
                implementation("io.micronaut.crac:micronaut-crac:$cracVersion")
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

    String getBuildFileBlock(String micronautConfig = getMicronautConfigBlock()) {
        """$pluginsBlock
          |$repositoriesBlock
          |$dependenciesBlock
          |$micronautConfig
          |mainClassName="example.Application"
          |""".stripMargin()
    }
}
