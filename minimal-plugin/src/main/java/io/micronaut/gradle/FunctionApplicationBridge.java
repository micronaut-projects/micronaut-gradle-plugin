/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle;

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures early {@code application { }} configuration for function builds until
 * the real Gradle application plugin is applied for runtimes like
 * {@code lambda_provided}. Groovy build scripts can declare the block before the
 * runtime callback decides whether the real plugin is needed, so the bridge replays
 * that configuration once {@link org.gradle.api.plugins.ApplicationPlugin} is present.
 */
final class FunctionApplicationBridge {
    private final Property<String> mainModule;
    private final Property<String> mainClass;
    private final Property<String> applicationName;
    private final Property<String> executableDir;
    private final CopySpec applicationDistribution;
    private final List<String> applicationDefaultJvmArgs = new ArrayList<>();

    private JavaApplication delegate;

    FunctionApplicationBridge(Project project) {
        var objects = project.getObjects();
        this.mainModule = objects.property(String.class);
        this.mainClass = objects.property(String.class);
        this.applicationName = objects.property(String.class).convention(project.getName());
        this.executableDir = objects.property(String.class);
        this.applicationDistribution = project.copySpec();
    }

    void attach(JavaApplication javaApplication) {
        this.delegate = javaApplication;
        if (applicationName.isPresent()) {
            javaApplication.setApplicationName(applicationName.get());
        }
        if (mainModule.isPresent()) {
            javaApplication.getMainModule().set(mainModule.get());
        }
        if (mainClass.isPresent()) {
            javaApplication.getMainClass().set(mainClass.get());
        }
        if (!applicationDefaultJvmArgs.isEmpty()) {
            javaApplication.setApplicationDefaultJvmArgs(applicationDefaultJvmArgs);
        }
        if (executableDir.isPresent()) {
            javaApplication.setExecutableDir(executableDir.get());
        }
        javaApplication.getApplicationDistribution().with(applicationDistribution);
    }

    @SuppressWarnings("unused")
    public void call(Closure<?> closure) {
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(this);
        closure.call();
    }

    @SuppressWarnings("unused")
    public Property<String> getMainModule() {
        return delegate != null ? delegate.getMainModule() : mainModule;
    }

    @SuppressWarnings("unused")
    public void setMainModule(String value) {
        getMainModule().set(value);
    }

    @SuppressWarnings("unused")
    public Property<String> getMainClass() {
        return delegate != null ? delegate.getMainClass() : mainClass;
    }

    @SuppressWarnings("unused")
    public void setMainClass(String value) {
        getMainClass().set(value);
    }

    @SuppressWarnings("unused")
    public String getApplicationName() {
        return delegate != null ? delegate.getApplicationName() : applicationName.getOrNull();
    }

    @SuppressWarnings("unused")
    public void setApplicationName(String value) {
        if (delegate != null) {
            delegate.setApplicationName(value);
        } else {
            applicationName.set(value);
        }
    }

    @SuppressWarnings("unused")
    public Iterable<String> getApplicationDefaultJvmArgs() {
        return delegate != null ? delegate.getApplicationDefaultJvmArgs() : applicationDefaultJvmArgs;
    }

    @SuppressWarnings("unused")
    public void setApplicationDefaultJvmArgs(Iterable<String> values) {
        if (delegate != null) {
            delegate.setApplicationDefaultJvmArgs(values);
            return;
        }
        applicationDefaultJvmArgs.clear();
        values.forEach(applicationDefaultJvmArgs::add);
    }

    @SuppressWarnings("unused")
    public String getExecutableDir() {
        return delegate != null ? delegate.getExecutableDir() : executableDir.getOrNull();
    }

    @SuppressWarnings("unused")
    public void setExecutableDir(String value) {
        if (delegate != null) {
            delegate.setExecutableDir(value);
        } else {
            executableDir.set(value);
        }
    }

    @SuppressWarnings("unused")
    public CopySpec getApplicationDistribution() {
        return delegate != null ? delegate.getApplicationDistribution() : applicationDistribution;
    }

    @SuppressWarnings("unused")
    public void setApplicationDistribution(CopySpec copySpec) {
        if (delegate != null) {
            delegate.setApplicationDistribution(copySpec);
        } else {
            applicationDistribution.with(copySpec);
        }
    }
}
