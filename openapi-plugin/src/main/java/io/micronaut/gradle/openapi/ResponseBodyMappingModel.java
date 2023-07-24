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
package io.micronaut.gradle.openapi;

import java.io.Serializable;

/**
 * A model that can be used to specify body mappings during OpenAPI generation.
 *
 * <p>An equivalent to Micronaut OpenAPI's ResponseBodyMapping but
 * without using classes from that dependency so that we can isolate
 * execution in workers.</p>
 */
public final class ResponseBodyMappingModel implements Serializable {

    /**
     * The response header name that triggers the change of response type.
     */
    private final String headerName;

    /**
     * The type in which will be used as the response type. The type must take
     * a single type parameter, which will be the original body.
     */
    private final String mappedBodyType;

    /**
     * Whether the mapped body type needs to be supplied list items as property.
     */
    boolean isListWrapper;

    /**
     * Whether the mapped response body type required validation.
     */
    boolean isValidated;

    /**
     * Create a response body mapping.
     *
     * @param headerName The response header name that triggers the change of response type.
     * @param mappedBodyType The type in which will be used as the response type. The type must take
     *                       a single type parameter, which will be the original body. If the value is null,
     *                       the header will be removed and body not changed.
     */
    public ResponseBodyMappingModel(String headerName, String mappedBodyType) {
        this.headerName = headerName;
        this.mappedBodyType = mappedBodyType;
    }

    /**
     * Specify whether the mapped body is a list wrapper.
     * Then the mapped body type needs to be supplied list items as property
     *
     * @param isListWrapper whether it is a list wrapper
     * @return this instance
     */
    public ResponseBodyMappingModel withListWrapper(boolean isListWrapper) {
        this.isListWrapper = isListWrapper;
        return this;
    }

    /**
     * Specify whether the mapped response body type required validation.
     *
     * @param isValidated whether it should be validated
     * @return this instance
     */
    public ResponseBodyMappingModel withValidated(boolean isValidated) {
        this.isValidated = isValidated;
        return this;
    }

    public String getHeaderName() {
        return headerName;
    }

    public String getMappedBodyType() {
        return mappedBodyType;
    }

    public boolean isListWrapper() {
        return isListWrapper;
    }

    public boolean isValidated() {
        return isValidated;
    }

}
