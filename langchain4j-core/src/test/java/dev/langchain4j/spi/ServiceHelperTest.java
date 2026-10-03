package dev.langchain4j.spi;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ServiceHelperTest implements WithAssertions {

    private void assertServices(Collection<ExampleService> services) {
        assertThat(services).extracting(ExampleService::getGreeting).containsExactlyInAnyOrder("Hello", "Goodbye");
    }

    @SuppressWarnings("unused")
    interface NotAService {
        int unused();
    }

    @Test
    void load_factories() {
        assertServices(ServiceHelper.loadFactories(ExampleService.class));
        assertServices(ServiceHelper.loadFactories(ExampleService.class, ServiceHelperTest.class.getClassLoader()));
        assertThat(ServiceHelper.loadFactories(NotAService.class)).isEmpty();
    }

    @Test
    void collection_loading_does_not_claim_that_returned_services_are_ignored() throws Exception {
        String output = runLoader("collection");
        assertThat(output)
                .contains(
                        "greetings=Hello,Goodbye",
                        "WARN",
                        ExampleServiceHello.class.getName(),
                        ExampleServiceGoodbye.class.getName())
                .doesNotContain("ignoring");
    }

    @ParameterizedTest
    @CsvSource({
        "all, 'greetings=Hello,Goodbye'",
        "all-explicit, 'greetings=Hello,Goodbye'",
        "all-fallback, 'greetings=Hello,Goodbye'",
        "all-missing, 'greetings=0'",
        "single-missing, 'selected=NULL'",
        "priority-all, 'greetings=high,low'"
    })
    void all_provider_loading_preserves_results_without_selection_warnings(String mode, String expected)
            throws Exception {
        assertThat(runLoader(mode)).contains(expected).doesNotContain("WARN");
    }

    @Test
    void single_provider_selection_keeps_the_warning_and_selected_service() throws Exception {
        assertThat(runLoader("single"))
                .contains(
                        "selected=Hello",
                        "WARN",
                        "using " + ExampleServiceHello.class.getName(),
                        "ignoring [" + ExampleServiceGoodbye.class.getName());
    }

    @Test
    void single_provider_selection_uses_priority_before_service_loader_order() throws Exception {
        assertThat(runLoader("priority-single"))
                .contains(
                        "selected=high",
                        "WARN",
                        "using " + HighPriorityService.class.getName(),
                        "ignoring [" + LowPriorityService.class.getName());
    }

    private String runLoader(String mode) throws IOException, InterruptedException {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path outputFile = Files.createTempFile("spi-loader-output", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", executable)
                                    .toString(),
                            "-cp",
                            classpath,
                            LoaderProcess.class.getName(),
                            mode)
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile())
                    .start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                fail("SPI loader process did not finish: " + Files.readString(outputFile));
            }
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as(output).isZero();
            return output;
        } finally {
            try {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
                }
            } finally {
                Files.deleteIfExists(outputFile);
            }
        }
    }

    public static class LowPriorityService implements ExampleService, PrioritizedFactory {
        public String getGreeting() {
            return "low";
        }

        public int priority() {
            return YIELDS_TO_OTHERS;
        }
    }

    public static class HighPriorityService implements ExampleService, PrioritizedFactory {
        public String getGreeting() {
            return "high";
        }

        public int priority() {
            return 10;
        }
    }

    public static class LoaderProcess {
        public static void main(String[] args) throws Exception {
            String mode = args[0];
            Path serviceFile = null;
            try {
                if (mode.startsWith("priority-")) {
                    serviceFile = Files.createTempFile("spi-priority", ".txt");
                    Files.writeString(
                            serviceFile,
                            LowPriorityService.class.getName() + "\n" + HighPriorityService.class.getName() + "\n");
                    URL resource = serviceFile.toUri().toURL();
                    Thread.currentThread()
                            .setContextClassLoader(new ClassLoader(ExampleService.class.getClassLoader()) {
                                public Enumeration<URL> getResources(String name) throws IOException {
                                    if (name.equals("META-INF/services/" + ExampleService.class.getName())) {
                                        return Collections.enumeration(Collections.singletonList(resource));
                                    }
                                    return super.getResources(name);
                                }
                            });
                }
                if (mode.equals("single-missing")) {
                    System.out.println("selected="
                            + (ServiceHelper.loadFactory(NotAService.class) == null ? "NULL" : "unexpected"));
                } else if (mode.equals("single") || mode.equals("priority-single")) {
                    System.out.println("selected="
                            + ServiceHelper.loadFactory(ExampleService.class).getGreeting());
                } else if (mode.equals("all-missing")) {
                    System.out.println("greetings="
                            + ServiceHelper.loadAllFactories(NotAService.class).size());
                } else {
                    Collection<ExampleService> services;
                    if (mode.equals("collection")) {
                        services = ServiceHelper.loadFactories(ExampleService.class);
                    } else if (mode.equals("all-explicit")) {
                        services = ServiceHelper.loadAllFactories(
                                ExampleService.class, ExampleService.class.getClassLoader());
                    } else if (mode.equals("all-fallback")) {
                        services = ServiceHelper.loadAllFactories(ExampleService.class, new ClassLoader(null) {});
                    } else {
                        services = ServiceHelper.loadAllFactories(ExampleService.class);
                    }
                    System.out.println("greetings="
                            + services.stream().map(ExampleService::getGreeting).collect(Collectors.joining(",")));
                }
            } finally {
                if (serviceFile != null) Files.deleteIfExists(serviceFile);
            }
        }
    }
}
