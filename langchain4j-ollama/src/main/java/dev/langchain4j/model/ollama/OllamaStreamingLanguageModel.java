package dev.langchain4j.model.ollama;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 * <br>
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama API parameters</a>.
 */
public class OllamaStreamingLanguageModel implements StreamingLanguageModel {

    private final OllamaClient client;
    private final String modelName;
    private final Double temperature;
    private final Integer topK;
    private final Double topP;
    private final Double repeatPenalty;
    private final Integer seed;
    private final Integer numPredict;
    private final List<String> stop;
    private final String format;

    @Builder
    public OllamaStreamingLanguageModel(String baseUrl,
                                        String modelName,
                                        Double temperature,
                                        Integer topK,
                                        Double topP,
                                        Double repeatPenalty,
                                        Integer seed,
                                        Integer numPredict,
                                        List<String> stop,
                                        String format,
                                        Duration timeout) {
        this.client = OllamaClient.builder()
                .baseUrl(baseUrl)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.topK = topK;
        this.topP = topP;
        this.repeatPenalty = repeatPenalty;
        this.seed = seed;
        this.numPredict = numPredict;
        this.stop = stop;
        this.format = format;
    }

    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {
        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .options(Options.builder()
                        .temperature(temperature)
                        .topK(topK)
                        .topP(topP)
                        .repeatPenalty(repeatPenalty)
                        .seed(seed)
                        .numPredict(numPredict)
                        .stop(stop)
                        .build())
                .format(format)
                .stream(true)
                .build();

        client.streamingCompletion(request, handler);
    }
}
