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

import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlPosition;
import org.tomlj.TomlTable;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A version catalog parser which is tolerant to errors and
 * builds a representation of the catalog suitable for updating
 * coordinates.
 */
public class LenientVersionCatalogParser {
    private static final String LIBRARIES_KEY = "libraries";
    private static final String VERSIONS_KEY = "versions";

    private final VersionCatalogTomlModel model = new VersionCatalogTomlModel();

    public static List<String> splitToList(String coordinates) {
        List<String> result = new ArrayList<>(3);
        String[] split = coordinates.split(":");
        for (int i = 0, splitLength = split.length; i < splitLength; i++) {
            String part = split[i];
            if (i < splitLength - 1 || !part.isEmpty()) {
                result.add(part);
            }
        }
        return result;
    }

    public void parse(InputStream in) throws IOException {
        RichVersionParser strictVersionParser = new RichVersionParser();
        TomlParseResult result = Toml.parse(in);
        TomlTable librariesTable = result.getTable(LIBRARIES_KEY);
        TomlTable versionsTable = result.getTable(VERSIONS_KEY);
        parseLibraries(librariesTable, strictVersionParser);
        parseVersions(versionsTable, strictVersionParser);
    }

    public VersionCatalogTomlModel getModel() {
        return model;
    }

    private void parseLibraries(@Nullable TomlTable librariesTable, RichVersionParser strictVersionParser) {
        if (librariesTable == null) {
            return;
        }
        List<String> keys = librariesTable.keySet()
                .stream()
                .sorted(Comparator.comparing(String::length))
                .toList();
        for (String alias : keys) {
            parseLibrary(alias, librariesTable, strictVersionParser);
        }
    }

    private void parseVersions(@Nullable TomlTable versionsTable, RichVersionParser strictVersionParser) {
        if (versionsTable == null) {
            return;
        }
        List<String> keys = versionsTable.keySet()
                .stream()
                .sorted(Comparator.comparing(String::length))
                .toList();
        for (String alias : keys) {
            parseVersion(alias, versionsTable, strictVersionParser);
        }
    }

    @Nullable
    private static String expectString(String name, TomlTable table, @Nullable String element) {
        String path = name;
        if (element != null) {
            path += "." + element;
        }
        return table.getString(path);
    }

    @Nullable
    private static TomlArray expectArray(TomlTable table, String element) {
        try {
            return table.getArray(element);
        } catch (TomlInvalidTypeException ex) {
            return null;
        }
    }

    @Nullable
    private static Boolean expectBoolean(TomlTable table, String element) {
        return table.getBoolean(element);
    }

    private void parseLibrary(String alias, TomlTable librariesTable, RichVersionParser strictVersionParser) {
        Object gav = librariesTable.get(alias);
        TomlPosition position = librariesTable.inputPositionOf(alias);
        if (gav instanceof String) {
            List<String> splitted = splitToList((String) gav);
            if (splitted.size() == 3) {
                String group = splitted.get(0);
                String name = splitted.get(1);
                String version = splitted.get(2);
                RichVersion rich = strictVersionParser.parse(version);
                VersionModel versionModel = new VersionModel(null, rich, position);
                model.addLibrary(new Library(
                        alias,
                        group,
                        name,
                        versionModel,
                        position
                ));
                return;
            }
        }
        String group = expectString(alias, librariesTable, "group");
        String name = expectString(alias, librariesTable, "name");
        Object version = librariesTable.get(alias + ".version");
        String mi = expectString(alias, librariesTable, "module");
        if (mi != null) {
            List<String> splitted = splitToList(mi);
            if (splitted.size() == 2) {
                group = splitted.get(0);
                name = splitted.get(1);
            }
        }
        VersionModel versionModel = null;
        if (version instanceof String) {
            String require = (String) version;
            RichVersion richVersion = strictVersionParser.parse(require);
            versionModel = new VersionModel(null, richVersion, position);
        } else if (version instanceof TomlTable versionTable) {
            String versionRef = versionTable.getString("ref");
            String require = versionTable.getString("require");
            String prefer = versionTable.getString("prefer");
            String strictly = versionTable.getString("strictly");
            TomlArray rejectedArray = expectArray(versionTable, "reject");
            List<String> rejectedVersions = rejectedArray != null ? rejectedArray.toList().stream()
                    .map(String::valueOf)
                    .toList() : null;
            Boolean rejectAll = expectBoolean(versionTable, "rejectAll");
            versionModel = new VersionModel(versionRef, versionRef == null ? new RichVersion(
                    require,
                    strictly,
                    prefer,
                    rejectedVersions,
                    rejectAll != null ? rejectAll : false
            ) : null, position);
        }
        if (versionModel != null) {
            model.addLibrary(new Library(alias, group, name, versionModel, position));
        }
    }

    private void parseVersion(String alias, TomlTable versionsTable, RichVersionParser strictVersionParser) {
        String require = null;
        String strictly = null;
        String prefer = null;
        List<String> rejectedVersions = null;
        Boolean rejectAll = null;
        Object version = versionsTable.get(alias);
        TomlPosition position = versionsTable.inputPositionOf(alias);
        if (version instanceof String) {
            require = (String) version;
            RichVersion richVersion = strictVersionParser.parse(require);
            model.addVersion(new VersionModel(alias, richVersion, position));
        } else if (version instanceof TomlTable versionTable) {
            require = versionTable.getString("require");
            prefer = versionTable.getString("prefer");
            strictly = versionTable.getString("strictly");
            TomlArray rejectedArray = expectArray(versionTable, "reject");
            rejectedVersions = rejectedArray != null ? rejectedArray.toList().stream()
                    .map(String::valueOf)
                    .toList() : null;
            rejectAll = expectBoolean(versionTable, "rejectAll");
            model.addVersion(new VersionModel(alias, new RichVersion(
                    require,
                    strictly,
                    prefer,
                    rejectedVersions,
                    rejectAll != null ? rejectAll : false
            ), position));
        }
    }

}
