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

import org.tomlj.TomlPosition;

import javax.annotation.Nullable;

public class VersionModel {
    private final String reference;
    private final RichVersion version;
    private final TomlPosition position;

    public VersionModel(@Nullable String reference, @Nullable RichVersion version, TomlPosition position) {
        this.reference = reference;
        this.version = version;
        this.position = position;
    }

    @Nullable
    public String getReference() {
        return reference;
    }

    @Nullable
    public RichVersion getVersion() {
        return version;
    }

    public TomlPosition getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        VersionModel that = (VersionModel) o;

        if (reference != null ? !reference.equals(that.reference) : that.reference != null) {
            return false;
        }
        return version != null ? version.equals(that.version) : that.version == null;
    }

    @Override
    public int hashCode() {
        int result = reference != null ? reference.hashCode() : 0;
        result = 31 * result + (version != null ? version.hashCode() : 0);
        return result;
    }
}
