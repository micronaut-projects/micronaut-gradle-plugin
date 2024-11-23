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

public record Library(
    String alias,
    String group,
    String name,
    VersionModel version,
    TomlPosition position
) {

    public String getModule() {
        return group + ":" + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Library library = (Library) o;

        if (!group.equals(library.group)) {
            return false;
        }
        if (!name.equals(library.name)) {
            return false;
        }
        return version.equals(library.version);
    }

    @Override
    public int hashCode() {
        int result = group.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + (version != null ? version.hashCode() : 0);
        return result;
    }
}
