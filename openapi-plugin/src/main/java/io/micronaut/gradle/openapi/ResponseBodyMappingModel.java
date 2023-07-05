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

/**
 * An equivalent to Micronaut OpenAPI's ResponseBodyMapping but
 * without using classes from that dependency so that we can isolate
 * execution in workers.
 * @param headerName the header name
 * @param mappedBodyType the mapped body type
 * @param isListWrapper if it's a list wrapper
 * @param isValidated if it's validated
 */
public record ResponseBodyMappingModel(String headerName,
                                       String mappedBodyType,
                                       boolean isListWrapper,
                                       boolean isValidated) {
}
