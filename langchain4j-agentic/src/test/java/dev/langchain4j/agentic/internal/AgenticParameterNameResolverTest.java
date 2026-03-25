package dev.langchain4j.agentic.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class AgenticParameterNameResolverTest {

    @Test
    void should_prioritize_v_annotation_over_java_parameter_name() throws Exception {
        try (CompiledAiService compiledAiService = compileTestAiService()) {
            Method method = compiledAiService.type().getMethod("chat", String.class, String.class);
            Parameter parameter = method.getParameters()[1];

            assertThat(parameter.isNamePresent()).isTrue();

            String variableName = new AgenticParameterNameResolver().getVariableName(parameter);

            assertThat(variableName).isEqualTo("xx");
        }
    }

    @Test
    void should_render_system_message_template_using_v_annotation_name() throws Exception {
        try (CompiledAiService compiledAiService = compileTestAiService()) {
            CapturingChatModel model = new CapturingChatModel();
            Object aiService = AiServices.builder((Class) compiledAiService.type())
                    .chatModel(model)
                    .build();

            String response = (String) compiledAiService.type()
                    .getMethod("chat", String.class, String.class)
                    .invoke(aiService, "hello", "value-from-v");

            assertThat(response).isEqualTo("ok");
            assertThat(model.messages())
                    .containsExactly(
                            SystemMessage.from("Use value-from-v."),
                            dev.langchain4j.data.message.UserMessage.from("hello"));
        }
    }

    @Test
    void should_render_system_message_provider_template_using_v_annotation_name() throws Exception {
        try (CompiledAiService compiledAiService = compileTestAiService()) {
            CapturingChatModel model = new CapturingChatModel();
            Object aiService = AiServices.builder((Class) compiledAiService.type())
                    .chatModel(model)
                    .systemMessageProvider(memoryId -> "Use {{xx}}.")
                    .build();

            String response = (String) compiledAiService.type()
                    .getMethod("chatWithProvider", String.class, String.class)
                    .invoke(aiService, "hello", "value-from-v");

            assertThat(response).isEqualTo("ok");
            assertThat(model.messages())
                    .containsExactly(
                            SystemMessage.from("Use value-from-v."),
                            dev.langchain4j.data.message.UserMessage.from("hello"));
        }
    }

    private static CompiledAiService compileTestAiService() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("These tests require a JDK").isNotNull();

        Path tempDir = Files.createTempDirectory(dynamicTestClassesDir(), "compiled-ai-service-");
        Path sourceFile = tempDir.resolve("dev/langchain4j/agentic/internal/generated/CompiledTestAiService.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, compiledTestAiServiceSource(), StandardCharsets.UTF_8);

        int result = compiler.run(
                null,
                null,
                null,
                "-parameters",
                "-classpath",
                aiServicesClassPath(),
                "-d",
                tempDir.toString(),
                sourceFile.toString());

        assertThat(result).isZero();

        URLClassLoader classLoader =
                new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, AgenticParameterNameResolverTest.class.getClassLoader());
        Class<?> type = Class.forName(
                "dev.langchain4j.agentic.internal.generated.CompiledTestAiService", true, classLoader);
        return new CompiledAiService(type, classLoader);
    }

    private static Path dynamicTestClassesDir() throws Exception {
        Path dir = Path.of("target", "dynamic-test-classes");
        Files.createDirectories(dir);
        return dir;
    }

    private static String aiServicesClassPath() {
        try {
            return new File(AiServices.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getAbsolutePath();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to resolve AiServices classpath", e);
        }
    }

    private static String compiledTestAiServiceSource() {
        return """
                package dev.langchain4j.agentic.internal.generated;

                import dev.langchain4j.service.SystemMessage;
                import dev.langchain4j.service.UserMessage;
                import dev.langchain4j.service.V;

                public interface CompiledTestAiService {

                    @SystemMessage("Use {{xx}}.")
                    String chat(@UserMessage String message, @V("xx") String yy);

                    String chatWithProvider(@UserMessage String message, @V("xx") String yy);
                }
                """;
    }

    private record CompiledAiService(Class<?> type, URLClassLoader classLoader) implements AutoCloseable {

        @Override
        public void close() throws Exception {
            classLoader.close();
        }
    }

    static class CapturingChatModel implements ChatModel {

        private List<ChatMessage> messages;

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            this.messages = chatRequest.messages();
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }

        List<ChatMessage> messages() {
            return messages;
        }
    }
}
