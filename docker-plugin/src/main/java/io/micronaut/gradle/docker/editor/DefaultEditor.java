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
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class DefaultEditor implements Editor {
    private final ObjectFactory objects;
    private final List<String> lines;
    private final DefaultEditor parent;
    private final String before;
    private final String after;

    // We use Optional because Gradle doesn't support injecting null values
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Inject
    public DefaultEditor(ObjectFactory objects,
                         Optional<DefaultEditor> parent,
                         List<String> lines,
                         Optional<String> before,
                         Optional<String> after) {
        this.objects = objects;
        this.parent = parent.orElse(null);
        this.lines = lines;
        this.before = before.orElse(null);
        this.after = after.orElse(null);
    }

    @Override
    public void before(String line, Action<? super Editor> action) {
        DefaultEditor t = objects.newInstance(DefaultEditor.class, objects, Optional.of(this), lines, Optional.of(line), Optional.empty());
        action.execute(t);
    }

    @Override
    public void after(String line, Action<? super Editor> action) {
        DefaultEditor t = objects.newInstance(DefaultEditor.class, objects, Optional.of(this), lines, Optional.empty(), Optional.of(line));
        action.execute(t);
    }

    private Boundaries getBoundaries() {
        int size = lines.size();
        if (parent == null) {
            Integer start = after == null ? null : lookupFor(after, 0, size) + 1;
            Integer end = before == null ? null : lookupFor(before, 0, size);
            return new Boundaries(start, end);
        } else {
            Boundaries parentBoundaries = parent.getBoundaries();
            Integer start = after == null ? parentBoundaries.start : (lookupFor(after, parentBoundaries.getStart().orElse(0), parentBoundaries.getEnd().orElse(size)) + 1);
            Integer end = before == null ? parentBoundaries.end : lookupFor(before, parentBoundaries.getStart().orElse(0), parentBoundaries.getEnd().orElse(size));
            return new Boundaries(start, end);
        }
    }

    private Integer lookupFor(String line, int min, int max) {
        for (int i = min; i < max; i++) {
            if (lines.get(i).equals(line)) {
                return i;
            }
        }
        throw new IllegalStateException("Unable to find line " + line);
    }


    @Override
    public void replace(String replacement) {
        if (lines.isEmpty()) {
            throw new IllegalStateException("No lines to replace");
        }
        Boundaries boundaries = getBoundaries();
        int startIndex = boundaries.getStart().orElse(0);
        int endIndex = boundaries.getEnd().orElse(lines.size());
        for (int idx = startIndex; idx < endIndex; idx++) {
            lines.set(idx, replacement);
        }
    }

    @Override
    public void replace(String line, String replacement) {
        Boundaries boundaries = getBoundaries();
        int startIndex = boundaries.getStart().orElse(0);
        int endIndex = boundaries.getEnd().orElse(lines.size());
        for (int idx = startIndex; idx < endIndex; idx++) {
            if (lines.get(idx).equals(line)) {
                lines.set(idx, replacement);
            }
        }
    }

    @Override
    public void replaceRegex(String regex, String replacement) {
        Boundaries boundaries = getBoundaries();
        int startIndex = boundaries.getStart().orElse(0);
        int endIndex = boundaries.getEnd().orElse(lines.size());
        for (int idx = startIndex; idx < endIndex; idx++) {
            var line = lines.get(idx);
            lines.set(idx, line.replaceAll(regex, replacement));
        }
    }

    @Override
    public void insert(String... lines) {
        int startIndex = getBoundaries().getStart().orElse(0);
        this.lines.addAll(startIndex, Arrays.asList(lines));
    }

    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    private record Boundaries(
        Integer start,
        Integer end
    ) {

        public Optional<Integer> getStart() {
                return Optional.ofNullable(start);
            }

        public Optional<Integer> getEnd() {
                return Optional.ofNullable(end);
            }
    }
}
