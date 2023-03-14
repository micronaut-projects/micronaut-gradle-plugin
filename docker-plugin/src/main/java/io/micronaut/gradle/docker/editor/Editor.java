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
package io.micronaut.gradle.docker.editor;

import org.gradle.api.Action;

/**
 * API for simple edition of text files like docker files.
 */
public interface Editor {
    /**
     * Restricts edition to lines after the given line.
     * @param line the marker line
     * @param action the action to execute
     */
    void after(String line, Action<? super Editor> action);

    /**
     * Restricts edition to lines before the given line.
     * @param line the marker line
     * @param action the action to execute
     */
    void before(String line, Action<? super Editor> action);

    /**
     * Replaces lines matching the current lower and upper bounds with the
     * provided replacement.
     *
     * @param replacement the replacement
     */
    void replace(String replacement);

    /**
     * Replaces lines matching the current lower and upper bounds with the
     * replacement, if they match the line pattern.
     *
     * @param line the line pattern
     * @param replacement the replacement
     */
    void replace(String line, String replacement);

    /**
     * Inserts the provided lines after the current lower bound.
     * @param lines the lines to be inserted
     */
    void insert(String... lines);
}
