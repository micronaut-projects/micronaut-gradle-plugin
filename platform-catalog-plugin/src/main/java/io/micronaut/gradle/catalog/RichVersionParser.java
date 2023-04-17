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

import org.gradle.api.InvalidUserCodeException;

import javax.annotation.Nullable;

public class RichVersionParser {

    public RichVersion parse(@Nullable String version) {
        if (version == null) {
            return RichVersion.EMPTY;
        }
        int idx = version.indexOf("!!");
        if (idx == 0) {
            throw new InvalidUserCodeException("The strict version modifier (!!) must be appended to a valid version number");
        }
        if (idx > 0) {
            String strictly = version.substring(0, idx);
            String prefer = version.substring(idx+2);
            return new RichVersion(null, strictly, prefer, null, false);
        }
        return new RichVersion(version, null, null, null, false);
    }

}
