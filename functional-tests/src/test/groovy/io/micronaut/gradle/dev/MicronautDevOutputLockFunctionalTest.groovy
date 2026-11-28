package io.micronaut.gradle.dev

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class MicronautDevOutputLockFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    def "the compile tasks wait for a development process that holds the lock of the class output"() {
        given:
        writeProject()
        File lockFile = file("build/classes/java/main.micronaut-dev.lock")

        when: "another process, as micronaut-dev does while it compiles, holds the lock"
        def holder = holdLock(lockFile)
        def output = new StringWriter()
        def forwarded = new Writer() {
            void write(char[] chars, int off, int len) { synchronized (output) { output.write(chars, off, len) } }
            void flush() { }
            void close() { }
        }
        def text = { synchronized (output) { return output.toString() } }
        def compiling = CompletableFuture.supplyAsync { configureRunner("compileJava").forwardStdOutput(forwarded).build() }
        // until the build reaches the lock, which it cannot take
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(3)
        while (!text().contains("waiting for it") && !compiling.isDone() && System.currentTimeMillis() < deadline) {
            Thread.sleep(200)
        }
        Thread.sleep(2_000)

        then:
        text().contains("waiting for it")
        !compiling.isDone()
        !file("build/classes/java/main/example/Application.class").exists()

        when: "the process releases it"
        holder.outputStream.close()
        holder.waitFor(30, TimeUnit.SECONDS)
        BuildResult result = compiling.get(5, TimeUnit.MINUTES)

        then:
        result.task(":mnDevLockOutputs").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
        result.task(":mnDevUnlockOutputs").outcome == TaskOutcome.SUCCESS
        file("build/classes/java/main/example/Application.class").exists()

        and: "the build released it"
        canLock(lockFile)

        cleanup:
        holder?.destroyForcibly()
    }

    def "the locks are taken around the compile tasks, released before the launch, and fit for the configuration cache"() {
        given:
        writeProject()

        when:
        def compiled = build("testClasses")

        then:
        compiled.task(":mnDevLockOutputs").outcome == TaskOutcome.SUCCESS
        compiled.task(":mnDevUnlockOutputs").outcome == TaskOutcome.SUCCESS
        file("build/classes/java/main.micronaut-dev.lock").exists()
        file("build/classes/java/test.micronaut-dev.lock").exists()
        canLock(file("build/classes/java/main.micronaut-dev.lock"))
        canLock(file("build/classes/java/test.micronaut-dev.lock"))

        when: "clean takes the locks too, and those of the files at their paths once it deleted them"
        def cleaned = build("clean")

        then:
        cleaned.task(":mnDevLockOutputs").outcome == TaskOutcome.SUCCESS
        cleaned.task(":clean").outcome == TaskOutcome.SUCCESS
        cleaned.task(":mnDevUnlockOutputs").outcome == TaskOutcome.SUCCESS
        !file("build/classes/java/main/example/Application.class").exists()
        file("build/classes/java/main.micronaut-dev.lock").exists()
        canLock(file("build/classes/java/main.micronaut-dev.lock"))

        when: "the configuration cache stores the two tasks, with the service, and reuses them"
        def stored = build("mnDevLockOutputs", "mnDevUnlockOutputs", "--configuration-cache")
        def reused = build("mnDevLockOutputs", "mnDevUnlockOutputs", "--configuration-cache")

        then:
        stored.output.contains("Configuration cache entry stored")
        reused.output.contains("Reusing configuration cache")
        reused.task(":mnDevUnlockOutputs").outcome == TaskOutcome.SUCCESS
        canLock(file("build/classes/java/main.micronaut-dev.lock"))

        when: "the order of the tasks: lock, compile, unlock, then the launch"
        def dry = build("mnTest", "mnDev", "--dry-run")
        def order = dry.output.readLines().findAll { it.startsWith(":") }.collect { it.split(" ")[0] }

        then:
        order.indexOf(":mnDevLockOutputs") < order.indexOf(":compileJava")
        order.indexOf(":compileJava") < order.indexOf(":compileTestJava")
        order.indexOf(":compileTestJava") < order.indexOf(":mnDevUnlockOutputs")
        order.indexOf(":mnDevUnlockOutputs") < order.indexOf(":mnDev")
        order.indexOf(":mnDevUnlockOutputs") < order.indexOf(":mnTest")
    }

    private void writeProject() {
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            group = "example"
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
            }
            $repositoriesBlock
            application { mainClass = "example.Application" }
        """
        writeSource("src/main/java/example/Application.java", """
            package example;
            public class Application {
                public static void main(String[] args) { }
            }
        """)
        writeSource("src/test/java/example/ApplicationTest.java", """
            package example;
            class ApplicationTest { }
        """)
    }

    private void writeSource(String path, String source) {
        File file = file(path)
        file.parentFile.mkdirs()
        file.text = source
    }

    /**
     * A JVM apart from the build's, which runs in the test's own: a file lock excludes other processes, not other
     * channels of the one that holds it. It locks the file, says so, and holds it until its input is closed.
     */
    private Process holdLock(File lockFile) {
        lockFile.parentFile.mkdirs()
        File source = new File(baseDir.toFile(), "locker/Locker.java")
        source.parentFile.mkdirs()
        source.text = """
            import java.nio.channels.FileChannel;
            import java.nio.file.Path;
            import java.nio.file.StandardOpenOption;
            public class Locker {
                public static void main(String[] args) throws Exception {
                    try (FileChannel channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                        channel.lock();
                        System.out.println("locked");
                        System.out.flush();
                        while (System.in.read() != -1) { }
                    }
                }
            }
        """
        String java = new File(System.getProperty("java.home"), "bin/java").absolutePath
        Process process = new ProcessBuilder(java, source.absolutePath, lockFile.absolutePath).redirectErrorStream(true).start()
        String line = new BufferedReader(new InputStreamReader(process.inputStream)).readLine()
        assert line == "locked"
        process
    }

    private static boolean canLock(File lockFile) {
        def channel = java.nio.channels.FileChannel.open(lockFile.toPath(), java.nio.file.StandardOpenOption.WRITE)
        try {
            def lock = channel.tryLock()
            lock?.release()
            lock != null
        } catch (java.nio.channels.OverlappingFileLockException ignored) {
            // the build, which runs in this JVM, still holds it
            false
        } finally {
            channel.close()
        }
    }
}
