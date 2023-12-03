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
package io.micronaut.gradle;

import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class LenientGradle {
    private static final Map<String, Optional<Class<?>>> CLASSES = new ConcurrentHashMap<>();

    private LenientGradle() {
    }

    private static Optional<Class<?>> findClass(String name) {
        return CLASSES.computeIfAbsent(name, LenientGradle::loadClass);
    }

    private static Optional<Class<?>> loadClass(String name) {
        try {
            return Optional.of(Class.forName(name));
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return Optional.empty();
        }
    }

    public static <T> void ifTypePresent(String typeName, Consumer<? super Class<T>> consumer) {
        //noinspection unchecked
        findClass(typeName).ifPresent(c -> consumer.accept((Class<T>) c));
    }

    /**
     * An alternative method call to {@link DomainObjectCollection#withType(Class, Action)} which
     * will not require the class to be present on classpath.
     *
     * @param collection the domain object collection
     * @param name the name of the type to look for
     * @param consumer the consumer
     * @param <T> the type of the collection
     */
    public static <T> void withType(DomainObjectCollection<T> collection, String name, Action<? super T> consumer) {
        //noinspection unchecked
        findClass(name).ifPresent(clazz -> collection.withType((Class<T>) clazz, consumer));
    }
}
