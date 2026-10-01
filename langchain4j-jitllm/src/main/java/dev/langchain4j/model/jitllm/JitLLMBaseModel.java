package dev.langchain4j.model.jitllm;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.beehive.jitllm.api.CancellationToken;
import org.beehive.jitllm.api.FinishReason;
import org.beehive.jitllm.api.GenerationResult;
import org.beehive.jitllm.api.GenerationSession;
import org.beehive.jitllm.api.LocalModel;
import org.beehive.jitllm.api.LocalModels;
import org.beehive.jitllm.api.ModelOptions;
import org.beehive.jitllm.api.TextGenerationModel;
import org.beehive.jitllm.api.ThinkingMode;
import org.beehive.jitllm.runtime.backend.BackendId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class JitLLMBaseModel implements AutoCloseable {

    private static final int DEFAULT_CONTEXT_LENGTH = 4096;

    private static final Logger log = LoggerFactory.getLogger(JitLLMBaseModel.class);
    private static final String USE_TORNADOVM = "use.tornadovm";

    private final ReentrantLock lock = new ReentrantLock();
    private final int contextLength;
    private final ChatRequestParameters defaultRequestParameters;
    private final Integer seed;
    private final boolean returnThinking;
    private final List<ChatModelListener> listeners;
    private final LocalModel model;

    private GenerationSession session;
    private boolean closed;

    JitLLMBaseModel(
            Path modelPath,
            Integer contextLength,
            Boolean onGPU,
            Boolean think,
            ChatRequestParameters defaultRequestParameters,
            Integer seed,
            Boolean returnThinking,
            List<ChatModelListener> listeners) {
        ensureNotNull(modelPath, "modelPath");
        this.contextLength =
                ensureGreaterThanZero(getOrDefault(contextLength, DEFAULT_CONTEXT_LENGTH), "contextLength");
        this.defaultRequestParameters = defaultRequestParameters;
        this.seed = seed;
        this.returnThinking = getOrDefault(returnThinking, false);
        this.listeners = copy(listeners);
        this.model = load(
                modelPath,
                this.contextLength,
                getOrDefault(onGPU, () -> Boolean.getBoolean(USE_TORNADOVM)),
                toThinkingMode(think));
    }

    private static ThinkingMode toThinkingMode(Boolean think) {
        if (think == null) {
            return ThinkingMode.DEFAULT;
        }
        return think ? ThinkingMode.ENABLED : ThinkingMode.DISABLED;
    }

    private static LocalModel load(Path modelPath, int contextLength, boolean onGPU, ThinkingMode thinkingMode) {
        ModelOptions.Builder options =
                ModelOptions.builder().contextLength(contextLength).thinkingMode(thinkingMode);
        if (onGPU) {
            if (!Boolean.getBoolean(USE_TORNADOVM)) {
                throw new IllegalStateException("onGPU(true) requires the JVM to be started through TornadoVM with -D"
                        + USE_TORNADOVM + "=true. Use onGPU(false) to run on the CPU.");
            }
            // no backend is set, so the one provided by the TornadoVM SDK (CUDA, OpenCL or Metal) is used
        } else {
            options.backend(BackendId.CPU);
        }
        try {
            return LocalModels.load(modelPath, options.build());
        } catch (IOException e) {
            throw new LangChain4jException("Failed to load the model from " + modelPath, e);
        }
    }

    GenerationResult generate(
            ChatRequest chatRequest, JitLLMOutputParser parser, CancellationToken cancellationToken) {
        JitLLMConversions.validate(chatRequest);
        var generationRequest = JitLLMConversions.toGenerationRequest(
                chatRequest, seed, event -> parser.accept(event.text()), cancellationToken);

        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("The model is closed");
            }
            if (session == null) {
                session = ((TextGenerationModel) model).newSession();
            }
            session.reset(); // every ChatRequest carries the whole conversation
            GenerationResult result = session.generate(generationRequest);
            if (result.finishReason() == FinishReason.CONTEXT_FULL) {
                log.warn(
                        "Generation stopped because the context window of {} tokens is full. "
                                + "Increase contextLength to fit longer conversations.",
                        contextLength);
            }
            parser.finish();
            return result;
        } finally {
            lock.unlock();
        }
    }

    ChatResponse toChatResponse(GenerationResult result, JitLLMOutputParser parser) {
        List<ToolExecutionRequest> toolExecutionRequests = JitLLMConversions.toToolExecutionRequests(result.toolCalls());
        AiMessage.Builder aiMessage = AiMessage.builder();
        if (toolExecutionRequests.isEmpty()) {
            aiMessage.text(parser.answer());
        } else {
            aiMessage.toolExecutionRequests(toolExecutionRequests);
        }
        if (returnThinking) {
            aiMessage.thinking(parser.thinking());
        }
        return ChatResponse.builder()
                .aiMessage(aiMessage.build())
                .tokenUsage(JitLLMConversions.toTokenUsage(result))
                .finishReason(JitLLMConversions.toFinishReason(result.finishReason()))
                .build();
    }

    boolean returnThinking() {
        return returnThinking;
    }

    /**
     * Returns the default request parameters of this model.
     *
     * @return the default request parameters
     */
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    /**
     * Returns the listeners of this model.
     *
     * @return the listeners
     */
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    /**
     * Releases the memory held by the model, including the device memory when running on a GPU.
     * Waits for a running generation to finish first.
     * After the model is closed, every request fails with an {@link IllegalStateException}.
     */
    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            if (session != null) {
                session.close(); // the engine requires the session to be closed before the model
            }
            model.close();
        } finally {
            lock.unlock();
        }
    }
}
