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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class OpenApiReferenceResolver {

    private static final Pattern URI_SCHEME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:.*$");
    private static final Pattern WINDOWS_ABSOLUTE_PATH_PATTERN = Pattern.compile("^[A-Za-z]:[\\\\/].*$");

    private OpenApiReferenceResolver() {
    }

    static List<File> referencedFiles(File definitionFile, Iterable<File> parserClasspath) {
        if (definitionFile == null) {
            return List.of();
        }
        var discoveredFiles = new LinkedHashSet<File>();
        try (var parser = OpenApiDocumentParser.create(parserClasspath)) {
            collectReferencedFiles(normalize(definitionFile.toPath()), parser, discoveredFiles, new HashSet<>());
        } catch (IOException ignored) {
            return List.of();
        }
        return List.copyOf(discoveredFiles);
    }

    private static void collectReferencedFiles(Path definitionFile, OpenApiDocumentParser parser, Set<File> discoveredFiles, Set<Path> visitedFiles) {
        if (!Files.isRegularFile(definitionFile) || !visitedFiles.add(definitionFile)) {
            return;
        }
        String content;
        try {
            content = Files.readString(definitionFile, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return;
        }
        Object document = parser.parseStructuredDocument(definitionFile, content);
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
            collectReferencedFiles(resolvedReference, parser, discoveredFiles, visitedFiles);
        }
    }

    private static List<String> findReferences(Object node) {
        var references = new LinkedHashSet<String>();
        collectReferences(node, references, Collections.newSetFromMap(new IdentityHashMap<>()));
        return List.copyOf(references);
    }

    private static void collectReferences(Object node, Set<String> references, Set<Object> visitedNodes) {
        if (node == null || OpenApiDocumentParser.isNull(node)) {
            return;
        }
        if (OpenApiDocumentParser.isContainerNode(node) && !visitedNodes.add(node)) {
            return;
        }
        if (OpenApiDocumentParser.isObject(node)) {
            for (Object entry : OpenApiDocumentParser.properties(node)) {
                Object value = OpenApiDocumentParser.entryValue(entry);
                if ("$ref".equals(OpenApiDocumentParser.entryKey(entry)) && OpenApiDocumentParser.isTextual(value)) {
                    references.add(OpenApiDocumentParser.textValue(value));
                }
                collectReferences(value, references, visitedNodes);
            }
            return;
        }
        if (OpenApiDocumentParser.isArray(node)) {
            for (Object value : OpenApiDocumentParser.elements(node)) {
                collectReferences(value, references, visitedNodes);
            }
        }
    }

    private static boolean isJson(Path definitionFile) {
        String fileName = definitionFile.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".json");
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

    private static final class OpenApiDocumentParser implements AutoCloseable {

        private final URLClassLoader classLoader;
        private final Class<?> jsonClass;
        private final Class<?> deserializationUtilsClass;
        private final Class<?> parseOptionsClass;
        private final Class<?> swaggerParseResultClass;

        private OpenApiDocumentParser(URLClassLoader classLoader) throws ClassNotFoundException {
            this.classLoader = classLoader;
            jsonClass = classLoader.loadClass("io.swagger.v3.core.util.Json");
            deserializationUtilsClass = classLoader.loadClass("io.swagger.v3.parser.util.DeserializationUtils");
            parseOptionsClass = classLoader.loadClass("io.swagger.v3.parser.core.models.ParseOptions");
            swaggerParseResultClass = classLoader.loadClass("io.swagger.v3.parser.core.models.SwaggerParseResult");
        }

        static OpenApiDocumentParser create(Iterable<File> parserClasspath) throws IOException {
            var urls = new LinkedHashSet<URL>();
            for (File file : parserClasspath) {
                urls.add(file.toURI().toURL());
            }
            try {
                return new OpenApiDocumentParser(new URLClassLoader(urls.toArray(URL[]::new), OpenApiReferenceResolver.class.getClassLoader()));
            } catch (ClassNotFoundException e) {
                try {
                    return new OpenApiDocumentParser(new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader()));
                } catch (ClassNotFoundException ignored) {
                    throw new IOException("The OpenAPI parser is not available on the generator classpath", e);
                }
            }
        }

        Object parseStructuredDocument(Path definitionFile, String content) {
            try {
                if (isJson(definitionFile)) {
                    Object mapper = jsonClass.getMethod("mapper").invoke(null);
                    return mapper.getClass().getMethod("readTree", String.class).invoke(mapper, content);
                }
                Object parseResult = swaggerParseResultClass.getConstructor().newInstance();
                Object document = deserializationUtilsClass.getMethod("readYamlTree", String.class, parseOptionsClass, swaggerParseResultClass)
                    .invoke(null, content, null, parseResult);
                List<?> messages = (List<?>) swaggerParseResultClass.getMethod("getMessages").invoke(parseResult);
                if (messages != null && messages.stream().anyMatch(message -> message.toString().contains("Global tag is not allowed"))) {
                    return null;
                }
                return document;
            } catch (IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException | RuntimeException ignored) {
                return null;
            }
        }

        static boolean isNull(Object node) {
            return invokeBoolean(node, "isNull");
        }

        static boolean isContainerNode(Object node) {
            return isObject(node) || isArray(node);
        }

        static boolean isObject(Object node) {
            return invokeBoolean(node, "isObject");
        }

        static boolean isArray(Object node) {
            return invokeBoolean(node, "isArray");
        }

        static boolean isTextual(Object node) {
            return invokeBoolean(node, "isTextual");
        }

        static Iterable<?> properties(Object node) {
            return (Iterable<?>) invoke(node, "properties");
        }

        static Iterable<?> elements(Object node) {
            return (Iterable<?>) node;
        }

        static Object entryKey(Object entry) {
            return ((Map.Entry<?, ?>) entry).getKey();
        }

        static Object entryValue(Object entry) {
            return ((Map.Entry<?, ?>) entry).getValue();
        }

        static String textValue(Object node) {
            return (String) invoke(node, "textValue");
        }

        private static boolean invokeBoolean(Object target, String methodName) {
            return (Boolean) invoke(target, methodName);
        }

        private static Object invoke(Object target, String methodName) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() throws IOException {
            classLoader.close();
        }
    }
}
