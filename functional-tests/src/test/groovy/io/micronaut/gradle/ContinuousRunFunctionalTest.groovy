package io.micronaut.gradle

import io.micronaut.gradle.fixtures.AbstractFunctionalTest
import spock.lang.Issue

import java.net.HttpURLConnection
import java.net.ServerSocket
import java.nio.file.Files
import java.time.Duration

@Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/2")
class ContinuousRunFunctionalTest extends AbstractFunctionalTest {
    private static final String INTERNAL_CONTINUOUS_STARTUP_TIMEOUT_PROPERTY = "io.micronaut.internal.gradle.continuous.startup.timeout"

    def "parallel continuous run keeps sibling applications available"() {
        given:
        int booksPort = findAvailablePort()
        int authorsPort = findAvailablePort()
        settingsFile << """
            rootProject.name = 'multi-run'
            include 'books', 'authors'
        """
        buildFile << """
            allprojects {
                group = "example"
                version = "0.1"
            }
        """
        writeApplication("books", booksPort, "BooksController", "/books", "books-v1")
        writeApplication("authors", authorsPort, "AuthorsController", "/authors", "authors-v1")

        File outputFile = file("build/continuous-run.log")
        outputFile.parentFile.mkdirs()
        Process buildProcess = startContinuousBuild(outputFile)

        when:
        awaitHttp("books startup", "http://127.0.0.1:${booksPort}/books", "books-v1", Duration.ofSeconds(120), outputFile)
        awaitHttp("authors startup", "http://127.0.0.1:${authorsPort}/authors", "authors-v1", Duration.ofSeconds(120), outputFile)

        File application = file("books/src/main/java/example/books/Application.java")
        application.text = application.text.replace('"books-v1"', '"books-v2"')

        awaitBodyChange(
            "http://127.0.0.1:${booksPort}/books",
            "books-v2",
            "http://127.0.0.1:${authorsPort}/authors",
            "authors-v1",
            Duration.ofSeconds(120),
            outputFile
        )

        then:
        true

        cleanup:
        buildProcess?.destroy()
        if (buildProcess?.isAlive()) {
            buildProcess.destroyForcibly()
            buildProcess.waitFor()
        }
    }

    def "continuous run fails when startup exits non-zero after 500ms"() {
        given:
        settingsFile << "rootProject.name = 'delayed-failure'"
        buildFile << """
            plugins {
                id 'io.micronaut.application'
            }

            micronaut {
                version '${micronautVersion}'
                runtime 'netty'
            }

            repositories {
                mavenCentral()
            }

            application {
                mainClass = 'example.Application'
            }
        """
        file("src/main/java/example").mkdirs()
        file("src/main/java/example/Application.java").text = """
package example;

public final class Application {
    private Application() {
    }

    public static void main(String[] args) throws Exception {
        Thread.sleep(1500);
        System.exit(1);
    }
}
"""

        File outputFile = file("build/continuous-run-failure.log")
        outputFile.parentFile.mkdirs()
        Process buildProcess = startContinuousBuild(outputFile, "-D${INTERNAL_CONTINUOUS_STARTUP_TIMEOUT_PROPERTY}=3000")

        when:
        int exitCode = awaitProcessExit(buildProcess, Duration.ofSeconds(120), outputFile)

        then:
        exitCode != 0
        failureMessage("Expected Gradle to fail the continuous run build.", outputFile).contains("Continuous run process exited during startup with code 1.")
        failureMessage("Expected Gradle to fail the continuous run build.", outputFile).contains("Build cancelled")
    }

    private Process startContinuousBuild(File outputFile) {
        startContinuousBuild(outputFile, new String[0])
    }

    private Process startContinuousBuild(File outputFile, String... extraArgs) {
        File repoRoot = findRepoRoot()
        List<String> command = [
            new File(repoRoot, "gradlew").canonicalPath,
            "-p", baseDir.toFile().absolutePath,
            "--no-daemon",
            "--parallel",
            "run",
            "--continuous",
            "-S",
            "-Porg.gradle.java.installations.auto-download=false",
            "-Porg.gradle.java.installations.auto-detect=false",
            "-Porg.gradle.java.installations.fromEnv=GRAALVM_HOME",
            "-Dio.micronaut.graalvm.rich.output=false",
            *extraArgs
        ]
        ProcessBuilder builder = new ProcessBuilder(command)
        builder.directory(repoRoot)
        builder.redirectErrorStream(true)
        builder.redirectOutput(outputFile)
        builder.start()
    }

    private static int awaitProcessExit(Process buildProcess, Duration timeout, File outputFile) {
        if (!buildProcess.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
            buildProcess.destroyForcibly()
            buildProcess.waitFor()
            throw new AssertionError(failureMessage("Timed out waiting for Gradle to exit.", outputFile))
        }
        buildProcess.exitValue()
    }

    private void writeApplication(String projectName, int port, String controllerName, String route, String body) {
        File projectDir = file(projectName)
        new File(projectDir, "src/main/java/example/${projectName}").mkdirs()
        new File(projectDir, "build.gradle").text = """
            plugins {
                id 'io.micronaut.application'
            }

            micronaut {
                version '${micronautVersion}'
                runtime 'netty'
            }

            repositories {
                mavenCentral()
            }

            application {
                mainClass = 'example.${projectName}.Application'
            }
        """
        new File(projectDir, "src/main/java/example/${projectName}/Application.java").text = """
package example.${projectName};

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

public final class Application {
    private Application() {
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", ${port}), 0);
        server.createContext("${route}", new ResponseHandler("${body}"));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        new CountDownLatch(1).await();
    }

    private static final class ResponseHandler implements HttpHandler {
        private final byte[] responseBody;

        private ResponseHandler(String responseBody) {
            this.responseBody = responseBody.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(200, responseBody.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBody);
            } finally {
                exchange.close();
            }
        }
    }
}
"""
    }

    private static File findRepoRoot() {
        File current = new File(System.getProperty("user.dir")).canonicalFile
        while (current != null && !new File(current, "gradlew").exists()) {
            current = current.parentFile
        }
        if (current == null) {
            throw new IllegalStateException("Cannot locate the repository root from ${System.getProperty("user.dir")}")
        }
        current
    }

    private static int findAvailablePort() {
        ServerSocket socket = new ServerSocket(0)
        try {
            socket.localPort
        } finally {
            socket.close()
        }
    }

    private static void awaitHttp(String description, String url, String expectedBody, Duration timeout, File outputFile) {
        long deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            try {
                if (readBody(url) == expectedBody) {
                    return
                }
            } catch (Exception ignored) {
                // wait for the application to come up
            }
            Thread.sleep(500)
        }
        throw new AssertionError(failureMessage("Timed out waiting for ${description} at ${url}.", outputFile))
    }

    private static String failureMessage(String message, File outputFile) {
        String output = outputFile.exists() ? Files.readString(outputFile.toPath()) : ""
        return message + System.lineSeparator() + output.takeRight(12000)
    }

    private static String readBody(String url) {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection()
        connection.connectTimeout = 1000
        connection.readTimeout = 1000
        connection.requestMethod = "GET"
        try {
            connection.inputStream.getText("UTF-8").trim()
        } finally {
            connection.disconnect()
        }
    }

    private static String tryReadBody(String url) {
        try {
            return readBody(url)
        } catch (Exception ignored) {
            return null
        }
    }

    private static void awaitBodyChange(String url, String expectedBody, String siblingUrl, String siblingBody, Duration timeout, File outputFile) {
        long deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            assert readBody(siblingUrl) == siblingBody: failureMessage("Sibling application became unavailable during the restart.", outputFile)
            if (tryReadBody(url) == expectedBody) {
                return
            }
            Thread.sleep(500)
        }
        throw new AssertionError(failureMessage("Timed out waiting for ${expectedBody} at ${url}.", outputFile))
    }
}
