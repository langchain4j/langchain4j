package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.ai4j.openai4j.completion.CompletionResponse;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.language.TokenCountEstimator;
import dev.langchain4j.model.output.Result;
import lombok.Builder;

import java.time.Duration;

import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static dev.langchain4j.model.openai.OpenAiModelName.TEXT_DAVINCI_003;

public class OpenAiLanguageModel implements LanguageModel, TokenCountEstimator {

    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final OpenAiTokenizer tokenizer;

    @Builder
    public OpenAiLanguageModel(String apiKey,
                               String modelName,
                               Double temperature,
                               Duration timeout,
                               Boolean logRequests,
                               Boolean logResponses) {
        this.client = OpenAiClient.builder()
                .apiKey(apiKey)
                .callTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .connectTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .readTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .writeTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = modelName == null ? TEXT_DAVINCI_003 : modelName;
        this.temperature = temperature == null ? DEFAULT_TEMPERATURE : temperature;
        this.tokenizer = new OpenAiTokenizer(this.modelName);
    }

    @Override
    public Result<String> process(String text) {

        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(text)
                .temperature(temperature)
                .build();

        CompletionResponse response = client.completion(request).execute();

        return Result.from(response.text());
    }

    @Override
    public Result<String> process(Prompt prompt) {
        return this.process(prompt.text());
    }

    @Override
    public Result<String> process(Object structuredPrompt) {
        return process(toPrompt(structuredPrompt));
    }

    @Override
    public int estimateTokenCount(String prompt) {
        return tokenizer.countTokens(prompt);
    }

    @Override
    public int estimateTokenCount(Prompt prompt) {
        return estimateTokenCount(prompt.text());
    }

    @Override
    public int estimateTokenCount(Object structuredPrompt) {
        return estimateTokenCount(toPrompt(structuredPrompt));
    }

    @Override
    public int estimateTokenCount(TextSegment textSegment) {
        return estimateTokenCount(textSegment.text());
    }

    public static OpenAiLanguageModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }
}
