package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.language.TokenCountEstimator;
import lombok.Builder;

import java.time.Duration;

import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static dev.langchain4j.model.openai.OpenAiModelName.TEXT_DAVINCI_003;
import static java.time.Duration.ofSeconds;

public class OpenAiStreamingLanguageModel implements StreamingLanguageModel, TokenCountEstimator {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final OpenAiTokenizer tokenizer;

    @Builder
    public OpenAiStreamingLanguageModel(String apiKey,
                                        String modelName,
                                        Double temperature,
                                        Duration timeout,
                                        Boolean logRequests,
                                        Boolean logResponses) {


        modelName = modelName == null ? TEXT_DAVINCI_003 : modelName;
        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? ofSeconds(15) : timeout;

        this.client = OpenAiClient.builder()
                .apiKey(apiKey)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = modelName;
        this.temperature = temperature;
        this.tokenizer = new OpenAiTokenizer(this.modelName);
    }

    @Override
    public void process(String text, StreamingResponseHandler handler) {
        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(text)
                .temperature(temperature)
                .build();

        client.completion(request)
                .onPartialResponse(partialResponse -> {
                    String partialResponseText = partialResponse.text();
                    if (partialResponseText != null) {
                        handler.onNext(partialResponseText);
                    }
                })
                .onComplete(handler::onComplete)
                .onError(handler::onError)
                .execute();
    }

    @Override
    public void process(Prompt prompt, StreamingResponseHandler handler) {
        process(prompt.text(), handler);
    }

    @Override
    public void process(Object structuredPrompt, StreamingResponseHandler handler) {
        process(toPrompt(structuredPrompt), handler);
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

    public static OpenAiStreamingLanguageModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }
}
