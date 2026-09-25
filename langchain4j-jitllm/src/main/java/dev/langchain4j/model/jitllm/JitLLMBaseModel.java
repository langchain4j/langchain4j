package dev.langchain4j.model.jitllm;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.beehive.jitllm.api.GenerationEvent;
import org.beehive.jitllm.api.GenerationRequest;
import org.beehive.jitllm.api.GenerationResult;
import org.beehive.jitllm.api.GenerationSession;
import org.beehive.jitllm.api.LocalModel;
import org.beehive.jitllm.api.LocalModels;
import org.beehive.jitllm.api.ModelOptions;
import org.beehive.jitllm.api.TextGenerationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for JitLLM chat models.
 *
 * <p>Uses only jitLLM's public API ({@code org.beehive.jitllm.api}): {@code LocalModels.load}
 * loads the model and owns its accelerator resources, a {@code GenerationSession} generates, and
 * chat templating and tool encoding are the engine's. LangChain4j sends the whole conversation on
 * every request; the session reuses the encoded prefix when one request extends the last, so that
 * costs nothing beyond the first turn.
 */
abstract class JitLLMBaseModel implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(JitLLMBaseModel.class);

    private LocalModel model;

    /**
     * One session for the model's life, created on first use.
     *
     * <p><b>Not one per request.</b> On the accelerator path a session builds its own execution
     * plan, and a plan holds its own device copy of the weights, so a session per request exhausts
     * device memory within a few calls — measured, as a {@code TornadoOutOfMemoryException} on the
     * third case of the inherited suite. The conversation is sent whole every time regardless, and
     * the session reuses the encoded prefix when one request extends the last.
     */
    private GenerationSession session;

    private Integer maxTokens;
    private Double temperature;
    private Double topP;
    private Integer seed;
    private boolean closed = false;
    private volatile GenerationResult lastResult;

    /**
     * Loads the engine model this instance generates through.
     *
     * @param modelPath the GGUF file
     * @param temperature sampling temperature
     * @param topP nucleus sampling parameter
     * @param seed sampling seed
     * @param maxTokens the context length, and the default generation budget
     * @param onGPU whether to run on an accelerator; the backend is whichever one the TornadoVM
     *              SDK the JVM was started with provides, which requires {@code -Duse.tornadovm=true}
     */
    public void init(Path modelPath, Double temperature, Double topP, Integer seed, Integer maxTokens, Boolean onGPU) {
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.topP = topP;
        this.seed = seed;

        // CPU is an explicit backend. For the accelerator path no backend is named: naming one
        // (say CUDA) makes the engine reject any SDK built for another (OpenCL, Metal), whereas
        // leaving it unset runs on whichever backend the SDK provides, as -Duse.tornadovm selects.
        ModelOptions.Builder options = ModelOptions.builder().contextLength(maxTokens);
        if (onGPU) {
            if (!Boolean.getBoolean("use.tornadovm")) {
                throw new IllegalStateException("onGPU(true) needs the JVM started through TornadoVM with"
                        + " -Duse.tornadovm=true; use onGPU(false) to run on the CPU");
            }
        } else {
            options.backend(org.beehive.jitllm.runtime.backend.BackendId.CPU);
        }
        try {
            this.model = LocalModels.load(modelPath, options.build());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load model from " + modelPath, e);
        }
    }

    /**
     * The loaded engine model.
     *
     * @return the model, or {@code null} before {@link #init} has run
     */
    public LocalModel getModel() {
        return model;
    }

    /**
     * Generates a chat response.
     *
     * @param request the request, whose messages are the whole conversation
     * @return the engine's result for this request
     * @param onEvent receives one ordered event per emitted completion token — its id and the text
     *                it completed — or {@code null} for a non-streaming call
     */
    public GenerationResult modelResponse(ChatRequest request, Consumer<GenerationEvent> onEvent) {
        List<ToolSpecification> tools = request.toolSpecifications();

        GenerationRequest.Builder builder = GenerationRequest.builder()
                .messages(JitLLMConversions.toEngineMessages(request.messages()))
                .maxNewTokens(maxTokens)
                .temperature(temperature.floatValue())
                .topP(topP.floatValue());
        if (seed != null) {
            builder.seed(seed);
        }
        if (!tools.isEmpty()) {
            builder.tools(JitLLMConversions.toEngineTools(tools));
        }
        if (onEvent != null) {
            builder.onEvent(onEvent);
        }

        GenerationSession session = session();
        // LangChain4j's ChatModel is stateless: the caller owns the conversation and sends the
        // whole of it on every request. The session retains its own history too, so without this
        // each request would append the whole conversation again to the previous one and the
        // context would grow without bound across calls.
        session.reset();
        GenerationResult result = session.generate(builder.build());
        lastResult = result;
        // A truncated response is still returned in full, with FinishReason.LENGTH (#5959).
        switch (result.finishReason()) {
            case MAX_TOKENS ->
                log.warn(
                        "Generation stopped after reaching maxTokens ({}), so the response is truncated. "
                                + "Increase maxTokens(...) on the model builder to get a complete response.",
                        maxTokens);
            case CONTEXT_FULL ->
                log.warn("Generation stopped because the context window is full, so the response is truncated. "
                        + "Increase maxTokens(...) on the model builder, which also sets the context length.");
            default -> {}
        }
        return result;
    }

    /**
     * Logs the prompt and generation token counts and rates of the last request, at INFO.
     */
    public void printLastMetrics() {
        GenerationResult result = lastResult;
        if (result == null) {
            log.info("jitLLM: no request has completed yet");
        } else {
            log.info("jitLLM: {}", result.timings());
        }
    }

    private synchronized GenerationSession session() {
        if (session == null) {
            session = ((TextGenerationModel) model).newSession();
        }
        return session;
    }

    /**
     * A fresh identifier for a tool call the model requested.
     *
     * @return the identifier
     */
    protected static String generateCallId() {
        return JitLLMConversions.generateCallId();
    }

    /**
     * Normalizes tool-call argument JSON so equivalent spellings compare equal.
     *
     * @param json the arguments as the model emitted them
     * @return the normalized form
     */
    protected static String normalizeJson(String json) {
        return JitLLMConversions.normalizeJson(json);
    }

    /**
     * Releases the engine resources this model holds. The session closes before the model, which
     * the engine requires.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (session != null) {
            session.close();
            session = null;
        }
        if (model != null) {
            model.close();
        }
    }
}
