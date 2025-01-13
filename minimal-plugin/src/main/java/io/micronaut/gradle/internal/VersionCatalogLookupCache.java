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
package io.micronaut.gradle.internal;

import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.provider.Provider;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class is used as a workaround for very poor performance
 * of version catalog scanning in Gradle.
 * See <a href="https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/932">Issue</a>
 * and <a href="https://github.com/gradle/gradle/pull/27444/files">PR</a>
 */
class VersionCatalogLookupCache implements AutoCloseable {
    private final AtomicReference<VersionCatalog> oldCatalog = new AtomicReference<>();
    private final Map<String, Provider<MinimalExternalModuleDependency>> coordsToDependencyProvider = new ConcurrentHashMap<>();

    static VersionCatalogLookupCache get() {
        class Holder {
            private static final VersionCatalogLookupCache INSTANCE = new VersionCatalogLookupCache();
        }
        return Holder.INSTANCE;
    }

    private VersionCatalogLookupCache() {

    }

    void clear() {
        oldCatalog.set(null);
        coordsToDependencyProvider.clear();
    }

    private void memoize(VersionCatalog catalog) {
        if (oldCatalog.compareAndSet(null, catalog)) {
            coordsToDependencyProvider.clear();
            catalog.getLibraryAliases()
                .stream()
                .parallel()
                .forEach(alias -> {
                    var library = catalog.findLibrary(alias);
                    library.ifPresent(lib ->
                        coordsToDependencyProvider.put(lib.get().getModule().toString(), lib)
                    );
                });
        }
    }

    public Optional<Provider<MinimalExternalModuleDependency>> findDependencyFromCatalog(VersionCatalog catalog, String coordinates) {
        memoize(catalog);
        return Optional.ofNullable(coordsToDependencyProvider.get(coordinates));
    }

    @Override
    public void close() {
        coordsToDependencyProvider.clear();
    }
}
