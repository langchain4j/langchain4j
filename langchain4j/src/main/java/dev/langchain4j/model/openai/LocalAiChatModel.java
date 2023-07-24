package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static dev.langchain4j.model.openai.OpenAiHelper.*;
import static java.util.Arrays.asList;

public class LocalAiChatModel implements ChatLanguageModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final Integer maxRetries;

    public static void main(String[] args) {
//        ToolSpecification toolSpec = ToolSpecification.builder()
//                .name("add")
//                .addParameter("a", INTEGER)
//                .addParameter("b", INTEGER)
//                .build();

        LocalAiChatModel model = LocalAiChatModel.builder()
                .timeout(Duration.ofSeconds(60))
                .url("http://localhost:8004")
                .maxTokens(100)
                .build();

        AiMessage answer = model.sendUserMessage("tell me a joke");

        System.out.println(answer);
    }

    @Builder
    public LocalAiChatModel(String modelName,
                            String url,
                            Double temperature,
                            Double topP,
                            Integer maxTokens,
                            Double presencePenalty,
                            Double frequencyPenalty,
                            Duration timeout,
                            Integer maxRetries,
                            Boolean logRequests,
                            Boolean logResponses) {

        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
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
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.maxRetries = maxRetries;
    }

    @Override
    public AiMessage sendUserMessage(String userMessage) {
        return sendUserMessage(userMessage(userMessage));
    }

    @Override
    public AiMessage sendUserMessage(UserMessage userMessage) {
        return sendMessages(userMessage);
    }

    @Override
    public AiMessage sendUserMessage(Object structuredPrompt) {
        Prompt prompt = toPrompt(structuredPrompt);
        return sendUserMessage(prompt.toUserMessage());
    }

    @Override
    public AiMessage sendMessages(ChatMessage... messages) {
        return sendMessages(asList(messages));
    }

    @Override
    public AiMessage sendMessages(List<ChatMessage> messages) {
        return sendMessages(messages, null);
    }

    @Override
    public AiMessage sendMessages(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(modelName)
                .messages(toOpenAiMessages(messages))
                .functions(toFunctions(toolSpecifications))
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .presencePenalty(presencePenalty)
                .frequencyPenalty(frequencyPenalty)
                .build();

        ChatCompletionResponse response = withRetry(() -> client.chatCompletion(request).execute(), maxRetries);

        return aiMessageFrom(response);
    }
}
