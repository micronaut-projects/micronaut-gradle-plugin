/*
 * Copyright 2003-2021 the original author or authors.
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
package io.micronaut.gradle.openapi.tasks;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

final class OpenApiReferenceResolver {

    private static final Pattern URI_SCHEME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:.*$");
    private static final Pattern WINDOWS_ABSOLUTE_PATH_PATTERN = Pattern.compile("^[A-Za-z]:[\\\\/].*$");

    private OpenApiReferenceResolver() {
    }

    static List<File> referencedFiles(File definitionFile) {
        if (definitionFile == null) {
            return List.of();
        }
        var discoveredFiles = new LinkedHashSet<File>();
        collectReferencedFiles(normalize(definitionFile.toPath()), discoveredFiles, new HashSet<>());
        return List.copyOf(discoveredFiles);
    }

    private static void collectReferencedFiles(Path definitionFile, Set<File> discoveredFiles, Set<Path> visitedFiles) {
        if (!Files.isRegularFile(definitionFile) || !visitedFiles.add(definitionFile)) {
            return;
        }
        String content;
        try {
            content = Files.readString(definitionFile, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return;
        }
        Object document = parseStructuredDocument(content);
        if (document == null) {
            return;
        }
        Path baseDirectory = definitionFile.getParent();
        for (String reference : findReferences(document)) {
            Path resolvedReference = resolveLocalReference(baseDirectory, reference);
            if (resolvedReference == null || !Files.isRegularFile(resolvedReference)) {
                continue;
            }
            discoveredFiles.add(resolvedReference.toFile());
            collectReferencedFiles(resolvedReference, discoveredFiles, visitedFiles);
        }
    }

    private static Object parseStructuredDocument(String content) {
        try {
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(content);
        } catch (YAMLException ignored) {
            return null;
        }
    }

    private static List<String> findReferences(Object node) {
        var references = new LinkedHashSet<String>();
        collectReferences(node, references, Collections.newSetFromMap(new IdentityHashMap<>()));
        return List.copyOf(references);
    }

    private static void collectReferences(Object node, Set<String> references, Set<Object> visitedNodes) {
        if (node instanceof Map<?, ?> map) {
            if (!visitedNodes.add(node)) {
                return;
            }
            for (var entry : map.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                if ("$ref".equals(key) && value instanceof String reference) {
                    references.add(reference);
                }
                collectReferences(value, references, visitedNodes);
            }
            return;
        }
        if (node instanceof Iterable<?> iterable) {
            if (!visitedNodes.add(node)) {
                return;
            }
            for (Object value : iterable) {
                collectReferences(value, references, visitedNodes);
            }
        }
    }

    private static Path resolveLocalReference(Path baseDirectory, String reference) {
        String referenceLocation = stripFragment(reference);
        if (referenceLocation.isEmpty()) {
            return null;
        }
        if (isWindowsAbsolutePath(referenceLocation)) {
            return normalize(Path.of(referenceLocation));
        }
        if (URI_SCHEME_PATTERN.matcher(referenceLocation).matches()) {
            if (!referenceLocation.regionMatches(true, 0, "file:", 0, 5)) {
                return null;
            }
            try {
                return normalize(Path.of(URI.create(referenceLocation)));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        Path effectiveBaseDirectory = baseDirectory == null ? Path.of(".") : baseDirectory;
        return normalize(effectiveBaseDirectory.resolve(referenceLocation));
    }

    private static String stripFragment(String reference) {
        int fragmentSeparator = reference.indexOf('#');
        String withoutFragment = fragmentSeparator >= 0 ? reference.substring(0, fragmentSeparator) : reference;
        return withoutFragment.trim();
    }

    static boolean isWindowsAbsolutePath(String referenceLocation) {
        return WINDOWS_ABSOLUTE_PATH_PATTERN.matcher(referenceLocation).matches();
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
