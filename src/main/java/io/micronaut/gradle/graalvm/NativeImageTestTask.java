package io.micronaut.gradle.graalvm;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.testing.Test;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executes the applications tests against the native image by building the native image,
 * starting the process and passing the necessary parameters to expose the native image to the
 * Micronaut application.
 */
public class NativeImageTestTask extends DefaultTask {

    /**
     * The minimum port number.
     */
    private static final int MIN_PORT_RANGE = 1025;

    /**
     * The maximum port number.
     */
    private static final int MAX_PORT_RANGE = 65535;

    @SuppressWarnings("ConstantName")
    private static final Random random = new Random(System.currentTimeMillis());

    private final ListProperty<String> args;

    public NativeImageTestTask() {
        this.args = getObjectFactory().listProperty(String.class)
                .convention(new ArrayList<>(5));
    }

    /**
     * Adds args for the main class to be executed.
     *
     * @param args Args for the main class.
     *
     * @return this
     */
    public NativeImageTestTask args(Iterable<?> args) {
        if (args != null) {
            for (Object arg : args) {
                if (arg != null) {
                    this.args.add(arg.toString());
                }
            }
        }
        return this;
    }

    /**
     * Adds args for the main class to be executed.
     *
     * @param args Args for the main class.
     *
     * @return this
     */
    public NativeImageTestTask args(Object... args) {
        if (args != null) {
            for (Object arg : args) {
                if (arg != null) {
                    this.args.add(arg.toString());
                }
            }
        }
        return this;
    }

    /**
     * Sets the args for the native server.
     *
     * @param args Args for the main class.
     * @return this
     */
    public NativeImageTestTask setArgs(@Nullable List<String> args) {
        this.args.set(args);
        return this;
    }

    @Input
    public ListProperty<String> getArgs() {
        return args;
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void exec() {
        Project project = getProject();
        NativeImageTask nativeImage = (NativeImageTask) project.getTasks().findByName("nativeImage");
        Test test = (Test) project.getTasks().findByName("test");
        File file = nativeImage.getNativeImageOutput();
        ExecutorService es = Executors.newSingleThreadExecutor();
        CompletableFuture<Process> processFuture = new CompletableFuture<>();
        int port = findAvailableTcpPort();
        es.submit(() -> {
            ProcessBuilder processBuilder = new ProcessBuilder();
            List<String> commandArgs = new ArrayList<>();
            commandArgs.addAll(Arrays.asList(
                    file.getAbsolutePath(),
                    "-Dmicronaut.environments=test",
                    "-Dmicronaut.server.host=localhost",
                    "-Dmicronaut.server.port=" + port
            ));
            commandArgs.addAll(this.args.get());
            processBuilder.command(commandArgs);
            try {
                Process start = processBuilder.start();
                processFuture.complete(start);
                inheritIO(start.getInputStream());
                inheritIO(start.getErrorStream());
            } catch (IOException e) {
                processFuture.completeExceptionally(new RuntimeException("Error starting native image server: " + e.getMessage(), e));
            }
        });

        try {
            Process process = processFuture.get();
            test.doLast((task) -> process.destroy());
            test.systemProperty("micronaut.test.server.url", "http://localhost:" + port);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error starting native image server: " + e.getMessage(), e);
        }

    }

    private void inheritIO(final InputStream src) {
        PrintStream out = System.out;
        new Thread(() -> {
            Scanner sc = new Scanner(src);
            while (sc.hasNextLine()) {
                out.println(sc.nextLine());
            }
        }).start();
    }

    /**
     * Finds an available TCP port.
     *
     * @return The available port
     */
    private static int findAvailableTcpPort() {
        int currentPort = nextPort();
        while (!isTcpPortAvailable(currentPort)) {
            currentPort = nextPort();
        }
        return currentPort;
    }


    private static boolean isTcpPortAvailable(int currentPort) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLocalHost(), currentPort), 20);
            return false;
        } catch (Throwable e) {
            return true;
        }
    }

    private static int nextPort() {
        int seed = NativeImageTestTask.MAX_PORT_RANGE - NativeImageTestTask.MIN_PORT_RANGE;
        return random.nextInt(seed) + NativeImageTestTask.MIN_PORT_RANGE;
    }
}
