package dev.langchain4j.model.gpullama3;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.beehive.gpullama3.LlamaApp;
import org.beehive.gpullama3.Options;
import org.beehive.gpullama3.auxiliary.LastRunMetrics;
import org.beehive.gpullama3.inference.sampler.Sampler;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.loader.ModelLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

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

    private static String extractSystemPrompt(ChatRequest request) {
        return request.messages().stream().filter(m -> m instanceof dev.langchain4j.data.message.SystemMessage).map(m -> ((dev.langchain4j.data.message.SystemMessage) m).text()).findFirst()
                .orElse(null); // systemPrompt is optional
    }

    private static String extractUserPrompt(ChatRequest request) {
        return request.messages().stream().filter(m -> m instanceof dev.langchain4j.data.message.UserMessage).map(m -> ((dev.langchain4j.data.message.UserMessage) m).singleText())
                .reduce((first, second) -> second) // take the last user message
                .orElseThrow(() -> new IllegalArgumentException("ChatRequest has no UserMessage"));
    }

    // @formatter:off
    public void init(Path modelPath,
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
            this.sampler = LlamaApp.selectSampler(
                    model.configuration().vocabularySize(),
                    temperature.floatValue(), topP.floatValue(), seed
            );
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

        Options options = new Options(modelPath,
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

        String responseText = model.runInstructOnce(sampler, options);
        // Create AI message from response
        AiMessage aiMessage = AiMessage.from(responseText);

        // Create and return chat response
        return ChatResponse.builder().aiMessage(aiMessage).build();
    }

    public String modelStringResponse(ChatRequest request, Consumer<String> tokeCallBack) {
        Options options = new Options(modelPath,
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

        String responseText = model.runInstructOnceLangChain4J(sampler, options, tokeCallBack);
        return responseText;
    }
    // @formatter:on

    public void printLastMetrics() {
        LastRunMetrics.printMetrics();
    }
}
