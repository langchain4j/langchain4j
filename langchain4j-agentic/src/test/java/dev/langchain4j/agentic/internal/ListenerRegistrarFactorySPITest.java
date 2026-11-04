package dev.langchain4j.agentic.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

/**
 * Reproduces the NPE reported in <a href="https://github.com/langchain4j/langchain4j/issues/5103">#5103</a>.
 *
 * When an {@code AiServiceListenerRegistrarFactory} is registered via SPI and returns a shared singleton registrar,
 * the supervisor's internal {@code PlannerAgent} fires events through the same registrar that sub-agent proxies
 * are listening on. The sub-agent listener receives the event with null {@code managedParameters()}, causing NPE
 * at {@code AgentInvocationHandler.invoke()} line 82.
 */
class ListenerRegistrarFactorySPITest {

    private static final String SPI_SERVICE_FILE =
            "META-INF/services/dev.langchain4j.spi.observability.AiServiceListenerRegistrarFactory";

    @Test
    void should_reproduce_npe_with_spi_listener_factory_and_supervisor() throws Exception {
        try (CompiledFactory compiledFactory = compileTestFactory()) {
            ClassLoader originalCL = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(compiledFactory.classLoader());
            try {
                ChatModel model = new StubChatModel();

                SimpleExpert expert = AgenticServices.agentBuilder(SimpleExpert.class)
                        .chatModel(model)
                        .build();

                SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                        .chatModel(model)
                        .subAgents(expert)
                        .build();

                assertThatCode(() -> supervisor.invoke("test question"))
                        .as("Before the fix, a shared SPI registrar caused NPE in AgentInvocationHandler " +
                            "because managedParameters() was null when PlannerAgent fired events")
                        .doesNotThrowAnyException();
            } finally {
                Thread.currentThread().setContextClassLoader(originalCL);
            }
        }
    }

    public interface SimpleExpert {

        @UserMessage("You are a simple expert. Answer: {{request}}")
        @Agent(description = "A simple expert", outputKey = "response")
        String answer(@V("request") String request);
    }

    private static CompiledFactory compileTestFactory() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("These tests require a JDK").isNotNull();

        Path tempDir = Files.createTempDirectory(dynamicTestClassesDir(), "compiled-factory-");

        Path sourceFile =
                tempDir.resolve("dev/langchain4j/agentic/internal/generated/TestListenerRegistrarFactory.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, testFactorySource(), StandardCharsets.UTF_8);

        int result = compiler.run(
                null,
                null,
                null,
                "-classpath",
                System.getProperty("java.class.path"),
                "-d",
                tempDir.toString(),
                sourceFile.toString());
        assertThat(result).as("Factory compilation must succeed").isZero();

        Path servicesFile = tempDir.resolve(SPI_SERVICE_FILE);
        Files.createDirectories(servicesFile.getParent());
        Files.writeString(
                servicesFile,
                "dev.langchain4j.agentic.internal.generated.TestListenerRegistrarFactory",
                StandardCharsets.UTF_8);

        URLClassLoader classLoader = new URLClassLoader(
                new URL[] {tempDir.toUri().toURL()}, ListenerRegistrarFactorySPITest.class.getClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (name.equals(SPI_SERVICE_FILE)) {
                    return findResources(name);
                }
                return super.getResources(name);
            }
        };
        return new CompiledFactory(classLoader);
    }

    private static Path dynamicTestClassesDir() throws Exception {
        Path dir = Path.of("target", "dynamic-test-classes");
        Files.createDirectories(dir);
        return dir;
    }

    private static String testFactorySource() {
        return """
                package dev.langchain4j.agentic.internal.generated;

                import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
                import dev.langchain4j.observability.api.DefaultAiServiceListenerRegistrar;
                import dev.langchain4j.spi.observability.AiServiceListenerRegistrarFactory;

                public class TestListenerRegistrarFactory implements AiServiceListenerRegistrarFactory {

                    private static final AiServiceListenerRegistrar REGISTRAR = new DelegatingRegistrar();

                    @Override
                    public AiServiceListenerRegistrar get() {
                        return REGISTRAR;
                    }

                    public static class DelegatingRegistrar implements AiServiceListenerRegistrar {
                        private final DefaultAiServiceListenerRegistrar delegate = new DefaultAiServiceListenerRegistrar();

                        public DelegatingRegistrar() {
                            delegate.shouldThrowExceptionOnEventError(true);
                        }

                        @Override
                        public <T extends dev.langchain4j.observability.api.event.AiServiceEvent> void register(
                                dev.langchain4j.observability.api.listener.AiServiceListener<T> listener) {
                            delegate.register(listener);
                        }

                        @Override
                        public <T extends dev.langchain4j.observability.api.event.AiServiceEvent> void unregister(
                                dev.langchain4j.observability.api.listener.AiServiceListener<T> listener) {
                            delegate.unregister(listener);
                        }

                        @Override
                        public <T extends dev.langchain4j.observability.api.event.AiServiceEvent> void fireEvent(T event) {
                            delegate.fireEvent(event);
                        }

                        @Override
                        public void shouldThrowExceptionOnEventError(boolean value) {
                            // always keep delegate set to true
                        }
                    }
                }
                """;
    }

    private record CompiledFactory(URLClassLoader classLoader) implements AutoCloseable {

        @Override
        public void close() throws Exception {
            classLoader.close();
        }
    }

    static class StubChatModel implements ChatModel {

        private int callCount = 0;

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            callCount++;
            String response = switch (callCount) {
                case 1 -> """
                        {"agentName":"answer","arguments":{"request":"test question"}}""";
                case 2 -> "stub expert answer";
                default -> """
                        {"agentName":"done","arguments":{"response":"final answer"}}""";
            };
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(response))
                    .build();
        }
    }
}
