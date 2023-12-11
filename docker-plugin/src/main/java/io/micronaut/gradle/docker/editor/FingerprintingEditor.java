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
import java.util.Collections;
import java.util.List;

/**
 * An implementation of editor which computes a fingerprint
 * for Gradle input snapshotting.
 */
public class FingerprintingEditor implements Editor {
    private final ObjectFactory objects;
    private final List<String> fingerprint;

    // We use Optional because Gradle doesn't support injecting null values
    @Inject
    public FingerprintingEditor(ObjectFactory objects,
                                List<String> fingerprint) {
        this.objects = objects;
        this.fingerprint = fingerprint;
    }

    @Override
    public void before(String line, Action<? super Editor> action) {
        fingerprint.add("BEFORE LINE " + line);
        fork(action);
    }

    @Override
    public void after(String line, Action<? super Editor> action) {
        fingerprint.add("AFTER LINE " + line);
        fork(action);
    }

    private void fork(Action<? super Editor> action) {
        FingerprintingEditor t = objects.newInstance(FingerprintingEditor.class, objects, fingerprint);
        fingerprint.add("BEGIN");
        action.execute(t);
        fingerprint.add("END");
    }

    @Override
    public void replace(String replacement) {
        fingerprint.add("REPLACE");
        fingerprint.add(replacement);
    }

    @Override
    public void replace(String line, String replacement) {
        fingerprint.add("REPLACE");
        fingerprint.add(line);
        fingerprint.add(replacement);
    }

    @Override
    public void replaceRegex(String regex, String replacement) {
        fingerprint.add("REPLACE_REGEX");
        fingerprint.add(regex);
        fingerprint.add(replacement);
    }

    @Override
    public void insert(String... lines) {
        fingerprint.add("INSERT");
        Collections.addAll(fingerprint, lines);
    }

    public List<String> getFingerprint() {
        return fingerprint;
    }

}
