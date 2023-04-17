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

import javax.annotation.Nullable;
import java.util.List;

public class RichVersion {
    public static final RichVersion EMPTY = new RichVersion(null, null, null, null, false);

    private final String require;
    private final String strictly;
    private final String prefer;
    private final List<String> rejectedVersions;
    private final boolean rejectAll;

    RichVersion(@Nullable String require,
                @Nullable String strictly,
                @Nullable String prefer,
                @Nullable List<String> rejectedVersions,
                boolean rejectAll) {
        this.require = require;
        this.strictly = strictly;
        this.prefer = prefer;
        this.rejectedVersions = rejectedVersions;
        this.rejectAll = rejectAll;
    }

    @Nullable
    public String getRequire() {
        return require;
    }

    @Nullable
    public String getStrictly() {
        return strictly;
    }

    @Nullable
    public String getPrefer() {
        return prefer;
    }

    @Nullable
    public List<String> getRejectedVersions() {
        return rejectedVersions;
    }

    public boolean isRejectAll() {
        return rejectAll;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RichVersion that = (RichVersion) o;

        if (rejectAll != that.rejectAll) {
            return false;
        }
        if (require != null ? !require.equals(that.require) : that.require != null) {
            return false;
        }
        if (strictly != null ? !strictly.equals(that.strictly) : that.strictly != null) {
            return false;
        }
        if (prefer != null ? !prefer.equals(that.prefer) : that.prefer != null) {
            return false;
        }
        return rejectedVersions != null ? rejectedVersions.equals(that.rejectedVersions) : that.rejectedVersions == null;
    }

    @Override
    public int hashCode() {
        int result = require != null ? require.hashCode() : 0;
        result = 31 * result + (strictly != null ? strictly.hashCode() : 0);
        result = 31 * result + (prefer != null ? prefer.hashCode() : 0);
        result = 31 * result + (rejectedVersions != null ? rejectedVersions.hashCode() : 0);
        result = 31 * result + (rejectAll ? 1 : 0);
        return result;
    }
}
