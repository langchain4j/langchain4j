package dev.langchain4j.model.alephalpha;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.lang.Boolean.FALSE;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.output.Response;
import io.github.heezer.alephalpha.client.Client;
import io.github.heezer.alephalpha.client.completion.CompletionRequest;
import io.github.heezer.alephalpha.client.completion.CompletionResponse;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;

/**
 * TODO
 * Represents an OpenAI language model with a chat completion interface, such as gpt-3.5-turbo and gpt-4.
 * You can find description of parameters <a href="https://platform.openai.com/docs/api-reference/chat/create">here</a>.
 */
public class AlephAlphaChatModel implements ChatLanguageModel, TokenCountEstimator {

    private final Client client;
    private final String model;
    //    private final Double temperature;
    //    private final Double topP;
    //    private final List<String> stop;
    private final Integer maxTokens;
    //    private final Double presencePenalty;
    //    private final Double frequencyPenalty;
    //    private final Map<String, Integer> logitBias;
    //    private final String responseFormat;
    //    private final Integer seed;
    //    private final String user;
    private final Integer maxRetries;
    private final List<String> stopSequences;

    //    private final Tokenizer tokenizer;

    @Builder
    public AlephAlphaChatModel(
        String apiKey,
        String modelName,
        //        Double temperature,
        //        Double topP,
        //        List<String> stop,
        Integer maxTokens,
        List<String> stopSequences,
        //        Double presencePenalty,
        //        Double frequencyPenalty,
        //        Map<String, Integer> logitBias,
        //        String responseFormat,
        //        Integer seed,
        //        String user,
        Duration timeout,
        Integer maxRetries,
        Proxy proxy,
        Boolean logRequests,
        Boolean logResponses
        //        Tokenizer tokenizer
    ) {
        timeout = getOrDefault(timeout, ofSeconds(60));

        Client.ClientBuilder cBuilder = Client
            .builder()
            .apiKey(apiKey)
            .callTimeout(timeout)
            .connectTimeout(timeout)
            .readTimeout(timeout)
            .writeTimeout(timeout)
            .proxy(proxy);

        if (getOrDefault(logRequests, FALSE)) {
            cBuilder.logRequests();
        }

        if (getOrDefault(logResponses, FALSE)) {
            cBuilder.logResponses();
        }

        this.client = cBuilder.build();
        this.model = modelName;
        //        this.temperature = getOrDefault(temperature, 0.7);
        //        this.topP = topP;
        //        this.stop = stop;
        this.maxTokens = maxTokens;
        //        this.presencePenalty = presencePenalty;
        //        this.frequencyPenalty = frequencyPenalty;
        //        this.logitBias = logitBias;
        //        this.responseFormat = responseFormat;
        //        this.seed = seed;
        //        this.user = user;
        this.maxRetries = getOrDefault(maxRetries, 3);
        //        this.tokenizer = getOrDefault(tokenizer, () -> new OpenAiTokenizer(this.modelName));
        this.stopSequences = stopSequences;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        if (messages == null) {
            throw new IllegalArgumentException("Messages are null");
        } else if (messages.size() > 1) {
            throw new IllegalArgumentException("Multiple messages are currently not supported by this model");
        }

        // TBD how to deal with type?
        CompletionRequest.CompletionRequestBuilder requestBuilder = CompletionRequest
            .builder()
            .prompt(messages.get(0).text())
            .stopSequences(stopSequences);

        if (model != null) {
            requestBuilder.model(model);
        }

        if (maxTokens != null) {
            requestBuilder.maximumTokens(maxTokens);
        }

        CompletionResponse response = withRetry(() -> client.complete(requestBuilder.build()), maxRetries);

        return Response.from(
            aiMessage(
                response
                    .getCompletions()
                    .stream()
                    .map(CompletionResponse.Completion::getCompletion)
                    .collect(Collectors.joining())
            )
            /* TBD implement it too,
            tokenUsageFrom(response.usage()),
            finishReasonFrom(response.choices().get(0).finishReason())
        */
        );
    }

    //    private Response<AiMessage> generate(
    //        List<ChatMessage> messages,
    //        List<ToolSpecification> toolSpecifications,
    //        ToolSpecification toolThatMustBeExecuted
    //    ) {
    //        CompletionRequest.CompletionRequestBuilder requestBuilder = CompletionRequest
    //            .builder()
    //            .p.messages(toOpenAiMessages(messages))
    //            .temperature(temperature)
    //            .topP(topP)
    //            .stop(stop)
    //            .maxTokens(maxTokens)
    //            .presencePenalty(presencePenalty)
    //            .frequencyPenalty(frequencyPenalty)
    //            .logitBias(logitBias)
    //            .responseFormat(responseFormat)
    //            .seed(seed)
    //            .user(user);
    //
    //        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
    //            requestBuilder.tools(toTools(toolSpecifications));
    //        }
    //        if (toolThatMustBeExecuted != null) {
    //            requestBuilder.toolChoice(toolThatMustBeExecuted.name());
    //        }
    //
    //        ChatCompletionRequest request = requestBuilder.build();
    //
    //        ChatCompletionResponse response = withRetry(() -> client.chatCompletion(request).execute(), maxRetries);
    //
    //        return Response.from(
    //            aiMessageFrom(response),
    //            tokenUsageFrom(response.usage()),
    //            finishReasonFrom(response.choices().get(0).finishReason())
    //        );
    //    }

    @Override
    public int estimateTokenCount(List<ChatMessage> messages) {
        // TBD
        return 0;
        //        return tokenizer.estimateTokenCountInMessages(messages);
    }

    public static AlephAlphaChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }
}
