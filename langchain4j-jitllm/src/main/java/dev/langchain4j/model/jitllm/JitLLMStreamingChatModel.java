package dev.langchain4j.model.jitllm;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.nio.file.Path;
import java.util.List;
import org.beehive.jitllm.api.GenerationResult;

/**
 * A {@link StreamingChatModel} that runs a GGUF model inside the JVM with <a href="https://github.com/beehive-lab/jitllm">jitLLM</a>,
 * on a GPU through <a href="https://github.com/beehive-lab/TornadoVM">TornadoVM</a> or on the CPU.
 * <p>
 * The model is loaded when it is built and keeps its memory (including GPU memory) until {@link #close()} is called.
 * One instance can be shared between threads, but it generates one response at a time:
 * concurrent requests wait for each other.
 * <p>
 * {@link #chat(ChatRequest, StreamingChatResponseHandler)} blocks until the response is complete,
 * and the handler is called on the calling thread. To avoid blocking, call it from a separate thread,
 * for example a virtual thread.
 * <p>
 * The response is streamed token by token. When the request contains tools, the response is
 * delivered once it is complete, because whether the model calls a tool is only known at the end.
 * Streaming can be cancelled through the {@link dev.langchain4j.model.chat.response.StreamingHandle}
 * passed to the handler.
 * <p>
 * Example:
 * <pre>{@code
 * try (JitLLMStreamingChatModel model = JitLLMStreamingChatModel.builder()
 *         .modelPath(Path.of("Qwen3-0.6B-Q8_0.gguf"))
 *         .build()) {
 *     model.chat("Tell me a story", handler);
 * }
 * }</pre>
 */
public final class JitLLMStreamingChatModel extends JitLLMBaseModel implements StreamingChatModel {

    private JitLLMStreamingChatModel(JitLLMStreamingChatModelBuilder builder) {
        super(
                builder.modelPath,
                builder.contextLength,
                builder.onGPU,
                builder.think,
                JitLLMConversions.defaultRequestParameters(
                        builder.defaultRequestParameters,
                        builder.temperature,
                        builder.topP,
                        builder.maxTokens,
                        builder.stopSequences),
                builder.seed,
                builder.returnThinking,
                builder.listeners);
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder
     */
    public static JitLLMStreamingChatModelBuilder builder() {
        return new JitLLMStreamingChatModelBuilder();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        JitLLMStreamingHandle streamingHandle = new JitLLMStreamingHandle();
        boolean streamWhileGenerating = chatRequest.toolSpecifications().isEmpty();
        JitLLMOutputParser parser = new JitLLMOutputParser(
                chatRequest.parameters().stopSequences(),
                answer -> {
                    if (streamWhileGenerating) {
                        emitPartialResponse(handler, answer, streamingHandle);
                    }
                },
                thinking -> {
                    if (streamWhileGenerating) {
                        emitPartialThinking(handler, thinking, streamingHandle);
                    }
                });

        GenerationResult result;
        try {
            result = generate(chatRequest, parser, streamingHandle.cancellationToken());
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(e));
            return;
        }

        List<ToolExecutionRequest> toolExecutionRequests = JitLLMConversions.toToolExecutionRequests(result.toolCalls());
        if (!streamWhileGenerating) {
            emitPartialThinking(handler, parser.thinking(), streamingHandle);
            if (toolExecutionRequests.isEmpty()) {
                emitPartialResponse(handler, parser.answer(), streamingHandle);
            }
        }
        if (streamingHandle.isCancelled()) {
            return;
        }
        for (int index = 0; index < toolExecutionRequests.size(); index++) {
            onCompleteToolCall(handler, new CompleteToolCall(index, toolExecutionRequests.get(index)));
        }
        onCompleteResponse(handler, toChatResponse(result, parser));
    }

    private static void emitPartialResponse(
            StreamingChatResponseHandler handler, String answer, JitLLMStreamingHandle streamingHandle) {
        if (!streamingHandle.isCancelled()) {
            onPartialResponse(handler, answer, streamingHandle);
        }
    }

    private void emitPartialThinking(
            StreamingChatResponseHandler handler, String thinking, JitLLMStreamingHandle streamingHandle) {
        if (returnThinking() && !streamingHandle.isCancelled()) {
            onPartialThinking(handler, thinking, streamingHandle);
        }
    }

    /**
     * Builder for {@link JitLLMStreamingChatModel}.
     */
    public static final class JitLLMStreamingChatModelBuilder {

        private Path modelPath;
        private Integer contextLength;
        private Boolean onGPU;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private List<String> stopSequences;
        private Integer seed;
        private Boolean think;
        private Boolean returnThinking;
        private ChatRequestParameters defaultRequestParameters;
        private List<ChatModelListener> listeners;

        private JitLLMStreamingChatModelBuilder() {}

        /**
         * Sets the path to the model file in GGUF format. Required.
         *
         * @param modelPath the path to the GGUF file
         * @return this builder
         */
        public JitLLMStreamingChatModelBuilder modelPath(Path modelPath) {
            this.modelPath = modelPath;
            return this;
        }

        /**
         * Sets the context window: the maximum number of tokens of the whole conversation
         * (system message, history, tool definitions and the generated response).
         * Memory for the whole window is reserved when the model is loaded. Default: 4096.
         *
         * @param contextLength the context window in tokens
         * @return this builder
         */
        public JitLLMStreamingChatModelBuilder contextLength(Integer contextLength) {
            this.contextLength = contextLength;
            return this;
        }

        /**
         * Sets whether the model runs on a GPU ({@code true}) or on the CPU ({@code false}).
         * <p>
         * Running on a GPU requires the JVM to be started through TornadoVM with {@code -Duse.tornadovm=true},
         * otherwise building the model fails with an {@link IllegalStateException}.
         * The GPU backend (CUDA, OpenCL or Metal) is the one provided by the installed TornadoVM SDK.
         * <p>
         * Default: {@code true} if the JVM was started with {@code -Duse.tornadovm=true}, {@code false} otherwise.
         *
         * @param onGPU whether to run on a GPU
         * @return this builder
         */
        public JitLLMStreamingChatModelBuilder onGPU(Boolean onGPU) {
            this.onGPU = onGPU;
            return this;
        }

        /**
         * Sets the sampling temperature. Default: 0.1.
         *
         * @param temperature the temperature
         * @return this builder
         */
        public JitLLMStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets the nucleus sampling probability. Default: 0.95.
         *
         * @param topP the nucleus sampling probability
         * @return this builder
         */
        public JitLLMStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * Sets the maximum number of tokens to generate per response, including the thinking. Default: 512.
         *
         * @param maxTokens the maximum number of tokens to generate
         * @return this builder
         */
        public JitLLMStreamingChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Sets the sequences that end the response when they appear in the answer.
         * The response is cut before the sequence. The thinking of reasoning models is not checked.
         *
         * @param stopSequences the stop sequences
         * @return this builder
         */
        public JitLLMStreamingChatModelBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        /**
         * Sets the seed of the random sampling, to make responses reproducible.
         * Default: a different seed for every request.
         *
         * @param seed the seed
         * @return this builder
         */
        public JitLLMStreamingChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Sets whether reasoning models think before answering: {@code true} enables thinking,
         * {@code false} disables it, and when not set, the model's own default applies (Qwen 3, for example, thinks).
         * Models that cannot think ignore this setting. Thinking improves answers to complex questions,
         * but the thinking tokens count against {@link #maxTokens(Integer)} and take time to generate.
         *
         * @param think whether the model thinks before answering
         * @return this builder
         */
        public JitLLMStreamingChatModelBuilder think(Boolean think) {
            this.think = think;
            return this;
        }

        /**
         * Sets whether the thinking of reasoning models (the text between {@code <think>} and {@code </think>})
         * is returned in {@link dev.langchain4j.data.message.AiMessage#thinking()} and streamed to
         * {@link StreamingChatResponseHandler#onPartialThinking}.
         * The thinking is never part of {@link dev.langchain4j.data.message.AiMessage#text()}. Default: {@code false}.
         *
         * @param returnThinking whether to return the thinking
         * @return this builder
         */
        public JitLLMStreamingChatModelBuilder returnThinking(Boolean returnThinking) {
            this.returnThinking = returnThinking;
            return this;
        }

        /**
         * Sets the parameters used for every request, unless the request sets them itself.
         * Values set directly on this builder (for example {@link #temperature(Double)}) take precedence.
         *
         * @param defaultRequestParameters the default request parameters
         * @return this builder
         */
        public JitLLMStreamingChatModelBuilder defaultRequestParameters(
                ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        /**
         * Sets the listeners notified about every request, response and error.
         *
         * @param listeners the listeners
         * @return this builder
         */
        public JitLLMStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        /**
         * Builds the model. This loads the model file, which can take a while for large models.
         *
         * @return the model
         */
        public JitLLMStreamingChatModel build() {
            return new JitLLMStreamingChatModel(this);
        }
    }
}
