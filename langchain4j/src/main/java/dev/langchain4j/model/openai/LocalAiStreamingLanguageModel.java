package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.language.StreamingLanguageModel;
import lombok.Builder;

import java.time.Duration;

import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static dev.langchain4j.model.openai.OpenAiModelName.TEXT_DAVINCI_003;
import static java.time.Duration.ofSeconds;

public class LocalAiStreamingLanguageModel implements StreamingLanguageModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;


    public static void main(String[] args) {

        LocalAiStreamingLanguageModel.builder()
                .modelName("ggml-gpt4all-j-v1.3-groovy.bin")
                .timeout(Duration.ofSeconds(60))
                .url("http://localhost:8145")
                .build()
                .process("hello", new StreamingResponseHandler() {

                    @Override
                    public void onNext(String token) {
                        System.out.println(token);
                    }

                    @Override
                    public void onError(Throwable error) {
                        System.out.println("ERROR here");
                    }
                });
    }

    @Builder
    public LocalAiStreamingLanguageModel(String modelName,
                                         String url,
                                         Double temperature,
                                         Duration timeout,
                                         Boolean logRequests,
                                         Boolean logResponses) {


        modelName = modelName == null ? TEXT_DAVINCI_003 : modelName;
        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? ofSeconds(30) : timeout;

        this.client = OpenAiClient.builder()
                .apiKey("not needed")
                .url(url)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = modelName;
        this.temperature = temperature;
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
                    String token = partialResponse.text();
                    if (token != null) {
                        handler.onNext(token);
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
}
