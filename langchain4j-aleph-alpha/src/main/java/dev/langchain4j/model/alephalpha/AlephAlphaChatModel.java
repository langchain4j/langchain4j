package dev.langchain4j.model.alephalpha;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.lang.Boolean.FALSE;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
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
public class AlephAlphaChatModel implements ChatLanguageModel {

    private final Client client;
    private final String model;
    private final String hosting;
    private final Integer maxTokens;
    private final Integer minTokens;
    private Boolean echo;
    private Double temperature;
    private Integer topK;
    private Double topP;
    private Double presencePenalty;
    private Double frequencyPenalty;
    private Double sequencePenalty;
    private Integer sequencePenaltyMinLength;
    private Boolean repetitionPenaltiesIncludePrompt;
    private Boolean repetitionPenaltiesIncludeCompletion;
    private Boolean useMultiplicativePresencePenalty;
    private Boolean useMultiplicativeFrequencyPenalty;
    private Boolean useMultiplicativeSequencePenalty;
    private String penaltyBias;
    private List<String> penaltyExceptions;
    private Boolean penaltyExceptionsIncludeStopSequences;
    private Integer bestOf;
    private Integer n;
    private Object logitBias;
    private Integer logProbs;
    private List<String> stopSequences;
    private Boolean tokens;
    private Boolean rawCompletion;
    private Boolean disableOptimizations;
    private List<String> completionBiasInclusion;
    private Boolean completionBiasInclusionFirstTokenOnly;
    private List<String> completionBiasExclusion;
    private Boolean completionBiasExclusionFirstTokenOnly;
    private Double contextualControlThreshold;
    private Boolean controlLogAdditive;
    private final Integer maxRetries;

    @Builder
    public AlephAlphaChatModel(
        String apiKey,
        String modelName,
        String hosting,
        Integer maxTokens,
        Integer minTokens,
        Boolean echo,
        Double temperature,
        Integer topK,
        Double topP,
        Double presencePenalty,
        Double frequencyPenalty,
        Double sequencePenalty,
        Integer sequencePenaltyMinLength,
        Boolean repetitionPenaltiesIncludePrompt,
        Boolean repetitionPenaltiesIncludeCompletion,
        Boolean useMultiplicativePresencePenalty,
        Boolean useMultiplicativeFrequencyPenalty,
        Boolean useMultiplicativeSequencePenalty,
        String penaltyBias,
        List<String> penaltyExceptions,
        Boolean penaltyExceptionsIncludeStopSequences,
        Integer bestOf,
        Integer n,
        Object logitBias,
        Integer logProbs,
        List<String> stopSequences,
        Boolean tokens,
        Boolean rawCompletion,
        Boolean disableOptimizations,
        List<String> completionBiasInclusion,
        Boolean completionBiasInclusionFirstTokenOnly,
        List<String> completionBiasExclusion,
        Boolean completionBiasExclusionFirstTokenOnly,
        Double contextualControlThreshold,
        Boolean controlLogAdditive,
        Duration timeout,
        Integer maxRetries,
        Proxy proxy,
        Boolean logRequests,
        Boolean logResponses
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
        this.hosting = hosting;
        this.maxTokens = maxTokens;
        this.minTokens = minTokens;
        this.echo = echo;
        this.temperature = temperature;
        this.topK = topK;
        this.topP = topP;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.sequencePenalty = sequencePenalty;
        this.sequencePenaltyMinLength = sequencePenaltyMinLength;
        this.repetitionPenaltiesIncludePrompt = repetitionPenaltiesIncludePrompt;
        this.repetitionPenaltiesIncludeCompletion = repetitionPenaltiesIncludeCompletion;
        this.useMultiplicativePresencePenalty = useMultiplicativePresencePenalty;
        this.useMultiplicativeFrequencyPenalty = useMultiplicativeFrequencyPenalty;
        this.useMultiplicativeSequencePenalty = useMultiplicativeSequencePenalty;
        this.penaltyBias = penaltyBias;
        this.penaltyExceptions = penaltyExceptions;
        this.penaltyExceptionsIncludeStopSequences = penaltyExceptionsIncludeStopSequences;
        this.bestOf = bestOf;
        this.n = n;
        this.logitBias = logitBias;
        this.logProbs = logProbs;
        this.stopSequences = stopSequences;
        this.tokens = tokens;
        this.rawCompletion = rawCompletion;
        this.disableOptimizations = disableOptimizations;
        this.completionBiasInclusion = completionBiasInclusion;
        this.completionBiasInclusionFirstTokenOnly = completionBiasInclusionFirstTokenOnly;
        this.completionBiasExclusion = completionBiasExclusion;
        this.completionBiasExclusionFirstTokenOnly = completionBiasExclusionFirstTokenOnly;
        this.contextualControlThreshold = contextualControlThreshold;
        this.controlLogAdditive = controlLogAdditive;
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        if (messages == null) {
            throw new IllegalArgumentException("Messages are null");
        } else if (messages.size() > 1) {
            throw new IllegalArgumentException("Multiple messages are currently not supported by this model");
        }

        CompletionRequest.CompletionRequestBuilder requestBuilder = CompletionRequest.builder();

        if (model != null) {
            requestBuilder.model(model);
        }

        if (maxTokens != null) {
            requestBuilder.maximumTokens(maxTokens);
        }

        // TBD how to deal with type?
        requestBuilder
            .prompt(messages.get(0).text())
            .hosting(hosting)
            .minimumTokens(minTokens)
            .echo(echo)
            .temperature(temperature)
            .topK(topK)
            .topP(topP)
            .presencePenalty(presencePenalty)
            .frequencyPenalty(frequencyPenalty)
            .sequencePenalty(sequencePenalty)
            .sequencePenaltyMinLength(sequencePenaltyMinLength)
            .repetitionPenaltiesIncludePrompt(repetitionPenaltiesIncludePrompt)
            .repetitionPenaltiesIncludeCompletion(repetitionPenaltiesIncludeCompletion)
            .useMultiplicativePresencePenalty(useMultiplicativePresencePenalty)
            .useMultiplicativeFrequencyPenalty(useMultiplicativeFrequencyPenalty)
            .useMultiplicativeSequencePenalty(useMultiplicativeSequencePenalty)
            .penaltyBias(penaltyBias)
            .penaltyExceptions(penaltyExceptions)
            .penaltyExceptionsIncludeStopSequences(penaltyExceptionsIncludeStopSequences)
            .bestOf(bestOf)
            .n(n)
            .logitBias(logitBias)
            .logProbs(logProbs)
            .stopSequences(stopSequences)
            .tokens(tokens)
            .rawCompletion(rawCompletion)
            .disableOptimizations(disableOptimizations)
            .completionBiasExclusion(completionBiasInclusion)
            .completionBiasExclusionFirstTokenOnly(completionBiasInclusionFirstTokenOnly)
            .completionBiasExclusion(completionBiasExclusion)
            .completionBiasExclusionFirstTokenOnly(completionBiasExclusionFirstTokenOnly)
            .contextualControlThreshold(contextualControlThreshold)
            .controlLogAdditive(controlLogAdditive);

        CompletionResponse response = withRetry(() -> client.complete(requestBuilder.build()), maxRetries);

        return Response.from(messageFrom(response), tokenUsageFrom(response), finishReasonFrom(response));
    }

    private static AiMessage messageFrom(CompletionResponse response) {
        return aiMessage(
            response
                .getCompletions()
                .stream()
                .map(CompletionResponse.Completion::getCompletion)
                .collect(Collectors.joining())
        );
    }

    private static TokenUsage tokenUsageFrom(CompletionResponse response) {
        return new TokenUsage(response.getNumTokensPromptTotal(), response.getNumTokensGenerated());
    }

    private static FinishReason finishReasonFrom(CompletionResponse response) {
        switch (response.getCompletions().get(0).getFinishReason()) {
            case "maximum_tokens":
                return FinishReason.LENGTH;
            case "stop_sequence_reached":
            case "end_of_text":
                return FinishReason.STOP;
            default:
                return FinishReason.OTHER;
        }
    }

    public static AlephAlphaChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }
}
