package io.micronaut.gradle.internal

import org.gradle.api.services.BuildServiceParameters
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class MicronautDevOutputLocksSpec extends Specification {

    @TempDir
    Path dir

    def "the lock file of an output is named as the launcher names it, beside the output"() {
        given:
        File output = dir.resolve("build/classes/java/main").toFile()

        expect:
        MicronautDevOutputLocks.lockFile(output) == dir.resolve("build/classes/java/main.micronaut-dev.lock").toAbsolutePath().normalize()
    }

    def "the lock files are taken in the order of their paths, once each"() {
        given:
        def outputs = ["kotlin/main", "java/test", "groovy/main", "java/main", "java/main"].collect { dir.resolve("build/classes/" + it).toFile() }

        expect:
        MicronautDevOutputLocks.lockFiles(outputs)*.toString()*.substring(dir.toAbsolutePath().toString().length() + 1)*.replace(File.separator, "/") == [
            "build/classes/groovy/main.micronaut-dev.lock",
            "build/classes/java/main.micronaut-dev.lock",
            "build/classes/java/test.micronaut-dev.lock",
            "build/classes/kotlin/main.micronaut-dev.lock"
        ]
    }

    @Requires({ !os.windows })
    def "a lock file deleted and created again while waiting for another JVM is locked at its path, not the deleted one"() {
        given: "another JVM, as micronaut-dev is, holds the lock"
        Path lockFile = dir.resolve("build/classes/java/main.micronaut-dev.lock").toAbsolutePath().normalize()
        Process holder = holdLock(lockFile)
        def locks = new MicronautDevOutputLocks() {
            @Override
            BuildServiceParameters.None getParameters() {
                null
            }
        }

        when: "the build waits for it"
        def taking = CompletableFuture.runAsync { locks.lock([lockFile]) }
        Thread.sleep(1000)

        then:
        !taking.done

        when: "a clean deletes the file, another process creates it again, and the holder lets go"
        Files.delete(lockFile)
        Files.createFile(lockFile)
        holder.outputStream.close()
        holder.waitFor(30, TimeUnit.SECONDS)
        taking.get(30, TimeUnit.SECONDS)

        then: "this JVM holds the file the path names now: a JVM holding a lock of it cannot take another"
        heldByThisJvm(lockFile)

        when:
        locks.close()

        then:
        !heldByThisJvm(lockFile)

        cleanup:
        holder?.destroyForcibly()
    }

    private Process holdLock(Path lockFile) {
        Files.createDirectories(lockFile.parent)
        Path source = dir.resolve("locker/Locker.java")
        Files.createDirectories(source.parent)
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
        Process process = new ProcessBuilder(java, source.toString(), lockFile.toString()).redirectErrorStream(true).start()
        assert new BufferedReader(new InputStreamReader(process.inputStream)).readLine() == "locked"
        process
    }

    private static boolean heldByThisJvm(Path lockFile) {
        FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)
        try {
            channel.tryLock()?.release()
            false
        } catch (OverlappingFileLockException ignored) {
            true
        } finally {
            channel.close()
        }
    }
}
