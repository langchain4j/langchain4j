package dev.langchain4j.model.gpullama3;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import org.beehive.gpullama3.auxiliary.LastRunMetrics;
import org.beehive.gpullama3.inference.sampler.Sampler;
import org.beehive.gpullama3.inference.state.State;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.format.ChatFormat;
import org.beehive.gpullama3.model.loader.ModelLoader;
import org.beehive.gpullama3.tornadovm.TornadoVMMasterPlan;

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

        processPromptMessages(request.messages());

        Set<Integer> stopTokens = chatFormat.getStopTokens();
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
            return "Ran out of context length...\n Increase context length with by passing to llama-tornado --max-tokens XXX";
        } else {
            return responseText;
        }
    }
    // @formatter:on

    public void printLastMetrics() {
        LastRunMetrics.printMetrics();
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
    private void processPromptMessages(List<ChatMessage> messageList) {
        for (ChatMessage msg : messageList) {
            if (msg instanceof UserMessage userMessage) {
                promptTokens.addAll(chatFormat.encodeMessage(
                        new ChatFormat.Message(ChatFormat.Role.USER, userMessage.singleText())));
            } else if (msg instanceof SystemMessage systemMessage && model.shouldAddSystemPrompt()) {
                promptTokens.addAll(
                        chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, systemMessage.text())));
            } else if (msg instanceof AiMessage aiMessage) {
                promptTokens.addAll(
                        chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, aiMessage.text())));
            }
        }

        // EncodeHeader to prime the model to start generating a new assistant response.
        promptTokens.addAll(chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")));
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
                    System.err.println("Error while cleaning up TornadoVM resources: " + e.getMessage());
                }
            }
        }
    }
}
