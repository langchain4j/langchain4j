package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.ai4j.openai4j.completion.CompletionResponse;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.language.LanguageModel;
import lombok.Builder;

import java.time.Duration;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static java.time.Duration.ofSeconds;

public class LocalAiLanguageModel implements LanguageModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Integer maxTokens;
    private final Integer maxRetries;

    public static void main(String[] args) {
        LocalAiLanguageModel model = LocalAiLanguageModel.builder()
//                .modelName("llama-2-13b-chat.ggmlv3.q2_K.bin")
//                .modelName("llama-2-13b-chat.ggmlv3.q3_K_S.bin")
//                .modelName("llama-2-13b-chat.ggmlv3.q3_K_L.bin")
//                .modelName("llama-2-13b-chat.ggmlv3.q4_0.bin")
//                .modelName("llama-2-13b-chat.ggmlv3.q4_1.bin")
//                .modelName("llama-2-13b-chat.ggmlv3.q4_K_M.bin")
//                .modelName("llama-2-13b-chat.ggmlv3.q5_0.bin")
//                .modelName("llama-2-13b-chat.ggmlv3.q8_0.bin")
                .maxTokens(10) // TODO fails without it?
                .timeout(ofSeconds(60))
                .url("http://localhost:8004")
                .build();

        String prompt = "[INST] <<SYS>>\n" +
                "You are a helpful, respectful and honest assistant. Always answer as helpfully as possible, while being safe.  Your answers should not include any harmful, unethical, racist, sexist, toxic, dangerous, or illegal content. Please ensure that your responses are socially unbiased and positive in nature. If a question does not make any sense, or is not factually coherent, explain why instead of answering something not correct. If you don't know the answer to a question, please don't share false information.\n" +
                "<</SYS>>\n" +
                "[/INST]";

        System.out.println(model.process(prompt));
    }

    @Builder
    public LocalAiLanguageModel(String url,
                                String modelName,
                                Double temperature,
                                Integer maxTokens,
                                Duration timeout,
                                Integer maxRetries,
                                Boolean logRequests,
                                Boolean logResponses) {

        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? ofSeconds(15) : timeout;
        maxRetries = maxRetries == null ? 3 : maxRetries;

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
        this.maxTokens = maxTokens;
        this.maxRetries = maxRetries;
    }

    @Override
    public String process(String text) {

        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(text)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .build();

        CompletionResponse response = withRetry(() -> client.completion(request).execute(), maxRetries);

        return response.text();
    }

    @Override
    public String process(Prompt prompt) {
        return this.process(prompt.text());
    }

    @Override
    public String process(Object structuredPrompt) {
        return process(toPrompt(structuredPrompt));
    }
}
