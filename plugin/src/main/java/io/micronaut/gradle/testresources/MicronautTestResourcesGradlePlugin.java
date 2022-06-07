/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package io.micronaut.gradle.testresources;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.session.BuildSessionLifecycleListener;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class MicronautTestResourcesGradlePlugin implements Plugin<Project> {
    public void apply(Project project) {
        Configuration server = createTestResourcesServerConfiguration(project);
        DependencyHandler dependencies = project.getDependencies();
        server.getDependencies().addAll(Arrays.asList(
                dependencies.create("io.micronaut.test:micronaut-test-resources-server:1.0.0-SNAPSHOT"),
                dependencies.create("io.micronaut.test:micronaut-test-resources-testcontainers:1.0.0-SNAPSHOT"),
                dependencies.create("io.micronaut.test:micronaut-test-resources-jdbc-mysql:1.0.0-SNAPSHOT"),
                dependencies.create("io.micronaut.test:micronaut-test-resources-jdbc-postgresql:1.0.0-SNAPSHOT"),
                dependencies.create("io.micronaut.test:micronaut-test-resources-kafka:1.0.0-SNAPSHOT")
        ));
        Provider<RegularFile> portFile = project.getLayout().getBuildDirectory().file("test-resources-port.txt");
        Provider<Integer> explicitPort = project.getProviders().systemProperty("micronaut.test-resources.server.port").map(Integer::parseInt);
        Provider<TestResourcesService> testResourcesService = project.getGradle().getSharedServices().registerIfAbsent("testResourcesService", TestResourcesService.class, spec -> {
            spec.getParameters().getClasspath().from(server);
            spec.getParameters().getPortFile().set(portFile);
            spec.getParameters().getPort().convention(explicitPort);
        });
        TaskContainer tasks = project.getTasks();

        TaskProvider<StartTestResourcesService> startTestResourcesService = tasks.register("startTestResourcesService", StartTestResourcesService.class, task -> {
            task.getServer().set(testResourcesService);
            task.getClasspath().from(server);
        });

        TaskProvider<WriteServerSettings> writeTestProperties = tasks.register("writeTestResourceProperties", WriteServerSettings.class, task -> {
            if (!explicitPort.isPresent()) {
                // This is a very ugly hack, but we need the port of the server
                // to be available when the task gets configured
                Path portFilePath = portFile.get().getAsFile().toPath();
                testResourcesService.get(); // force startup of service
                while (!Files.exists(portFilePath)) {
                    try {
                        // Give some time for the server to start
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
            task.getPort().set(explicitPort.orElse(project.getProviders().fileContents(portFile).getAsText().map(Integer::parseInt)));
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("generated-resources/test-resources-server"));
        });
        project.getConfigurations().all(conf -> {
            String name = conf.getName();
            if ("developmentOnly".equals(name) || "testRuntimeOnly".equals(name)) {
                conf.getDependencies().add(dependencies.create("io.micronaut.test:micronaut-test-resources-client:1.0.0-SNAPSHOT"));
                conf.getDependencies().add(dependencies.create(project.files(writeTestProperties)));
            }
        });

        tasks.withType(Test.class).configureEach(t -> t.dependsOn(startTestResourcesService));
        tasks.withType(JavaExec.class).configureEach(t -> t.dependsOn(startTestResourcesService));

        configureServiceReset((ProjectInternal) project);
    }

    private void configureServiceReset(ProjectInternal project) {
        ServiceRegistry services = project.getServices();
        ListenerManager listenerManager = services.get(ListenerManager.class);
        Field parentField;
        try {
            parentField = listenerManager.getClass().getDeclaredField("parent");

            parentField.setAccessible(true);
            listenerManager = (ListenerManager) parentField.get(parentField.get(listenerManager));
            listenerManager.addListener(new BuildSessionLifecycleListener() {
                @Override
                public void beforeComplete() {
                    TestResourcesService.reset();
                }
            });
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new GradleException(e.getMessage(), e);
        }
    }

    private Configuration createTestResourcesServerConfiguration(Project project) {
        return project.getConfigurations().create("testresources", conf -> {
            conf.setDescription("Dependencies for the Micronaut test resources service");
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
        });
    }

}
