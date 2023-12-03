/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle.catalog;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A model of a TOML file which can be used to update the file
 * automatically.
 */
public class VersionCatalogTomlModel {
    private final Set<Library> libraries = new HashSet<>();
    private final Set<VersionModel> versions = new HashSet<>();
    private final Map<String, Library> gaToLibrary = new HashMap<>();
    private final Map<String, VersionModel> versionAliasToVersion = new HashMap<>();

    /**
     * Given a version alias found in the "[versions]" section, stores
     * a list of libraries found in the "[libraries]" section.
     */
    private final Map<String, Set<Library>> versionAliasToModules = new HashMap<>();

    public void addLibrary(Library library) {
        libraries.add(library);
        gaToLibrary.put(library.getModule(), library);
        String versionRef = library.getVersion().getReference();
        if (versionRef != null) {
            Set<Library> librariesByVersionRef = versionAliasToModules.computeIfAbsent(versionRef, e -> new HashSet<>());
            librariesByVersionRef.add(library);
        }
    }

    public void addVersion(VersionModel version) {
        if (version.getReference() == null || version.getVersion() == null) {
            throw new IllegalArgumentException("version.getReference() is null or version.getVersion() is null");
        }
        versions.add(version);
        versionAliasToVersion.put(version.getReference(), version);
    }

    public Optional<Library> findLibrary(String group, String name) {
        return Optional.ofNullable(gaToLibrary.get(group + ":" + name));
    }

    public Optional<VersionModel> findVersion(String alias) {
        return Optional.ofNullable(versionAliasToVersion.get(alias));
    }

    public Set<Library> findLibrariesForVersionReference(String versionRef) {
        return versionAliasToModules.getOrDefault(versionRef, Collections.emptySet());
    }

    public Set<Library> getLibrariesTable() {
        return libraries;
    }

    public Set<VersionModel> getVersionsTable() {
        return versions;
    }
}
