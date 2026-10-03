package io.micronaut.gradle

import org.gradle.api.logging.Logging
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class RunCdsArchiveSpec extends Specification {

    @TempDir
    Path tempDir

    def "the duplicate check skips merged and metadata paths"() {
        expect:
        RunCdsArchive.isMergedOrMetadata(path) == skipped

        where:
        path                                                    | skipped
        'META-INF/MANIFEST.MF'                                  | true
        'META-INF/services/io.micronaut.inject.BeanDefinitionReference' | true
        'META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/example.$Foo$Definition$Reference' | true
        'module-info.class'                                     | true
        'META-INF/versions/9/module-info.class'                 | true
        'META-INF/versions/11/com/example/Foo.class'            | true
        'META-INF/SIGNER.SF'                                    | true
        'META-INF/SIGNER.RSA'                                   | true
        'META-INF/maven/com.example/dep/pom.properties'         | true
        'META-INF/LICENSE'                                      | true
        'META-INF/LICENSE.txt'                                  | true
        'META-INF/license/LICENSE.netty.txt'                    | true
        'META-INF/NOTICE.md'                                    | true
        'LICENSE'                                               | true
        'logback.xml'                                           | false
        'application.yml'                                       | false
        'com/example/Foo.class'                                 | false
        'META-INF/native-image/com.example/app/reflect-config.json' | false
        'com/example/LICENSE.txt'                               | false
    }

    def "finds the paths of the changing entries that a dependency JAR also has"() {
        given:
        def dependency = jar('dep.jar', ['com/example/Foo.class', 'logback.xml', 'META-INF/MANIFEST.MF', 'META-INF/services/a.B'])
        def classes = Files.createDirectories(tempDir.resolve('classes'))
        Files.createDirectories(classes.resolve('com/example'))
        Files.writeString(classes.resolve('com/example/Foo.class'), '')
        Files.writeString(classes.resolve('com/example/Bar.class'), '')
        Files.createDirectories(classes.resolve('META-INF/services'))
        Files.writeString(classes.resolve('META-INF/services/a.B'), '')
        def sibling = jar('sibling.jar', ['logback.xml', 'META-INF/MANIFEST.MF', 'sibling/Baz.class'])
        def archive = archive([dependency.toFile()])

        expect:
        archive.findDuplicates([classes.toFile(), sibling.toFile(), tempDir.resolve('missing').toFile()]) == ['com/example/Foo.class', 'logback.xml']
        Files.readAllLines(tempDir.resolve('cds').resolve(archiveDirectoryName(archive)).resolve(RunCdsArchive.INDEX_FILE)) == ['com/example/Foo.class', 'logback.xml']
    }

    def "a recording that ends in the middle of a line loses that line"() {
        given:
        def recording = tempDir.resolve('list.recording')
        def classList = tempDir.resolve('list.txt')
        Files.writeString(recording, content)

        expect:
        RunCdsArchive.completeRecording(recording, classList) == complete
        !complete || Files.readString(classList) == expected
        !complete || !Files.exists(recording)

        where:
        content                                   | complete | expected
        'java/lang/Object id: 0\njava/lang/Str'   | true     | 'java/lang/Object id: 0\n'
        'java/lang/Object id: 0\n'                | true     | 'java/lang/Object id: 0\n'
        'java/lang/Obj'                           | false    | null
        ''                                        | false    | null
    }

    def "quotes the arguments of an argument file"() {
        expect:
        RunCdsArchive.quote(argument) == quoted

        where:
        argument                         | quoted
        '-Xshare:dump'                   | '-Xshare:dump'
        '-cp'                            | '-cp'
        '/a b/c.jar:/d.jar'              | '"/a b/c.jar:/d.jar"'
        'C:\\Users\\me\\a.jar'           | '"C:\\\\Users\\\\me\\\\a.jar"'
        ''                               | '""'
    }

    private RunCdsArchive archive(List<File> jars) {
        def javaHome = Path.of(System.getProperty('java.home'))
        new RunCdsArchive(tempDir.resolve('cds'), jars, javaHome.resolve('bin/java'), javaHome, Runtime.version().toString(), [:],
                Logging.getLogger(RunCdsArchiveSpec))
    }

    private String archiveDirectoryName(RunCdsArchive archive) {
        Files.list(tempDir.resolve('cds')).withCloseable { it.findFirst().get().fileName.toString() }
    }

    private Path jar(String name, List<String> entries) {
        def jar = tempDir.resolve(name)
        new JarOutputStream(Files.newOutputStream(jar)).withCloseable { out ->
            entries.each {
                out.putNextEntry(new JarEntry(it))
                out.closeEntry()
            }
        }
        jar
    }
}
