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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The JVM options of a {@code run} launch, as far as its CDS archive depends on them: the JVM arguments
 * of the task, and the options that its environment adds through {@code JAVA_TOOL_OPTIONS},
 * {@code JDK_JAVA_OPTIONS} and {@code _JAVA_OPTIONS}.
 */
final class RunCdsJvmOptions {

    /**
     * The environment variables whose options the JVM, or the {@code java} launcher, adds to a launch,
     * in the order in which they apply around the command line ({@code _JAVA_OPTIONS} comes after it).
     * The dump and the probe run without them, so that an agent or a debugger set there does not load
     * into them.
     */
    static final List<String> OPTION_VARIABLES = List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS");

    /**
     * The module that HotSpot adds to the module graph of a launch with any
     * {@code -Dcom.sun.management.*} system property.
     */
    static final String JMX_AGENT_MODULE = "jdk.management.agent";

    /**
     * The module that HotSpot adds to the module graph of a launch while JVMCI is enabled, which is the
     * default of GraalVM JDKs. A dump disables JVMCI, so it has to add the module itself.
     */
    static final String JVMCI_MODULE = "jdk.internal.vm.ci";

    /**
     * The module options that take a value, as {@code --option=value} or as {@code --option value}.
     */
    private static final Set<String> MODULE_OPTIONS = Set.of(
        "--add-modules",
        "--limit-modules",
        "--upgrade-module-path",
        "--module-path",
        "--patch-module",
        "--add-opens",
        "--add-exports",
        "--add-reads",
        "--enable-native-access"
    );

    /**
     * The module options with which the JVM cannot use an AOT-linked archive: they change the module
     * graph that the archive was linked against, and the JVM then turns all CDS off.
     */
    private static final Set<String> AOT_LINKING_BLOCKERS = Set.of(
        "--limit-modules",
        "--upgrade-module-path",
        "--module-path",
        "--patch-module",
        "--add-opens",
        "--add-exports",
        "--add-reads"
    );

    /**
     * The {@code -XX} flags that configure CDS or the JDK AOT cache: a launch that has one of them manages
     * its own archive.
     */
    private static final Set<String> CDS_FLAGS = Set.of(
        "SharedArchiveFile",
        "SharedClassListFile",
        "DumpLoadedClassList",
        "ArchiveClassesAtExit",
        "AutoCreateSharedArchive",
        "RecordDynamicDumpInfo",
        "UseSharedSpaces",
        "RequireSharedSpaces",
        "AOTCache",
        "AOTCacheOutput",
        "AOTConfiguration",
        "AOTMode"
    );

    /**
     * The {@code -XX} flags that the archive does not depend on, and that the dump and the probe leave out:
     * flight recordings, error and output handling, and the flags that the dump and the probe set themselves.
     */
    private static final Set<String> UNRELATED_FLAGS = Set.of(
        "AOTClassLinking",
        "VerifySharedSpaces",
        "StartFlightRecording",
        "FlightRecorder",
        "FlightRecorderOptions",
        "OnError",
        "OnOutOfMemoryError",
        "ErrorFile",
        "HeapDumpOnOutOfMemoryError",
        "HeapDumpPath",
        "LogFile",
        "LogVMOutput",
        "DisplayVMOutputToStderr",
        "DisplayVMOutputToStdout",
        "PrintFlagsFinal",
        "PrintFlagsInitial",
        "PrintCommandLineFlags",
        "PrintVMOptions",
        "CRaCCheckpointTo",
        "CRaCRestoreFrom"
    );

    private final List<String> options;
    private final boolean jvmci;

    private RunCdsJvmOptions(List<String> options, boolean jvmci) {
        this.options = options;
        this.jvmci = jvmci;
    }

    /**
     * Collects the options of a launch.
     *
     * @param jvmArguments the JVM arguments of the task
     * @param environment the environment of the task
     * @param javaRuntimeVersion the {@code java.runtime.version} of the launcher
     * @return the options
     */
    static RunCdsJvmOptions of(List<String> jvmArguments, Map<String, ?> environment, String javaRuntimeVersion) {
        var raw = new ArrayList<String>();
        raw.addAll(environmentOptions(environment, "JAVA_TOOL_OPTIONS"));
        raw.addAll(environmentOptions(environment, "JDK_JAVA_OPTIONS"));
        raw.addAll(jvmArguments);
        raw.addAll(environmentOptions(environment, "_JAVA_OPTIONS"));
        List<String> options = normalize(raw);
        boolean jvmci = javaRuntimeVersion != null && javaRuntimeVersion.contains("jvmci");
        for (String option : options) {
            if (option.equals("-XX:+EnableJVMCI") || option.equals("-XX:+UseJVMCICompiler")) {
                jvmci = true;
            } else if (option.equals("-XX:-EnableJVMCI")) {
                jvmci = false;
            }
        }
        return new RunCdsJvmOptions(options, jvmci);
    }

    /**
     * Whether the launch already configures CDS or the JDK AOT cache itself, or turns sharing off.
     *
     * @return true if the launch manages its own archive
     */
    boolean configuresClassDataSharing() {
        for (String option : options) {
            if (option.startsWith("-Xshare:") && !option.equals("-Xshare:auto")) {
                return true;
            }
            String flag = flagName(option);
            if (flag != null && CDS_FLAGS.contains(flag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Why an AOT-linked archive cannot be used with this launch.
     *
     * @return what rules it out, or null if nothing does
     */
    String aotClassLinkingBlocker() {
        for (String option : options) {
            if (option.startsWith("-agentlib:jdwp") || option.startsWith("-Xrunjdwp") || option.equals("-Xdebug")) {
                return "the launch has a debugger (JDWP)";
            }
            if (option.startsWith("-javaagent:") || option.startsWith("-agentlib:") || option.startsWith("-agentpath:") || option.startsWith("-Xrun")) {
                return "the launch has an agent (" + optionName(option, ':') + ")";
            }
            String name = optionName(option, '=');
            if (AOT_LINKING_BLOCKERS.contains(name) || name.startsWith("-Djdk.module.")) {
                return "the launch has " + name;
            }
        }
        return null;
    }

    /**
     * Whether the launch configures logging of the {@code cds} or {@code aot} tags, in which case the
     * {@code run} task does not silence them.
     *
     * @return true if the launch logs CDS
     */
    boolean logsClassDataSharing() {
        for (String option : options) {
            if (option.equals("-Xlog")) {
                return true;
            }
            if (option.startsWith("-Xlog:")) {
                String selection = option.substring("-Xlog:".length());
                int output = selection.indexOf(':');
                if (output >= 0) {
                    selection = selection.substring(0, output);
                }
                if (selection.contains("cds") || selection.contains("aot") || selection.contains("all")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The modules that the dump adds to its module graph, so that it matches the launch's: the
     * {@code --add-modules} of the launch, the JMX agent while the launch has a
     * {@code -Dcom.sun.management.*} property, and the JVMCI module while the launcher enables JVMCI.
     *
     * @return the modules, in order
     */
    Set<String> dumpModules() {
        Set<String> modules = addedModules();
        if (jvmci) {
            modules.add(JVMCI_MODULE);
        }
        return modules;
    }

    /**
     * The options of the dump, besides the ones that make it a dump: the modules of
     * {@link #dumpModules()}, and the options the archive must agree on with the launch (collector,
     * compressed oops and object headers, maximum heap size, boot class path, preview features). It has no
     * system property: with {@code -Dcom.sun.management.*}, the dump would start the JMX agent.
     *
     * @return the options of the dump
     */
    List<String> dumpOptions() {
        var result = new ArrayList<String>();
        Set<String> modules = dumpModules();
        if (!modules.isEmpty()) {
            result.add("--add-modules=" + String.join(",", modules));
        }
        for (String option : options) {
            String flag = flagName(option);
            if (flag != null) {
                // a dump disables JVMCI, and fails with JVMCI flags
                if (isArchiveFlag(flag) && !flag.contains("JVMCI")) {
                    result.add(option);
                }
            } else if (isHeapOrBootOption(option)) {
                result.add(option);
            }
        }
        return result;
    }

    /**
     * The options of the probe, besides the ones that make it a probe: the module options of the launch,
     * with {@code --add-modules=jdk.management.agent} in place of the {@code -Dcom.sun.management.*}
     * properties, and its {@code -XX}, heap and boot class path options. It has no system property and no
     * agent.
     *
     * @return the options of the probe
     */
    List<String> probeOptions() {
        var result = new ArrayList<String>();
        Set<String> modules = addedModules();
        if (!modules.isEmpty()) {
            result.add("--add-modules=" + String.join(",", modules));
        }
        for (String option : options) {
            String flag = flagName(option);
            if (flag != null) {
                if (isArchiveFlag(flag)) {
                    result.add(option);
                }
            } else if (isHeapOrBootOption(option)) {
                result.add(option);
            } else {
                String name = optionName(option, '=');
                if (MODULE_OPTIONS.contains(name) && !name.equals("--add-modules")) {
                    result.add(option);
                }
            }
        }
        return result;
    }

    /**
     * The options that the archive key covers: the dump's modules and the probe's options.
     *
     * @return the options of the key
     */
    List<String> keyOptions() {
        var result = new ArrayList<String>();
        result.add("modules=" + String.join(",", new TreeSet<>(dumpModules())));
        result.addAll(probeOptions());
        return result;
    }

    private Set<String> addedModules() {
        var modules = new LinkedHashSet<String>();
        boolean jmx = false;
        for (String option : options) {
            if (option.startsWith("--add-modules=")) {
                for (String module : option.substring("--add-modules=".length()).split(",")) {
                    if (!module.isBlank()) {
                        modules.add(module.trim());
                    }
                }
            } else if (option.startsWith("-Dcom.sun.management.")) {
                jmx = true;
            }
        }
        if (jmx) {
            modules.add(JMX_AGENT_MODULE);
        }
        return modules;
    }

    private static boolean isArchiveFlag(String flag) {
        return !CDS_FLAGS.contains(flag) && !UNRELATED_FLAGS.contains(flag);
    }

    private static boolean isHeapOrBootOption(String option) {
        return option.startsWith("-Xmx") || option.startsWith("-Xbootclasspath/a:") || option.equals("--enable-preview");
    }

    /**
     * The name of a {@code -XX} flag, without its sign or value.
     *
     * @param option the option
     * @return the flag name, or null if the option is not a {@code -XX} flag
     */
    static String flagName(String option) {
        if (!option.startsWith("-XX:")) {
            return null;
        }
        String flag = option.substring("-XX:".length());
        if (flag.startsWith("+") || flag.startsWith("-")) {
            flag = flag.substring(1);
        }
        int value = flag.indexOf('=');
        return value < 0 ? flag : flag.substring(0, value);
    }

    private static String optionName(String option, char separator) {
        int index = option.indexOf(separator);
        return index < 0 ? option : option.substring(0, index);
    }

    private static List<String> environmentOptions(Map<String, ?> environment, String variable) {
        Object value = environment.get(variable);
        return value == null ? List.of() : tokenize(value.toString());
    }

    /**
     * Writes the module options that take a separate value as {@code --option=value}, and {@code -p} as
     * {@code --module-path}.
     */
    private static List<String> normalize(List<String> raw) {
        var result = new ArrayList<String>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            String option = raw.get(i);
            String name = option.equals("-p") ? "--module-path" : option;
            if (MODULE_OPTIONS.contains(name) && i + 1 < raw.size()) {
                i++;
                result.add(name + "=" + raw.get(i));
            } else {
                result.add(option);
            }
        }
        return result;
    }

    /**
     * Splits the value of an options variable, as the JVM does: on white space, with single or double
     * quotes around a value that contains some.
     *
     * @param value the value of the variable
     * @return the options
     */
    static List<String> tokenize(String value) {
        var tokens = new ArrayList<String>();
        var current = new StringBuilder();
        boolean inToken = false;
        char quote = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                inToken = true;
            } else if (Character.isWhitespace(c)) {
                if (inToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
            } else {
                current.append(c);
                inToken = true;
            }
        }
        if (inToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
