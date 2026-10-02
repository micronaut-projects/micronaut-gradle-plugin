package io.micronaut.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Requires

import javax.tools.ToolProvider
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.GZIPInputStream

@Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/1387")
class RunClassDataSharingSpec extends AbstractGradleBuildSpec {

    private static final String DUMPING = "Dumping a "
    private static final String PROBING = "Probing the "
    private static final String RECORDING = "Recording the classes that this run loads"

    // An in-process build cannot store the configuration cache on JDK 25 without --add-opens
    private boolean daemon

    def "run is unchanged when the setting is off"() {
        given:
        withProject("")

        when:
        def defaultRun = build(':app:run', '--info')

        then:
        defaultRun.task(':app:run').outcome == TaskOutcome.SUCCESS
        launchClasspath(defaultRun).first().endsWith('app/build/classes/java/main')
        !runCommand(defaultRun).contains('SharedArchiveFile')
        !runCommand(defaultRun).contains('DumpLoadedClassList')
        !runCommand(defaultRun).contains('-Xlog:cds')
        !file('app/build/run-class-data-sharing').exists()

        when:
        file('app/build.gradle') << """
            micronaut.runClassDataSharing.enabled = false
        """
        def disabledRun = build(':app:run', '--info')

        then:
        runCommand(disabledRun) == runCommand(defaultRun)
        !file('app/build/run-class-data-sharing').exists()
    }

    @Requires({ olderJdk() != null })
    def "run is unchanged with a launcher older than JDK 25"() {
        given:
        withProject("""
            micronaut.runClassDataSharing.enabled = true
            tasks.named('run') {
                javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(${olderJdkVersion()}) }
            }
        """)

        when:
        def result = build(':app:run', '--info', "-Porg.gradle.java.installations.paths=${olderJdk()}")

        then:
        result.task(':app:run').outcome == TaskOutcome.SUCCESS
        result.output.contains("needs JDK 25 or later, and the run task's launcher is JDK ${olderJdkVersion()}: it starts without one.")
        launchClasspath(result).first().endsWith('app/build/classes/java/main')
        !runCommand(result).contains('SharedArchiveFile')
        !runCommand(result).contains('DumpLoadedClassList')
        !file('app/build/run-class-data-sharing').exists()
    }

    def "records a class list, then dumps and probes once, and keys the archive on the dependency JARs"() {
        given:
        withProject("""
            micronaut.runClassDataSharing.enabled = true
            tasks.named('run') {
                jvmArgs '-Xlog:class+load'
            }
        """)

        when: 'the first run records the class list'
        def first = build(':app:run', '--info')

        then:
        first.output.contains(RECORDING)
        runCommand(first).contains('-XX:DumpLoadedClassList=')
        !runCommand(first).contains('SharedArchiveFile')
        loadSource(first, 'dep.Dep').startsWith('file:')
        !first.output.contains(DUMPING)
        // the dependencies first, then the project's output and the sibling project in their order
        def classpath = launchClasspath(first)
        classpath.dropRight(3).every { it.endsWith('.jar') && !it.contains('/lib/build/') }
        classpath.dropRight(3).any { it.endsWith('/dep-1.0.jar') }
        classpath[-3].endsWith('/app/build/classes/java/main')
        classpath[-2].endsWith('/app/build/resources/main')
        classpath[-1].endsWith('/lib/build/libs/lib.jar')
        first.output.contains('app=v1 dep=dep-1.0 lib=lib-v1')

        when: 'the second run dumps once, probes once and launches with the archive'
        def second = build(':app:run', '--info')

        then:
        count(second.output, DUMPING) == 1
        count(second.output, PROBING) == 1
        runCommand(second).contains('-XX:SharedArchiveFile=')
        runCommand(second).contains('-Xlog:cds*=off,aot*=off')
        !runCommand(second).contains('DumpLoadedClassList')
        loadSource(second, 'dep.Dep') == 'shared objects file'
        loadSource(second, 'example.Application').startsWith('file:')
        loadSource(second, 'lib.Greeter').contains('lib.jar')
        !second.output.contains('[error][cds]')
        !second.output.contains('[warning][cds]')
        second.output.contains('app=v1 dep=dep-1.0 lib=lib-v1')
        def archives = archiveFiles()
        archives.size() == 1
        archives[0].fileName.toString() == 'archive-plain.jsa'

        when: 'the third run neither dumps nor probes'
        def third = build(':app:run', '--info')

        then:
        !third.output.contains(DUMPING)
        !third.output.contains(PROBING)
        loadSource(third, 'dep.Dep') == 'shared objects file'

        when: 'a project source and the sibling project change'
        file('app/src/main/java/example/Application.java').text = application('v2')
        file('lib/src/main/java/lib/Greeter.java').text = greeter('lib-v2')
        def edited = build(':app:run', '--info')

        then: 'the key stays, and the run sees the edits'
        !edited.output.contains(DUMPING)
        !edited.output.contains(PROBING)
        !edited.output.contains(RECORDING)
        loadSource(edited, 'dep.Dep') == 'shared objects file'
        edited.output.contains('app=v2 dep=dep-1.0 lib=lib-v2')

        when: 'the dependency version changes'
        file('app/build.gradle').text = file('app/build.gradle').text.replace("com.example:dep:1.0", "com.example:dep:2.0")
        def newVersion = build(':app:run', '--info')
        def afterNewVersion = build(':app:run', '--info')

        then: 'the next run records again and the one after it dumps'
        newVersion.output.contains(RECORDING)
        !newVersion.output.contains(DUMPING)
        newVersion.output.contains('dep=dep-2.0')
        count(afterNewVersion.output, DUMPING) == 1
        loadSource(afterNewVersion, 'dep.Dep') == 'shared objects file'
        // the archives of the previous dependencies are gone
        archiveFiles().size() == 1

        when: 'only the mtime of a dependency JAR changes'
        def jar = file('repo/com/example/dep/2.0/dep-2.0.jar').toPath()
        Files.setLastModifiedTime(jar, FileTime.fromMillis(Files.getLastModifiedTime(jar).toMillis() - 60_000))
        def touched = build(':app:run', '--info')
        def afterTouched = build(':app:run', '--info')

        then:
        touched.output.contains(RECORDING)
        count(afterTouched.output, DUMPING) == 1
        loadSource(afterTouched, 'dep.Dep') == 'shared objects file'
    }

    def "dumps with the module graph of the launch"() {
        given:
        withProject("""
            micronaut.runClassDataSharing.enabled = true
        """)
        build(':app:run')

        when:
        def dumped = build(':app:run', '--info')

        then: 'the run task passes -Dcom.sun.management.jmxremote, so the dump adds the JMX agent module and no system property'
        runCommand(dumped).contains('-Dcom.sun.management.jmxremote')
        def dumpArgs = argumentFile('dump-plain.args')
        dumpArgs.any { it.startsWith('--add-modules=') && it.contains('jdk.management.agent') }
        !dumpArgs.any { it.startsWith('-D') }
        def probeArgs = argumentFile('probe-plain.args')
        probeArgs.any { it.startsWith('--add-modules=') && it.contains('jdk.management.agent') }
        !probeArgs.any { it.startsWith('-D') }

        when: 'a launch logs CDS'
        file('app/build.gradle') << """
            tasks.named('run') { jvmArgs '-Xlog:cds' }
        """
        def logged = build(':app:run', '--info')

        then: 'the key is the same, the logging is not silenced, and the module graphs match'
        !logged.output.contains(DUMPING)
        !runCommand(logged).contains('-Xlog:cds*=off')
        logged.output.contains('[cds]')
        !logged.output.contains('Mismatched values for property jdk.module.addmods')
        !logged.output.contains('[error][cds]')
    }

    def "uses the default order without the archive when the project and a dependency have the same path"() {
        given:
        withProject("""
            micronaut.runClassDataSharing.enabled = true
            dependencies { implementation 'com.example:dup:1.0' }
        """)
        file('app/src/main/resources/logback.xml').text = 'project'
        file('app/src/main/java/dup/Shared.java').text = 'package dup; public class Shared { public static String who() { return "project"; } }'
        file('app/src/main/java/example/Application.java').text = """package example;
public class Application {
    public static void main(String... args) throws Exception {
        System.out.println("java.class.path=" + System.getProperty("java.class.path"));
        try (var in = Application.class.getClassLoader().getResourceAsStream("logback.xml")) {
            System.out.println("resource=" + new String(in.readAllBytes()) + " class=" + dup.Shared.who());
        }
    }
}
"""

        when:
        def first = build(':app:run', '--info')

        then: 'the project copies win'
        first.output.contains('resource=project class=project')
        first.output.contains('The project and its dependency JARs both have dup/Shared.class, logback.xml.')
        launchClasspath(first).first().endsWith('app/build/classes/java/main')
        !runCommand(first).contains('SharedArchiveFile')
        !runCommand(first).contains('DumpLoadedClassList')

        when: 'the same duplicates are found again'
        def second = build(':app:run')

        then: 'they are logged at info level only'
        second.output.contains('resource=project class=project')
        launchClasspath(second).first().endsWith('app/build/classes/java/main')
        !second.output.contains('The project and its dependency JARs both have')
    }

    def "a corrupt archive fails the probe once, and later launches neither probe nor print CDS messages"() {
        given:
        withProject("""
            micronaut.runClassDataSharing.enabled = true
            tasks.named('run') { jvmArgs '-Xlog:class+load' }
        """)
        build(':app:run')
        build(':app:run')
        def archive = archiveFiles()[0]
        // an archive without a verdict is probed rather than dumped again
        Files.delete(archive.resolveSibling('verdict.properties'))
        def bytes = Files.readAllBytes(archive)
        int middle = bytes.length.intdiv(2)
        for (int i = middle; i < middle + 4096; i++) {
            bytes[i] = (byte) (bytes[i] ^ 0x5a)
        }
        Files.write(archive, bytes)

        when:
        def probed = build(':app:run', '--info')

        then:
        !probed.output.contains(DUMPING)
        count(probed.output, PROBING) == 1
        warnings(probed).count { it.contains('could not create a CDS archive of its dependencies') && it.contains('probe-plain.log') } == 1
        !Files.exists(archive)
        !runCommand(probed).contains('SharedArchiveFile')
        loadSource(probed, 'dep.Dep').startsWith('file:')

        when:
        def later = build(':app:run', '--info')

        then:
        !later.output.contains(PROBING)
        !later.output.contains(DUMPING)
        !later.output.contains('could not create a CDS archive')
        !later.output.contains('[cds]')
        later.output.contains('app=v1 dep=dep-1.0 lib=lib-v1')
    }

    def "a launch with which the JVM turns sharing off gets no archive"() {
        given: 'with --limit-modules, -Xshare:on turns CDS off without failing'
        withProject("""
            micronaut.runClassDataSharing.enabled = true
            tasks.named('run') { jvmArgs '--limit-modules', 'java.base,jdk.management.agent' }
        """)
        build(':app:run')

        when:
        def result = build(':app:run', '--info')

        then:
        count(result.output, DUMPING) == 1
        count(result.output, PROBING) == 1
        result.output.contains('Probed the plain CDS archive')
        result.output.contains(': not sharing')
        warnings(result).count { it.contains('probe-plain.log') } == 1
        !runCommand(result).contains('SharedArchiveFile')
        archiveFiles().empty
        result.output.contains('app=v1 dep=dep-1.0 lib=lib-v1')
    }

    def "an AOT-linked archive is used only for launches that can use one"() {
        given:
        def agent = agentJar()
        int port = freePort()
        withProject("""
            micronaut.runClassDataSharing {
                enabled = true
                aotClassLinking = true
            }
            def variant = providers.gradleProperty('variant').getOrElse('none')
            tasks.named('run') {
                jvmArgs '-Xlog:class+load'
                if (variant == 'debug') {
                    debugOptions {
                        enabled = true
                        suspend = false
                        port = $port
                    }
                } else if (variant == 'agent') {
                    jvmArgs '-javaagent:${agent.absolutePath.replace('\\', '/')}'
                } else if (variant == 'tool-options') {
                    environment 'JAVA_TOOL_OPTIONS', '-javaagent:${agent.absolutePath.replace('\\', '/')}'
                } else if (variant == 'add-opens') {
                    jvmArgs '--add-opens', 'java.base/java.lang=ALL-UNNAMED'
                }
            }
        """)
        build(':app:run')

        when:
        def linked = build(':app:run', '--info')

        then:
        argumentFile('dump-linked.args').contains('-XX:+AOTClassLinking')
        verdictModes() == ['linked']
        loadSource(linked, 'dep.Dep') == 'shared objects file'

        when:
        def other = build(':app:run', '--info', "-Pvariant=$variant")

        then:
        other.output.contains("is not AOT-linked, because the launch has $reason")
        count(other.output, DUMPING) == 1
        verdictModes() == ['linked', 'plain']
        !argumentFile('dump-plain.args').contains('-XX:+AOTClassLinking')
        loadSource(other, 'dep.Dep') == 'shared objects file'

        where:
        variant        | reason
        'debug'        | 'a debugger (JDWP)'
        'agent'        | 'an agent (-javaagent)'
        'tool-options' | 'an agent (-javaagent)'
        'add-opens'    | '--add-opens'
    }

    @Requires({ jdk27() != null })
    def "the archive is plain on JDK 27"() {
        given:
        withProject("""
            micronaut.runClassDataSharing {
                enabled = true
                aotClassLinking = true
            }
            tasks.named('run') {
                javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(27) }
                jvmArgs '-Xlog:class+load'
            }
        """)
        def paths = "-Porg.gradle.java.installations.paths=${jdk27()}"
        build(':app:run', paths)

        when:
        def result = build(':app:run', '--info', paths)

        then:
        result.output.contains('is not AOT-linked, because the launcher is JDK 27')
        verdictModes() == ['plain']
        !Files.exists(archiveDirectories()[0].resolve('dump-linked.args'))
        loadSource(result, 'dep.Dep') == 'shared objects file'
    }

    def "the archive stays out of the build cache and works with the configuration cache"() {
        given:
        daemon = true
        withProject("""
            micronaut.runClassDataSharing.enabled = true
            tasks.named('run') { jvmArgs '-Xlog:class+load' }
        """)
        settingsFile << """
            buildCache {
                local {
                    directory = new File(rootDir, 'build-cache')
                }
            }
        """

        when:
        def first = build(':app:run', '--build-cache', '--configuration-cache')
        def second = build(':app:run', '--build-cache', '--configuration-cache', '--info')
        def third = build(':app:run', '--build-cache', '--configuration-cache', '--info')

        then:
        first.output.contains(RECORDING)
        second.output.contains('Configuration cache entry reused')
        count(second.output, DUMPING) == 1
        loadSource(second, 'dep.Dep') == 'shared objects file'
        third.output.contains('Configuration cache entry reused')
        !third.output.contains(DUMPING)
        loadSource(third, 'dep.Dep') == 'shared objects file'
        archiveFiles().size() == 1
        // the compilations are in the build cache, the archive is not
        cacheEntriesMentioning('example/Application.class') == 1
        cacheEntriesMentioning('run-class-data-sharing') == 0
        cacheEntriesMentioning('.jsa') == 0
    }

    @Override
    GradleRunner configureRunner(String... args) {
        def runner = super.configureRunner(args)
        daemon ? runner.withDebug(false) : runner
    }

    @Override
    File file(String relativePath) {
        File file = super.file(relativePath)
        file.parentFile.mkdirs()
        file
    }

    private void withProject(String appConfiguration) {
        settingsFile << """
            rootProject.name = 'cds'
            include 'app', 'lib'
        """
        buildFile << ""
        publishJar('dep', '1.0', ['dep/Dep.java': 'package dep; public class Dep { public static String hello() { return "dep-1.0"; } }'])
        publishJar('dep', '2.0', ['dep/Dep.java': 'package dep; public class Dep { public static String hello() { return "dep-2.0"; } }'])
        publishJar('dup', '1.0', ['dup/Shared.java': 'package dup; public class Shared { public static String who() { return "dependency"; } }'],
                ['logback.xml': 'dependency'])
        file('lib/build.gradle').text = """
            plugins { id 'java-library' }
            tasks.withType(JavaCompile).configureEach { options.release = 17 }
        """
        file('lib/src/main/java/lib').mkdirs()
        file('lib/src/main/java/lib/Greeter.java').text = greeter('lib-v1')
        file('app/build.gradle').text = """
            plugins {
                id 'io.micronaut.minimal.application'
            }

            micronaut {
                version "$micronautVersion"
            }

            repositories {
                maven { url = rootProject.file('repo') }
            }
            $repositoriesBlock

            dependencies {
                implementation 'com.example:dep:1.0'
                implementation project(':lib')
            }

            application { mainClass = 'example.Application' }
            // Micronaut 5 needs Java 21, and the launcher may be older than the JDK that runs the build
            tasks.withType(JavaCompile).configureEach { options.release = 21 }
            $appConfiguration
        """
        file('app/src/main/java/example').mkdirs()
        file('app/src/main/resources').mkdirs()
        file('app/src/main/resources/application.properties').text = 'micronaut.application.name=app\n'
        file('app/src/main/java/example/Application.java').text = application('v1')
    }

    private static String application(String version) {
        """package example;
public class Application {
    public static void main(String... args) {
        System.out.println("java.class.path=" + System.getProperty("java.class.path"));
        System.out.println("app=$version dep=" + dep.Dep.hello() + " lib=" + lib.Greeter.greet());
    }
}
"""
    }

    private static String greeter(String value) {
        "package lib; public class Greeter { public static String greet() { return \"$value\"; } }"
    }

    private void publishJar(String artifact, String version, Map<String, String> sources, Map<String, String> resources = [:]) {
        def dir = file("repo/com/example/$artifact/$version")
        dir.mkdirs()
        def jar = new File(dir, "$artifact-${version}.jar")
        writeJar(jar, compile(sources), resources, null)
        new File(dir, "$artifact-${version}.pom").text = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>$artifact</artifactId>
  <version>$version</version>
</project>
"""
    }

    private File agentJar() {
        def jar = file('agent/agent.jar')
        jar.parentFile.mkdirs()
        def manifest = new Manifest()
        manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
        manifest.mainAttributes.put(new Attributes.Name('Premain-Class'), 'agent.Agent')
        writeJar(jar, compile(['agent/Agent.java': 'package agent; public class Agent { public static void premain(String args) { } }']), [:], manifest)
        jar
    }

    private Path compile(Map<String, String> sources) {
        Path sourceDir = Files.createTempDirectory(baseDir, 'src')
        Path classesDir = Files.createTempDirectory(baseDir, 'classes')
        def files = sources.collect { name, text ->
            Path source = sourceDir.resolve(name)
            Files.createDirectories(source.parent)
            source.text = text
            source.toString()
        }
        def compiler = ToolProvider.systemJavaCompiler
        assert compiler.run(null, null, null, *(['--release', '17', '-d', classesDir.toString()] + files)) == 0
        classesDir
    }

    private static void writeJar(File jar, Path classesDir, Map<String, String> resources, Manifest manifest) {
        jar.withOutputStream { out ->
            def jarOut = manifest == null ? new JarOutputStream(out) : new JarOutputStream(out, manifest)
            Files.walk(classesDir).filter { Files.isRegularFile(it) }.sorted().forEach { Path p ->
                jarOut.putNextEntry(new JarEntry(classesDir.relativize(p).toString().replace(File.separatorChar, (char) '/')))
                jarOut.write(Files.readAllBytes(p))
                jarOut.closeEntry()
            }
            resources.each { name, text ->
                jarOut.putNextEntry(new JarEntry(name))
                jarOut.write(text.bytes)
                jarOut.closeEntry()
            }
            jarOut.finish()
        }
    }

    private Path archiveRoot() {
        file('app/build/run-class-data-sharing').toPath()
    }

    private List<Path> archiveDirectories() {
        def result = []
        if (Files.isDirectory(archiveRoot())) {
            Files.walk(archiveRoot()).filter { Files.isRegularFile(it) && it.fileName.toString() == 'key.txt' }.sorted().forEach { result << it.parent }
        }
        result
    }

    private List<Path> archiveFiles() {
        def result = []
        if (Files.isDirectory(archiveRoot())) {
            Files.walk(archiveRoot()).filter { Files.isRegularFile(it) && it.fileName.toString().endsWith('.jsa') }.sorted().forEach { result << it }
        }
        result
    }

    private List<String> verdictModes() {
        archiveDirectories().collect { dir ->
            def verdict = new Properties()
            dir.resolve('verdict.properties').withInputStream { verdict.load(it) }
            verdict.getProperty('mode')
        }.findAll { it != null }.sort()
    }

    private List<String> argumentFile(String name) {
        def found = archiveDirectories().collect { it.resolve(name) }.findAll { Files.exists(it) }
        assert found.size() == 1
        found[0].readLines()
    }

    private int cacheEntriesMentioning(String text) {
        def cacheDir = file('build-cache')
        if (!cacheDir.directory) {
            return 0
        }
        cacheDir.listFiles().findAll { it.file && !it.name.endsWith('.properties') && !it.name.endsWith('.lock') }.count { entry ->
            try {
                new GZIPInputStream(entry.newInputStream()).withStream { new String(it.readAllBytes(), 'ISO-8859-1').contains(text) }
            } catch (IOException ignored) {
                false
            }
        } as int
    }

    private static String runCommand(BuildResult result) {
        def line = result.output.readLines().find { it.contains('Starting process') && it.contains('example.Application') }
        assert line != null
        line.substring(line.indexOf('Command: ') + 'Command: '.length())
    }

    private static List<String> launchClasspath(BuildResult result) {
        def line = result.output.readLines().find { it.startsWith('java.class.path=') }
        assert line != null
        line.substring('java.class.path='.length()).split(File.pathSeparator).toList()*.replace(File.separatorChar, (char) '/')
    }

    private static String loadSource(BuildResult result, String className) {
        def line = result.output.readLines().find { it.contains('[class,load] ' + className + ' source: ') }
        assert line != null
        line.substring(line.indexOf(' source: ') + ' source: '.length()).trim()
    }

    private static List<String> warnings(BuildResult result) {
        result.output.readLines().findAll { it.startsWith('The project and its dependency JARs') || it.startsWith('The run task could not') }
    }

    private static int count(String output, String text) {
        output.readLines().count { it.contains(text) } as int
    }

    private static int freePort() {
        new ServerSocket(0).withCloseable { it.localPort }
    }

    static String olderJdk() {
        ['JAVA_HOME_21_X64', 'JAVA_HOME_21_ARM64', 'JDK21_HOME']
                .collect { System.getenv(it) }
                .find { it && new File(it).directory }
    }

    static int olderJdkVersion() {
        def release = new File(olderJdk(), 'release')
        def line = release.readLines().find { it.startsWith('JAVA_VERSION=') }
        line.replace('JAVA_VERSION=', '').replace('"', '').tokenize('.')[0] as int
    }

    static String jdk27() {
        ['JDK27_HOME', 'JAVA_HOME_27_X64', 'JAVA_HOME_27_ARM64']
                .collect { System.getenv(it) }
                .find { it && new File(it).directory }
    }
}
