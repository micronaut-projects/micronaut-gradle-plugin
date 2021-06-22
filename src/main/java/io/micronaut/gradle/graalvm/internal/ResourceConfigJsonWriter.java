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
package io.micronaut.gradle.graalvm.internal;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * A simple, handcrafted "pretty" JSON writer which is <b>only</b> intended
 * to be used for the resource config file generation. Do not try to use it
 * for generic JSON writing as it's not designed for this.
 */
public class ResourceConfigJsonWriter {
    public static void generateJsonFile(List<Map<String, String>> resourceList, OutputStream out) {
        try (PrintWriter prn = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            prn.println("{");
            prn.println("   \"resources\": [");
            boolean comma = false;
            for (Map<String, String> entries : resourceList) {
                if (comma) {
                    prn.println(", ");
                }
                comma = true;
                prn.print("        { ");
                writeKeyValuePairs(prn, entries);
                prn.print(" }");
            }
            prn.println();
            prn.println("   ]");
            prn.println("}");
        }
    }

    private static void writeKeyValuePairs(PrintWriter prn, Map<String, String> entries) {
        boolean comma = false;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (comma) {
                prn.print(", ");
            }
            String key = escape(entry.getKey());
            String value = escape(entry.getValue());
            prn.print("\"" + key + "\": \"" + value + "\"");
            comma = true;
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replaceAll("\"", "\\\\\"");
    }
}
