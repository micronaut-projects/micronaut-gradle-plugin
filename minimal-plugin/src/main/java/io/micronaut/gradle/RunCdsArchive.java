/*
 * Copyright 2003-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle;

import org.gradle.api.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The CDS archive of the dependency JARs of the {@code run} task, and the files that it is made from,
 * under one directory of the build directory:
 * <pre>
 * &lt;dependency key&gt;/                  one per set of dependency JARs (path, size and mtime of each)
 *     entries.idx                      the entry names of the JARs, for the duplicate check
 *     duplicates.txt                   the duplicates that were last logged
 *     classlist-&lt;jdk&gt;.recording       the class list that a run is recording
 *     classlist-&lt;jdk&gt;.txt             the recorded class list
 *     &lt;archive key&gt;/                  one per archive key
 *         archive-plain.jsa or archive-linked.jsa
 *         dump-*.args, dump-*.log, probe-*.args, probe-*.log
 *         verdict.properties           the archive to use, or none, for this key
 * </pre>
 * The archive key covers the JDK, the mode, the module and {@code -XX} options of the launch, the class
 * list and the dependency JARs. When it changes, the archive is dumped and checked once. A launch with a
 * known key only reads the verdict.
 */
final class RunCdsArchive {

    static final String INDEX_FILE = "entries.idx";
    static final String VERDICT_FILE = "verdict.properties";

    private static final String DUPLICATES_FILE = "duplicates.txt";
    private static final String VERDICT_ARCHIVE = "archive";
    private static final String VERDICT_MODE = "mode";
    private static final int KEY_LENGTH = 16;
    private static final long DUMP_TIMEOUT_SECONDS = 600;
    private static final long PROBE_TIMEOUT_SECONDS = 120;
    private static final int LOGGED_DUPLICATES = 10;

    /**
     * How the archive is dumped.
     */
    enum Mode {
        /**
         * A static CDS archive.
         */
        PLAIN("plain"),
        /**
         * A static CDS archive dumped with {@code -XX:+AOTClassLinking}.
         */
        LINKED("linked");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }
    }

    private final Path root;
    private final Path dependencyDirectory;
    private final String dependencyKey;
    private final List<File> jars;
    private final Path java;
    private final List<String> jdkIdentity;
    private final Map<String, String> environment;
    private final Logger logger;

    /**
     * Creates the archive of a set of dependency JARs.
     *
     * @param root the directory of all archives of the project
     * @param jars the dependency JARs, in class path order
     * @param java the {@code java} executable of the launcher
     * @param javaHome the installation directory of the launcher
     * @param javaRuntimeVersion the {@code java.runtime.version} of the launcher
     * @param environment the environment of the launch
     * @param logger the logger
     */
    RunCdsArchive(Path root,
                  List<File> jars,
                  Path java,
                  Path javaHome,
                  String javaRuntimeVersion,
                  Map<String, String> environment,
                  Logger logger) {
        this.root = root;
        this.jars = List.copyOf(jars);
        this.java = java;
        this.environment = environment;
        this.logger = logger;
        var jarLines = new ArrayList<String>(jars.size());
        for (File jar : jars) {
            jarLines.add(fileIdentity(jar));
        }
        this.dependencyKey = sha256(jarLines);
        this.dependencyDirectory = root.resolve(dependencyKey.substring(0, KEY_LENGTH));
        // the runtime image is the first entry that the JVM validates
        this.jdkIdentity = List.of(
            "jdk=" + javaHome.toAbsolutePath(),
            "version=" + javaRuntimeVersion,
            "modules=" + fileIdentity(javaHome.resolve("lib").resolve("modules").toFile())
        );
    }

    /**
     * Finds the paths that are both in an entry that changes between runs and in a dependency JAR.
     * With the dependencies first, the dependency's copy of such a path would win, so the launch keeps
     * the default order. Merged and metadata paths, such as service files, are not compared.
     *
     * @param changingEntries the class path entries that are not archived
     * @return the duplicate paths, sorted
     * @throws IOException if an entry or the index cannot be read
     */
    List<String> findDuplicates(List<File> changingEntries) throws IOException {
        Set<String> index = index();
        var duplicates = new TreeSet<String>();
        for (File entry : changingEntries) {
            if (entry.isDirectory()) {
                Path base = entry.toPath();
                try (Stream<Path> files = Files.walk(base)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        if (Files.isRegularFile(file)) {
                            String name = base.relativize(file).toString().replace(File.separatorChar, '/');
                            if (index.contains(name) && !isMergedOrMetadata(name)) {
                                duplicates.add(name);
                            }
                        }
                    }
                }
            } else if (entry.isFile() && isZip(entry)) {
                try (var zip = new ZipFile(entry)) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry zipEntry = entries.nextElement();
                        String name = zipEntry.getName();
                        if (!zipEntry.isDirectory() && index.contains(name) && !isMergedOrMetadata(name)) {
                            duplicates.add(name);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(duplicates);
    }

    /**
     * Logs duplicate paths: as a warning when they differ from the ones logged last, and at info level
     * otherwise.
     *
     * @param duplicates the duplicate paths
     */
    void logDuplicates(List<String> duplicates) {
        Path file = dependencyDirectory.resolve(DUPLICATES_FILE);
        String content = String.join("\n", duplicates) + "\n";
        boolean known = false;
        try {
            known = Files.isRegularFile(file) && Files.readString(file, StandardCharsets.UTF_8).equals(content);
            if (!known) {
                writeAtomically(file, content.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // logged as new
        }
        String shown = duplicates.stream().limit(LOGGED_DUPLICATES).collect(Collectors.joining(", "));
        if (duplicates.size() > LOGGED_DUPLICATES) {
            shown += " and " + (duplicates.size() - LOGGED_DUPLICATES) + " more";
        }
        String message = "The project and its dependency JARs both have " + shown + ". So that the project's copy wins, "
            + "the run task keeps the default class path order and starts without a CDS archive.";
        if (known) {
            logger.info(message);
        } else {
            logger.warn(message);
        }
    }

    /**
     * Prepares the archive for a launch whose class path starts with the dependency JARs, and returns the
     * JVM arguments that the launch adds: the recording of the class list when there is none for these
     * dependencies and this JDK, the archive when it can be used, and nothing otherwise. The archive is
     * dumped and probed only when its key has no verdict yet.
     *
     * @param mode the requested mode
     * @param options the options of the launch
     * @param launchClasspath the class path of the launch
     * @return the JVM arguments to add
     * @throws IOException if a file of the archive cannot be read or written
     */
    List<String> launchArguments(Mode mode, RunCdsJvmOptions options, List<File> launchClasspath) throws IOException {
        Files.createDirectories(dependencyDirectory);
        String jdkKey = sha256(jdkIdentity).substring(0, KEY_LENGTH);
        Path classList = dependencyDirectory.resolve("classlist-" + jdkKey + ".txt");
        Path recording = dependencyDirectory.resolve("classlist-" + jdkKey + ".recording");
        if (!Files.isRegularFile(classList) && !completeRecording(recording, classList)) {
            Files.deleteIfExists(recording);
            logger.lifecycle("Recording the classes that this run loads, to create a CDS archive of the dependencies on the next run.");
            return List.of("-XX:DumpLoadedClassList=" + recording.toAbsolutePath());
        }
        var keyLines = new ArrayList<String>(jdkIdentity);
        keyLines.add("mode=" + mode.id());
        keyLines.addAll(options.keyOptions());
        keyLines.add("classlist=" + sha256(Files.readAllBytes(classList)));
        keyLines.add("dependencies=" + dependencyKey);
        Path keyDirectory = dependencyDirectory.resolve(sha256(keyLines).substring(0, KEY_LENGTH));
        Path archive = readVerdict(keyDirectory);
        if (archive == null && !Files.isRegularFile(keyDirectory.resolve(VERDICT_FILE))) {
            Files.createDirectories(keyDirectory);
            Files.write(keyDirectory.resolve("key.txt"), keyLines, StandardCharsets.UTF_8);
            archive = createArchive(keyDirectory, mode, options, classList, launchClasspath);
            deleteOtherDependencySets();
        }
        if (archive == null) {
            return List.of();
        }
        logger.info("Starting with the CDS archive {}", archive);
        var arguments = new ArrayList<String>();
        arguments.add("-XX:SharedArchiveFile=" + archive.toAbsolutePath());
        if (!options.logsClassDataSharing()) {
            // a mismatch that the key missed makes the JVM run without CDS, but never fails the run
            arguments.add("-Xlog:cds*=off,aot*=off");
        }
        return arguments;
    }

    /**
     * Reads the verdict of an archive key.
     *
     * @return the archive to use, or null when the key has no verdict, its verdict is to use no archive,
     * or its archive is gone
     */
    private Path readVerdict(Path keyDirectory) throws IOException {
        Path verdictFile = keyDirectory.resolve(VERDICT_FILE);
        if (!Files.isRegularFile(verdictFile)) {
            return null;
        }
        var verdict = new Properties();
        try (InputStream in = Files.newInputStream(verdictFile)) {
            verdict.load(in);
        }
        String name = verdict.getProperty(VERDICT_ARCHIVE, "");
        if (name.isEmpty()) {
            return null;
        }
        Path archive = keyDirectory.resolve(name);
        if (!Files.isRegularFile(archive)) {
            // the archive was deleted: check this key again
            Files.deleteIfExists(verdictFile);
            return null;
        }
        return archive;
    }

    private Path createArchive(Path keyDirectory, Mode requested, RunCdsJvmOptions options, Path classList, List<File> launchClasspath) throws IOException {
        List<Mode> attempts = requested == Mode.LINKED ? List.of(Mode.LINKED, Mode.PLAIN) : List.of(Mode.PLAIN);
        Path failureLog = null;
        Path archive = null;
        Mode archiveMode = null;
        for (Mode mode : attempts) {
            Path candidate = keyDirectory.resolve("archive-" + mode.id() + ".jsa");
            // an archive without a verdict was dumped by a run that stopped before its probe
            if (!Files.isRegularFile(candidate) && !dump(keyDirectory, mode, options, classList, candidate)) {
                failureLog = keyDirectory.resolve("dump-" + mode.id() + ".log");
                continue;
            }
            if (probe(keyDirectory, mode, options, candidate, launchClasspath)) {
                archive = candidate;
                archiveMode = mode;
                break;
            }
            failureLog = keyDirectory.resolve("probe-" + mode.id() + ".log");
            Files.deleteIfExists(candidate);
        }
        var verdict = new Properties();
        if (archive != null) {
            verdict.setProperty(VERDICT_ARCHIVE, archive.getFileName().toString());
            verdict.setProperty(VERDICT_MODE, archiveMode.id());
            if (failureLog != null) {
                logger.lifecycle("The run task could not use an AOT-linked CDS archive (see {}). It uses a plain one.", failureLog);
            }
        } else {
            verdict.setProperty(VERDICT_ARCHIVE, "");
            logger.warn("The run task could not create a CDS archive of its dependencies (see {}). It starts without one until the dependencies, the JDK or the JVM options change.", failureLog);
        }
        var bytes = new ByteArrayOutputStream();
        verdict.store(bytes, null);
        writeAtomically(keyDirectory.resolve(VERDICT_FILE), bytes.toByteArray());
        return archive;
    }

    private boolean dump(Path keyDirectory, Mode mode, RunCdsJvmOptions options, Path classList, Path archive) throws IOException {
        Path temporary = keyDirectory.resolve(archive.getFileName() + "." + UUID.randomUUID() + ".tmp");
        var arguments = new ArrayList<String>();
        arguments.add("-Xshare:dump");
        arguments.add("-XX:SharedClassListFile=" + classList.toAbsolutePath());
        arguments.add("-XX:SharedArchiveFile=" + temporary.toAbsolutePath());
        if (mode == Mode.LINKED) {
            arguments.add("-XX:+AOTClassLinking");
        }
        arguments.addAll(options.dumpOptions());
        arguments.add("-cp");
        arguments.add(jars.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)));
        logger.info("Dumping a {} CDS archive of {} dependency JARs", mode.id(), jars.size());
        long start = System.nanoTime();
        try {
            boolean dumped = run(keyDirectory, "dump-" + mode.id(), arguments, DUMP_TIMEOUT_SECONDS)
                && Files.isRegularFile(temporary) && Files.size(temporary) > 0;
            if (!dumped) {
                return false;
            }
            logger.info("Dumped the {} CDS archive ({} MB) in {} ms", mode.id(), Files.size(temporary) / (1024 * 1024), elapsedMillis(start));
            try {
                Files.move(temporary, archive, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException e) {
                // a concurrent run dumped it first
            }
            return true;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private boolean probe(Path keyDirectory, Mode mode, RunCdsJvmOptions options, Path archive, List<File> launchClasspath) throws IOException {
        var arguments = new ArrayList<String>();
        arguments.add("-Xshare:on");
        arguments.add("-XX:+VerifySharedSpaces");
        arguments.add("-XX:SharedArchiveFile=" + archive.toAbsolutePath());
        arguments.addAll(options.probeOptions());
        arguments.add("-cp");
        arguments.add(launchClasspath.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)));
        arguments.add("-version");
        logger.info("Probing the {} CDS archive", mode.id());
        long start = System.nanoTime();
        String name = "probe-" + mode.id();
        if (!run(keyDirectory, name, arguments, PROBE_TIMEOUT_SECONDS)) {
            return false;
        }
        // with some options, such as --limit-modules, the JVM turns sharing off without failing
        String output = Files.readString(keyDirectory.resolve(name + ".log"), StandardCharsets.UTF_8);
        boolean sharing = output.contains(", sharing");
        logger.info("Probed the {} CDS archive in {} ms: {}", mode.id(), elapsedMillis(start), sharing ? "sharing" : "not sharing");
        return sharing;
    }

    /**
     * Runs the launcher's {@code java} with an argument file, without the options variables of the
     * environment, and writes its output to a log file.
     */
    private boolean run(Path directory, String name, List<String> arguments, long timeoutSeconds) throws IOException {
        Path argumentFile = directory.resolve(name + ".args");
        Files.write(argumentFile, arguments.stream().map(RunCdsArchive::quote).toList(), StandardCharsets.UTF_8);
        Path log = directory.resolve(name + ".log");
        var builder = new ProcessBuilder(java.toString(), "@" + argumentFile.toAbsolutePath())
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile());
        Map<String, String> processEnvironment = builder.environment();
        processEnvironment.clear();
        processEnvironment.putAll(environment);
        RunCdsJvmOptions.OPTION_VARIABLES.forEach(processEnvironment::remove);
        Process process = builder.start();
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                Files.writeString(log, System.lineSeparator() + "Stopped after " + timeoutSeconds + " seconds" + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                return false;
            }
            return process.exitValue() == 0;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while running " + log.getFileName());
        }
    }

    /**
     * Reads the index of the dependency JARs' entry names, and writes it first if it does not exist.
     */
    private Set<String> index() throws IOException {
        Files.createDirectories(dependencyDirectory);
        Path indexFile = dependencyDirectory.resolve(INDEX_FILE);
        if (Files.isRegularFile(indexFile)) {
            return new HashSet<>(Files.readAllLines(indexFile, StandardCharsets.UTF_8));
        }
        var names = new TreeSet<String>();
        for (File jar : jars) {
            try (var zip = new ZipFile(jar)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && !isMergedOrMetadata(entry.getName())) {
                        names.add(entry.getName());
                    }
                }
            }
        }
        String content = names.isEmpty() ? "" : String.join("\n", names) + "\n";
        writeAtomically(indexFile, content.getBytes(StandardCharsets.UTF_8));
        return new HashSet<>(names);
    }

    /**
     * Deletes the files of other dependency sets, which no launch uses any more. A file that cannot be
     * deleted, such as an archive that a running application has mapped on Windows, stays.
     */
    private void deleteOtherDependencySets() {
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(root)) {
            for (Path directory : directories) {
                if (Files.isDirectory(directory) && !directory.equals(dependencyDirectory)) {
                    deleteRecursively(directory);
                }
            }
        } catch (IOException e) {
            logger.debug("Could not delete the previous CDS archives", e);
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    // still in use
                }
            });
        }
    }

    /**
     * Turns a recording into the class list. The last line is dropped when the recording ends in the
     * middle of it, as it does when the application was killed.
     *
     * @param recording the recording
     * @param classList the class list
     * @return true if the recording had at least one line
     * @throws IOException if the recording cannot be read or the class list cannot be written
     */
    static boolean completeRecording(Path recording, Path classList) throws IOException {
        if (!Files.isRegularFile(recording)) {
            return false;
        }
        byte[] bytes = Files.readAllBytes(recording);
        int end = bytes.length;
        while (end > 0 && bytes[end - 1] != '\n') {
            end--;
        }
        if (end == 0) {
            return false;
        }
        writeAtomically(classList, Arrays.copyOf(bytes, end));
        try {
            Files.deleteIfExists(recording);
        } catch (IOException e) {
            // still open on Windows
        }
        return true;
    }

    /**
     * Whether a path is merged or metadata, so that its copies in the project and in a dependency do not
     * shadow one another in a way that matters: the manifest, service and Micronaut metadata files,
     * module descriptors and versioned entries, signatures, Maven metadata, and licence and notice files.
     *
     * @param name the path, with {@code /} separators
     * @return true if the duplicate check skips it
     */
    static boolean isMergedOrMetadata(String name) {
        if (name.equals("module-info.class") || name.endsWith("/module-info.class")) {
            return true;
        }
        int lastSlash = name.lastIndexOf('/');
        String fileName = name.substring(lastSlash + 1).toUpperCase(Locale.ROOT);
        boolean licenseOrNotice = fileName.startsWith("LICENSE") || fileName.startsWith("LICENCE") || fileName.startsWith("NOTICE");
        if (!name.startsWith("META-INF/")) {
            return lastSlash < 0 && licenseOrNotice;
        }
        if (name.startsWith("META-INF/services/")
            || name.startsWith("META-INF/micronaut/")
            || name.startsWith("META-INF/versions/")
            || name.startsWith("META-INF/maven/")) {
            return true;
        }
        if (lastSlash == "META-INF".length()) {
            if (fileName.equals("MANIFEST.MF") || fileName.equals("INDEX.LIST") || fileName.equals("DEPENDENCIES")
                || fileName.endsWith(".SF") || fileName.endsWith(".RSA") || fileName.endsWith(".DSA")
                || fileName.endsWith(".EC") || fileName.startsWith("SIG-")) {
                return true;
            }
        }
        return licenseOrNotice;
    }

    /**
     * Quotes an argument for an argument file of the {@code java} launcher.
     */
    static String quote(String argument) {
        boolean plain = !argument.isEmpty() && argument.chars().noneMatch(c -> Character.isWhitespace(c) || c == '"' || c == '\'' || c == '\\' || c == '#');
        if (plain) {
            return argument;
        }
        return '"' + argument.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static boolean isZip(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".jar") || name.endsWith(".zip");
    }

    private static String fileIdentity(File file) {
        return file.getAbsolutePath() + "\t" + file.length() + "\t" + file.lastModified();
    }

    private static void writeAtomically(Path target, byte[] content) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try (OutputStream out = Files.newOutputStream(temporary)) {
            out.write(content);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static long elapsedMillis(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    static String sha256(List<String> lines) {
        return sha256(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
