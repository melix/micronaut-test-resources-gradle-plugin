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

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class ProxyService implements BuildService<ProxyService.Params> {
    interface Params extends BuildServiceParameters {
        ConfigurableFileCollection getClasspath();

        RegularFileProperty getPortFile();

        Property<Integer> getPort();
    }

    private static ExecutorService executorService = Executors.newFixedThreadPool(1);
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    public ProxyService() {
        if (STARTED.compareAndSet(false, true)) {
            File portFile = getParameters().getPortFile().getAsFile().get();
            Path portFilePath = portFile.toPath();
            if (Files.exists(portFilePath)) {
                try {
                    Files.delete(portFilePath);
                } catch (IOException e) {
                    throw new GradleException("Unable to delete port file", e);
                }
            }
            Property<Integer> port = getParameters().getPort();
            if (port.isPresent()) {
                boolean alreadyStarted;
                try {
                    Socket socket = new Socket("localhost", port.get());
                    socket.close();
                    alreadyStarted = true;
                    System.out.println("Test resources proxy already started on port " + port.get());
                } catch (IOException e) {
                    alreadyStarted = false;
                }
                if (alreadyStarted) {
                    return;
                }
            }
            startProxy(port, portFile);
        }
    }

    private void startProxy(Property<Integer> explicitPort, File outputPortFile) {
        executorService.submit(() -> {
            getExecOperations().javaexec(spec -> {
                spec.classpath(getParameters().getClasspath().getFiles());
                spec.getMainClass().set("io.micronaut.testresources.proxy.Application");
                spec.args("-Dmicronaut.http.client.read-timeout=60s");
                if (explicitPort.isPresent()) {
                    spec.args("-Dmicronaut.server.port=" + explicitPort.get());
                } else {
                    spec.args("--port-file=" + outputPortFile.getAbsolutePath());
                }
            });
        });
    }

    static void reset() {
        executorService.shutdownNow();
        executorService = Executors.newFixedThreadPool(1);
        STARTED.set(false);
    }
}
