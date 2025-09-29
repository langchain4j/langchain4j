package dev.langchain4j.model.gpullama3;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import org.beehive.gpullama3.Options;
import org.beehive.gpullama3.auxiliary.LastRunMetrics;
import org.beehive.gpullama3.inference.sampler.Sampler;
import org.beehive.gpullama3.inference.state.State;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.format.ChatFormat;
import org.beehive.gpullama3.model.loader.ModelLoader;
import org.beehive.gpullama3.tornadovm.TornadoVMMasterPlan;

abstract class GPULlama3BaseModel {
    private Path modelPath;
    private Double temperature;
    private Double topP;
    private Integer seed;
    private Integer maxTokens;
    private Boolean onGPU;
    private Model model;
    private Sampler sampler;
    private Boolean stream;

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
            Boolean onGPU,
            Boolean stream) {
        this.maxTokens = maxTokens;
        this.onGPU = onGPU;
        this.modelPath = modelPath;
        this.temperature = temperature;
        this.topP = topP;
        this.seed = seed;
        this.stream = stream;

        try {
            this.model = ModelLoader.loadModel(modelPath, maxTokens, true, onGPU);
            this.sampler = Sampler.selectSampler(
                    model.configuration().vocabularySize(), temperature.floatValue(), topP.floatValue(), seed);

            this.state = model.createNewState();
            this.conversationTokens = new ArrayList<>();
            this.chatFormat = model.chatFormat();

            if (model.shouldAddBeginOfText()) {
                conversationTokens.add(chatFormat.getBeginOfText());
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

    public ChatResponse modelResponse(ChatRequest request) {

        Options options = new Options(
                modelPath,
                extractUserPrompt(request),
                extractSystemPrompt(request),
                null, // suffix
                false, // interactive
                temperature.floatValue(),
                topP.floatValue(),
                seed,
                maxTokens,
                stream, // streaming
                false, // echo
                onGPU);

        // String responseText = model.runInstructOnce(sampler, options);
        String responseText = modelStringResponse(request, null);
        // Create AI message from response
        AiMessage aiMessage = AiMessage.from(responseText);

        // Create and return chat response
        return ChatResponse.builder().aiMessage(aiMessage).build();
    }

    public String modelStringResponse(ChatRequest request, IntConsumer tokenConsumer) {
        Options options = new Options(
                modelPath,
                extractUserPrompt(request),
                extractSystemPrompt(request),
                null, // suffix
                false, // interactive
                temperature.floatValue(),
                topP.floatValue(),
                seed,
                maxTokens,
                stream, // streaming
                false, // echo
                onGPU);

        // String responseText = model.runInstructOnceLangChain4J(sampler, options, tokeCallBack);
        // return responseText;

        String systemText = extractSystemPrompt(request);
        if (model.shouldAddSystemPrompt() && !systemText.isEmpty()) {
            conversationTokens.addAll(
                    chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, systemText)));
        }
        String userText = extractUserPrompt(request);
        conversationTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.USER, userText)));
        // TODO: Include reasoning for Deepseek-R1-Distill-Qwen

        Set<Integer> stopTokens = chatFormat.getStopTokens();
        List<Integer> responseTokens;

        if (onGPU) {
            responseTokens = model.generateTokensGPU(
                    state,
                    startPosition,
                    conversationTokens.subList(startPosition, conversationTokens.size()),
                    stopTokens,
                    options.maxTokens(),
                    sampler,
                    options.echo(),
                    tokenConsumer,
                    tornadoVMPlan);
        } else {
            responseTokens = model.generateTokens(
                    state,
                    startPosition,
                    conversationTokens.subList(startPosition, conversationTokens.size()),
                    stopTokens,
                    options.maxTokens(),
                    sampler,
                    options.echo(),
                    tokenConsumer);
        }

        conversationTokens.addAll(responseTokens);
        startPosition = conversationTokens.size();
        Integer stopToken = null;
        if (!responseTokens.isEmpty() && stopTokens.contains(responseTokens.getLast())) {
            stopToken = responseTokens.getLast();
            responseTokens.removeLast();
        }
        String responseText = model.tokenizer().decode(responseTokens);

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
