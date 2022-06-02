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
package io.micronaut.gradle.testresources;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class ProxyService implements BuildService<ProxyService.Params> {
    interface Params extends BuildServiceParameters {
        ConfigurableFileCollection getClasspath();
    }

    private static ExecutorService executorService = Executors.newFixedThreadPool(1);
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    public ProxyService() {
        if (STARTED.compareAndSet(false, true)) {
            executorService.submit(() -> {
                getExecOperations().javaexec(spec -> {
                    spec.classpath(getParameters().getClasspath().getFiles());
                    spec.getMainClass().set("io.micronaut.testresources.proxy.Application");
                    spec.args("-Dmicronaut.server.port=13322", "-Dmicronaut.http.client.read-timeout=60s");
                });
            });
        }
    }

    static void reset() {
        executorService.shutdownNow();
        executorService = Executors.newFixedThreadPool(1);
        STARTED.set(false);
    }
}
