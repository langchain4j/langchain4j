package dev.langchain4j.model.gpullama3;

import dev.langchain4j.model.chat.request.ChatRequest;
import java.io.IOException;
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
 * Abstract base class for GPULlama3 chat models providing core functionality for
 * conversation management and token generation.
 *
 * <p>This class handles:
 * <ul>
 *   <li>Model initialization and configuration</li>
 *   <li>Conversation state management (stateful approach)</li>
 *   <li>Token encoding and decoding using proper chat formats</li>
 *   <li>Both CPU and GPU execution modes</li>
 *   <li>System and user message processing</li>
 * </ul>
 *
 * <p>The class maintains conversation history across multiple requests, allowing
 * for natural multi-turn conversations without requiring clients to manage
 * conversation state explicitly.
 *
 * <p>Subclasses should implement specific model interfaces (e.g., ChatModel or
 * StreamingChatModel) while leveraging this base functionality.
 */
abstract class GPULlama3BaseModel {
    private Integer maxTokens;
    private Boolean onGPU;
    private Model model;
    private Sampler sampler;

    //
    int startPosition;
    State state;
    List<Integer> conversationTokens;
    ChatFormat chatFormat;
    TornadoVMMasterPlan tornadoVMPlan;

    private static String extractSystemPrompt(ChatRequest request) {
        return request.messages().stream()
                .filter(m -> m instanceof dev.langchain4j.data.message.SystemMessage)
                .map(m -> ((dev.langchain4j.data.message.SystemMessage) m).text())
                .findFirst()
                .orElse(""); // systemPrompt is optional
    }

    private static String extractUserPrompt(ChatRequest request) {
        return request.messages().stream()
                .filter(m -> m instanceof dev.langchain4j.data.message.UserMessage)
                .map(m -> ((dev.langchain4j.data.message.UserMessage) m).singleText())
                .reduce((first, second) -> second) // take the last user message
                .orElseThrow(() -> new IllegalArgumentException("ChatRequest has no UserMessage"));
    }

    // @formatter:off
    public void init(
            Path modelPath,
            Double temperature,
            Double topP,
            Integer seed,
            Integer maxTokens,
            Boolean onGPU) {
        this.maxTokens = maxTokens;
        this.onGPU = onGPU;

        try {
            this.model = ModelLoader.loadModel(modelPath, maxTokens, true, onGPU);
            this.sampler = Sampler.selectSampler(
                    model.configuration().vocabularySize(), temperature.floatValue(), topP.floatValue(), seed);

            this.state = model.createNewState();
            this.conversationTokens = new ArrayList<>();
            this.chatFormat = model.chatFormat();

            if (model.shouldAddBeginOfText()) {
                System.out.println("Adding BOS token to conversation history");
                conversationTokens.add(chatFormat.getBeginOfText());
            } else {
                System.out.println("Not adding BOS token to conversation history");
            }

            startPosition = 0;

            // we cannot add system message here, we add in modelResponse()

            if (onGPU) {
                tornadoVMPlan = TornadoVMMasterPlan.initializeTornadoVMPlan(state, model);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load model from " + modelPath, e);
        }
    }

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

        String systemText = extractSystemPrompt(request);
        if (model.shouldAddSystemPrompt() && !systemText.isEmpty()) {
            conversationTokens.addAll(
                    chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, systemText)));
        }

        String userText = extractUserPrompt(request);
        conversationTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.USER, userText)));

        // Prompt the model to generate the assistant response by adding the header**
        List<Integer> assistantHeader = chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, ""));
        conversationTokens.addAll(assistantHeader);
        Set<Integer> stopTokens = chatFormat.getStopTokens();
        List<Integer> responseTokens;

        if (onGPU) {
            responseTokens = model.generateTokensGPU(
                    state,
                    startPosition,
                    conversationTokens.subList(startPosition, conversationTokens.size()),
                    stopTokens,
                    maxTokens,
                    sampler,
                    false,
                    tokenConsumer,
                    tornadoVMPlan);
        } else {
            responseTokens = model.generateTokens(
                    state,
                    startPosition,
                    conversationTokens.subList(startPosition, conversationTokens.size()),
                    stopTokens,
                    maxTokens,
                    sampler,
                    false,
                    tokenConsumer);
        }

        Integer stopToken = null;
        if (!responseTokens.isEmpty() && stopTokens.contains(responseTokens.getLast())) {
            stopToken = responseTokens.getLast();
            // dbg
            //System.out.println("Found stop token at end: " + stopToken + " = '" + model.tokenizer().decode(List.of(stopToken)) + "'");
            responseTokens.removeLast();
        }

        String responseText = model.tokenizer().decode(responseTokens);

        // Add the response content tokens to conversation history
        conversationTokens.addAll(responseTokens);

        // Add the stop token to complete the message
        if (stopToken != null) {
            conversationTokens.add(stopToken);
        }

        startPosition = conversationTokens.size();

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

    public void freeTornadoVMGPUResources() {
        tornadoVMPlan.freeTornadoExecutionPlan();
    }
}
