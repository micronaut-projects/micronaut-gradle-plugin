package io.micronaut.gradle.graalvm;

import org.gradle.api.JavaVersion;

import java.util.Locale;

/**
 * Utilities for GraalVM.
 */
public final class GraalUtil {
    public static final int SHARED_ARENA_SUPPORT_MINIMUM_JAVA_VERSION = 25;

    private GraalUtil() {
    }

    /**
     * @return Return whether the JVM in use a GraalVM JVM.
     */
    public static boolean isGraalJVM() {
        return isGraal("jvmci.Compiler", "java.vendor.version", "java.vendor");
    }

    /**
     * @return The current JVM major version.
     */
    public static int currentJavaMajorVersion() {
        return Integer.parseInt(JavaVersion.current().getMajorVersion());
    }

    /**
     * @param javaVersion The Java major version.
     * @return Whether {@code -H:+SharedArenaSupport} is supported.
     */
    public static boolean supportsSharedArenaSupport(int javaVersion) {
        return javaVersion >= SHARED_ARENA_SUPPORT_MINIMUM_JAVA_VERSION;
    }

    /**
     * @param javaVersion The Java version string.
     * @return Whether {@code -H:+SharedArenaSupport} is supported.
     */
    public static boolean supportsSharedArenaSupport(String javaVersion) {
        return supportsSharedArenaSupport(parseMajorVersion(javaVersion));
    }

    /**
     * @param javaVersion The Java version string.
     * @return The Java major version.
     */
    public static int parseMajorVersion(String javaVersion) {
        if (javaVersion.contains(".")) {
            return Integer.parseInt(javaVersion.substring(0, javaVersion.indexOf('.')));
        }
        return Integer.parseInt(javaVersion);
    }

    private static boolean isGraal(String... props) {
        for (String prop : props) {
            String vv = System.getProperty(prop);
            if (vv != null && vv.toLowerCase(Locale.ENGLISH).contains("graal")) {
                return true;
            }
        }
        return false;
    }
}
