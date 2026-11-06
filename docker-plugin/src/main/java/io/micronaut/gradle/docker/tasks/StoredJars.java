/*
 * Copyright 2003-2022 the original author or authors.
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
package io.micronaut.gradle.docker.tasks;

import org.gradle.api.UncheckedIOException;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Rewrites a JAR with every entry STORED, keeping the order, names, times, extra fields and comments of its
 * entries, so that the same JAR always gives the same bytes.
 */
final class StoredJars {

    private StoredJars() {
    }

    /**
     * Rewrites a JAR in place with every entry STORED. A signed JAR, and a JAR whose entries are all STORED
     * already, are left as they are.
     *
     * @param jar the JAR
     */
    static void store(Path jar) {
        Path stored = jar.resolveSibling(jar.getFileName() + ".stored");
        try {
            try (var zip = new ZipFile(jar.toFile())) {
                if (!needsRewrite(zip)) {
                    return;
                }
                try (var out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(stored)))) {
                    if (zip.getComment() != null) {
                        out.setComment(zip.getComment());
                    }
                    Set<String> written = new HashSet<>();
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        if (written.add(name)) {
                            // For a name that occurs twice, the entry that a lookup by name returns
                            write(zip, zip.getEntry(name), out);
                        }
                    }
                }
            }
            Files.move(stored, jar, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to store the entries of " + jar, e);
        } finally {
            try {
                Files.deleteIfExists(stored);
            } catch (IOException e) {
                // the move failed already
            }
        }
    }

    private static boolean needsRewrite(ZipFile zip) {
        boolean deflated = false;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (isSignature(entry.getName())) {
                return false;
            }
            deflated |= entry.getMethod() != ZipEntry.STORED;
        }
        return deflated;
    }

    private static boolean isSignature(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("META-INF/") || upper.indexOf('/', "META-INF/".length()) >= 0) {
            return false;
        }
        return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC");
    }

    private static void write(ZipFile zip, ZipEntry source, ZipOutputStream out) throws IOException {
        byte[] content;
        try (InputStream in = zip.getInputStream(source)) {
            content = in.readAllBytes();
        }
        var crc = new CRC32();
        crc.update(content);
        var entry = new ZipEntry(source.getName());
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(content.length);
        entry.setCompressedSize(content.length);
        entry.setCrc(crc.getValue());
        entry.setTime(source.getTime());
        if (source.getExtra() != null) {
            entry.setExtra(source.getExtra());
        }
        if (source.getComment() != null) {
            entry.setComment(source.getComment());
        }
        out.putNextEntry(entry);
        out.write(content);
        out.closeEntry();
    }
}
