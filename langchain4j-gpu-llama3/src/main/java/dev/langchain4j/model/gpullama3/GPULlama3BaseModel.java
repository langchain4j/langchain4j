package dev.langchain4j.model.gpullama3;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import org.beehive.gpullama3.auxiliary.RunMetrics;
import org.beehive.gpullama3.inference.sampler.Sampler;
import org.beehive.gpullama3.inference.state.State;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.format.ChatFormat;
import org.beehive.gpullama3.model.format.ToolCallExtract;
import org.beehive.gpullama3.model.loader.ModelLoader;
import org.beehive.gpullama3.tornadovm.TornadoVMMasterPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for GPULlama3 chat models providing core functionality for conversation management and token generation.
 *
 * <p>This class handles:
 * <ul>
 *   <li>Model initialization and configuration</li>
 *   <li>Conversation state management (stateless approach)</li>
 *   <li>Token encoding and decoding using proper chat formats</li>
 *   <li>Both CPU and GPU execution modes</li>
 *   <li>System and user message processing</li>
 *   <li>Automatic resource cleanup using modern Cleaner API</li>
 * </ul>
 *
 *
 * <p>GPU resources are automatically cleaned up when the model is garbage collected,
 * but can also be manually freed using {@link #freeTornadoVMGPUResources()} or
 * {@link #close()}.
 *
 * <p>Subclasses should implement specific model interfaces (e.g., ChatModel or
 * StreamingChatModel) while leveraging this base functionality.
 */
abstract class GPULlama3BaseModel implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(GPULlama3BaseModel.class);
    private static final Cleaner CLEANER = Cleaner.create();

    private final Integer START_POSITION = 0;
    State state;
    List<Integer> promptTokens;
    ChatFormat chatFormat;
    TornadoVMMasterPlan tornadoVMPlan;
    private Integer maxTokens;
    private Boolean onGPU;
    private Model model;
    private Sampler sampler;
    /** Cleaner for automatic resource management */
    private Cleaner.Cleanable cleanable;
    /** Flag to track if resources have been closed */
    private boolean closed = false;

    public void init(Path modelPath, Double temperature, Double topP, Integer seed, Integer maxTokens, Boolean onGPU) {
        this.maxTokens = maxTokens;
        this.onGPU = onGPU;

        try {
            this.model = ModelLoader.loadModel(modelPath, maxTokens, true, onGPU);
            this.state = model.createNewState();
            this.sampler = Sampler.selectSampler(
                    model.configuration().vocabularySize(), temperature.floatValue(), topP.floatValue(), seed);

            this.chatFormat = model.chatFormat();

            if (onGPU) {
                tornadoVMPlan = TornadoVMMasterPlan.initializeTornadoVMPlan(state, model);
                // Register automatic cleanup with Cleaner
                this.cleanable = CLEANER.register(this, new TornadoVMCleanupAction(tornadoVMPlan));
            } else {
                this.cleanable = null;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load model from " + modelPath, e);
        }
    }
    // @formatter:off

    public Model getModel() {
        return model;
    }

    public Sampler getSampler() {
        return sampler;
    }

    /**
     * Generates a chat response from the model.
     * Used by GPULlama3StreamingChatModel.
     * @param request
     * @param tokenConsumer
     * @return
     */
    public String modelResponse(ChatRequest request, IntConsumer tokenConsumer) {
        this.promptTokens = new ArrayList<>();

        if (model.shouldAddBeginOfText()) {
            promptTokens.add(chatFormat.getBeginOfText());
        }

        List<ToolSpecification> tools = request.toolSpecifications();
        String toolsJson = tools.isEmpty() ? null : buildToolsJson(tools);
        if (toolsJson != null && !chatFormat.supportsToolCalling()) {
            throw new UnsupportedOperationException("Tool calling is not supported for model format: "
                    + chatFormat.getClass().getSimpleName());
        }

        processPromptMessages(request.messages(), toolsJson);

        Set<Integer> stopTokens = toolsJson == null ? chatFormat.getStopTokens() : chatFormat.getToolAwareStopTokens();
        List<Integer> responseTokens;

        if (onGPU) {
            responseTokens = model.generateTokensGPU(
                    state,
                    START_POSITION,
                    promptTokens.subList(START_POSITION, promptTokens.size()),
                    stopTokens,
                    maxTokens,
                    sampler,
                    false,
                    tokenConsumer,
                    tornadoVMPlan);
        } else {
            responseTokens = model.generateTokens(
                    state,
                    START_POSITION,
                    promptTokens.subList(START_POSITION, promptTokens.size()),
                    stopTokens,
                    maxTokens,
                    sampler,
                    false,
                    tokenConsumer);
        }

        Integer stopToken = null;
        if (!responseTokens.isEmpty() && stopTokens.contains(responseTokens.getLast())) {
            stopToken = responseTokens.getLast();
            responseTokens.removeLast();
        }

        String responseText = model.tokenizer().decode(responseTokens);

        // Add the response content tokens to conversation history
        promptTokens.addAll(responseTokens);

        // Add the stop token to complete the message
        if (stopToken != null) {
            promptTokens.add(stopToken);
        }

        if (stopToken == null) {
            log.warn(
                    "Generation stopped after reaching maxTokens ({}), so the response is truncated. "
                            + "Increase maxTokens(...) on the model builder to get a complete response.",
                    maxTokens);
        }

        return responseText;
    }
    // @formatter:on

    public void printLastMetrics() {
        RunMetrics.printMetrics();
    }

    /**
     * Processes chat messages and encodes them into prompt tokens.
     *
     * <p>This method iterates through the provided chat messages and converts them into
     * encoded tokens based on their message type and role. The encoded tokens are appended to the {@link #promptTokens} list for later use in model inference.
     *
     * <p>Supported message types:
     * <ul>
     *   <li>{@link UserMessage} - Encoded with the USER role</li>
     *   <li>{@link SystemMessage} - Encoded with the SYSTEM role (only if {@link Model#shouldAddSystemPrompt()} returns true)</li>
     *   <li>{@link AiMessage} - Encoded with the ASSISTANT role</li>
     * </ul>
     *
     * <p>Each message is encoded using the configured {@link ChatFormat}, which ensures
     * proper formatting according to the model's requirements (e.g., Llama 3 chat format).
     *
     * @param messageList
     *         the list of chat messages to process and encode
     * @see ChatFormat#encodeMessage(ChatFormat.Message)
     * @see UserMessage
     * @see SystemMessage
     * @see AiMessage
     */
    private void processPromptMessages(List<ChatMessage> messageList, String toolsJson) {
        boolean toolsInjected = false;
        boolean injectToolsInUserMessage = toolsJson != null && chatFormat.injectsToolsInUserMessage();
        boolean hasSystemMessage = messageList.stream().anyMatch(SystemMessage.class::isInstance);

        if (toolsJson != null && !injectToolsInUserMessage && !hasSystemMessage && model.shouldAddSystemPrompt()) {
            promptTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(
                    ChatFormat.Role.SYSTEM,
                    chatFormat.toolSystemPromptSuffix(toolsJson).stripLeading())));
            toolsInjected = true;
        }

        for (ChatMessage msg : messageList) {
            if (msg instanceof UserMessage userMessage) {
                String content = userMessage.singleText();
                if (injectToolsInUserMessage && !toolsInjected) {
                    content = chatFormat.toolFirstUserMessagePrefix(toolsJson) + content;
                    toolsInjected = true;
                }
                promptTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.USER, content)));
            } else if (msg instanceof SystemMessage systemMessage && model.shouldAddSystemPrompt()) {
                String content = systemMessage.text();
                if (toolsJson != null) {
                    if (injectToolsInUserMessage) {
                        content = chatFormat.toolSystemMessagePrefix() + content;
                    } else {
                        content += chatFormat.toolSystemPromptSuffix(toolsJson);
                        toolsInjected = true;
                    }
                }
                promptTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, content)));
            } else if (msg instanceof AiMessage aiMessage) {
                if (aiMessage.hasToolExecutionRequests()) {
                    List<ToolCallExtract> toolCalls = aiMessage.toolExecutionRequests().stream()
                            .map(request -> new ToolCallExtract(
                                    request.name(), request.arguments(), java.util.Optional.ofNullable(request.id())))
                            .toList();
                    promptTokens.addAll(chatFormat.encodeToolCallAssistantTurn(toolCalls));
                } else {
                    promptTokens.addAll(chatFormat.encodeMessage(
                            new ChatFormat.Message(ChatFormat.Role.ASSISTANT, aiMessage.text())));
                }
            } else if (msg instanceof ToolExecutionResultMessage toolResultMessage) {
                promptTokens.addAll(chatFormat.encodeToolResultTurn(
                        toolResultMessage.id(),
                        toolResultMessage.toolName(),
                        unwrapToolResult(toolResultMessage.text())));
            }
        }

        // EncodeHeader to prime the model to start generating a new assistant response.
        promptTokens.addAll(chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")));
    }

    private String buildToolsJson(List<ToolSpecification> tools) {
        return buildToolMaps(tools).stream().map(Json::toJson).collect(Collectors.joining("\n\n"));
    }

    private static List<Map<String, Object>> buildToolMaps(List<ToolSpecification> tools) {
        List<Map<String, Object>> toolMaps = new ArrayList<>();
        for (ToolSpecification tool : tools) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            if (tool.description() != null) {
                function.put("description", tool.description());
            }
            function.put(
                    "parameters",
                    tool.parameters() == null
                            ? Map.of("type", "object", "properties", Map.of())
                            : JsonSchemaElementUtils.toMap(tool.parameters()));

            Map<String, Object> toolMap = new LinkedHashMap<>();
            toolMap.put("type", "function");
            toolMap.put("function", function);
            toolMaps.add(toolMap);
        }
        return toolMaps;
    }

    private static String unwrapToolResult(String text) {
        if (text == null) {
            return "";
        }
        if (text.startsWith("\"")) {
            try {
                return Json.fromJson(text, String.class);
            } catch (RuntimeException ignored) {
                // The result is not a JSON string literal; pass it through unchanged.
            }
        }
        return text;
    }

    protected static String generateCallId() {
        return "call_" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
    }

    protected static String normalizeJson(String json) {
        try {
            return Json.toJson(Json.fromJson(json, Object.class));
        } catch (RuntimeException ignored) {
            return json;
        }
    }

    /**
     * Manually releases GPU resources allocated by TornadoVM.
     *
     * <p>This method can be called explicitly to free resources immediately,
     * or will be called automatically when the model is garbage collected. It's safe to call this method multiple times.
     */
    public void freeTornadoVMGPUResources() {
        if (!closed && cleanable != null) {
            cleanable.clean();
            closed = true;
        }
    }

    /**
     * Closes the model and releases all associated resources.
     *
     * <p>This method implements AutoCloseable, allowing the model to be used
     * with try-with-resources statements for automatic resource management.
     */
    @Override
    public void close() {
        freeTornadoVMGPUResources();
    }

    /**
     * Cleanup action for TornadoVM resources that holds no reference to the model instance. This prevents memory leaks while ensuring resources are properly cleaned up.
     */
    private static class TornadoVMCleanupAction implements Runnable {
        private final TornadoVMMasterPlan plan;

        TornadoVMCleanupAction(TornadoVMMasterPlan plan) {
            this.plan = plan;
        }

        @Override
        public void run() {
            if (plan != null) {
                try {
                    plan.freeTornadoExecutionPlan();
                } catch (Exception e) {
                    log.error("Error while cleaning up TornadoVM resources", e);
                }
            }
        }
    }
}
