package dev.langchain4j.service;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.spi.services.CompletableFutureAdapter;
import dev.langchain4j.spi.services.TokenStreamAdapter;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

public class SpiAdapterLoadingTest implements WithAssertions {

    @Test
    void both_stream_adapters_remain_usable_without_selection_warnings() throws Exception {
        String output = runConsumer("stream");
        assertThat(output).contains("adapted=FirstStream,SecondStream");
        assertThat(output).doesNotContain("implementations of " + TokenStreamAdapter.class.getName());
    }

    @Test
    void both_async_tool_adapters_remain_usable_without_selection_warnings() throws Exception {
        String output = runConsumer("tool");
        assertThat(output).contains("adapted=first,second");
        assertThat(output).doesNotContain("implementations of " + CompletableFutureAdapter.class.getName());
    }

    private String runConsumer(String mode) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path outputFile = Files.createTempFile("spi-consumer-output", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", executable)
                                    .toString(),
                            "-cp",
                            classpath,
                            ConsumerProcess.class.getName(),
                            mode)
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile())
                    .start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                fail("SPI consumer process did not finish: " + Files.readString(outputFile));
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

    public record FirstStream(TokenStream stream) {}

    public record SecondStream(TokenStream stream) {}

    public abstract static class StreamAdapter implements TokenStreamAdapter {
        private final Class<?> type;
        private final Function<TokenStream, Object> factory;

        protected StreamAdapter(Class<?> type, Function<TokenStream, Object> factory) {
            this.type = type;
            this.factory = factory;
        }

        public boolean canAdaptTokenStreamTo(Type type) {
            return this.type.equals(type);
        }

        public Object adapt(TokenStream stream) {
            return factory.apply(stream);
        }
    }

    public static class FirstStreamAdapter extends StreamAdapter {
        public FirstStreamAdapter() {
            super(FirstStream.class, FirstStream::new);
        }
    }

    public static class SecondStreamAdapter extends StreamAdapter {
        public SecondStreamAdapter() {
            super(SecondStream.class, SecondStream::new);
        }
    }

    public interface StreamingAssistant {
        @UserMessage("first")
        FirstStream first();

        @UserMessage("second")
        SecondStream second();
    }

    public interface AsyncValue {
        CompletableFuture<?> future();
    }

    public record FirstAsync<T>(CompletableFuture<T> future) implements AsyncValue {}

    public record SecondAsync<T>(CompletableFuture<T> future) implements AsyncValue {}

    public abstract static class FutureAdapter implements CompletableFutureAdapter {
        private final Class<?> type;

        protected FutureAdapter(Class<?> type) {
            this.type = type;
        }

        public boolean canAdapt(Type type) {
            return type instanceof ParameterizedType parameterized && this.type.equals(parameterized.getRawType());
        }

        public CompletableFuture<?> toCompletableFuture(Object value) {
            return ((AsyncValue) value).future();
        }

        public Object fromCompletableFuture(Type type, CompletableFuture<?> future) {
            throw new UnsupportedOperationException("Tool-only fixture");
        }
    }

    public static class FirstFutureAdapter extends FutureAdapter {
        public FirstFutureAdapter() {
            super(FirstAsync.class);
        }
    }

    public static class SecondFutureAdapter extends FutureAdapter {
        public SecondFutureAdapter() {
            super(SecondAsync.class);
        }
    }

    public static class Tools {
        @Tool
        public FirstAsync<String> first() {
            return new FirstAsync<>(CompletableFuture.completedFuture("first"));
        }

        @Tool
        public SecondAsync<String> second() {
            return new SecondAsync<>(CompletableFuture.completedFuture("second"));
        }
    }

    public static class ConsumerProcess {
        public static void main(String[] args) throws Exception {
            boolean stream = args[0].equals("stream");
            Class<?> spi = stream ? TokenStreamAdapter.class : CompletableFutureAdapter.class;
            String providers = stream
                    ? FirstStreamAdapter.class.getName() + "\n" + SecondStreamAdapter.class.getName() + "\n"
                    : FirstFutureAdapter.class.getName() + "\n" + SecondFutureAdapter.class.getName() + "\n";
            Path serviceFile = Files.createTempFile("spi-adapters", ".txt");
            try {
                Files.writeString(serviceFile, providers);
                URL resource = serviceFile.toUri().toURL();
                Thread.currentThread().setContextClassLoader(new ClassLoader(spi.getClassLoader()) {
                    public Enumeration<URL> getResources(String name) throws IOException {
                        if (name.equals("META-INF/services/" + spi.getName())) {
                            return Collections.enumeration(Collections.singletonList(resource));
                        }
                        return super.getResources(name);
                    }
                });
                if (stream) {
                    StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                            .streamingChatModel(new StreamingChatModel() {})
                            .build();
                    FirstStream first = assistant.first();
                    SecondStream second = assistant.second();
                    if (first.stream() == null || second.stream() == null)
                        throw new AssertionError("Missing TokenStream");
                    System.out.println("adapted=FirstStream,SecondStream");
                } else {
                    Tools tools = new Tools();
                    InvocationContext context =
                            InvocationContext.builder().chatMemoryId("DEFAULT").build();
                    String first = new DefaultToolExecutor(tools, Tools.class.getDeclaredMethod("first"))
                            .executeAsync(request("first"), context)
                            .get()
                            .resultText();
                    String second = new DefaultToolExecutor(tools, Tools.class.getDeclaredMethod("second"))
                            .executeAsync(request("second"), context)
                            .get()
                            .resultText();
                    System.out.println("adapted=" + first + "," + second);
                }
            } finally {
                Files.deleteIfExists(serviceFile);
            }
        }

        private static ToolExecutionRequest request(String name) {
            return ToolExecutionRequest.builder()
                    .id("1")
                    .name(name)
                    .arguments("{}")
                    .build();
        }
    }
}
