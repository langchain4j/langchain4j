package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.core.auth.iam.IAMAuthenticator;
import com.ibm.watsonx.ai.tokenization.TokenizationParameters;
import com.ibm.watsonx.ai.tokenization.TokenizationResponse;
import com.ibm.watsonx.ai.tokenization.TokenizationResponse.Result;
import com.ibm.watsonx.ai.tokenization.TokenizationService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link TokenCountEstimator} implementation that integrates IBM watsonx.ai with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * TokenCountEstimator tokenCountEstimator = WatsonxTokenCountEstimator.builder()
 *     .url("https://...") // or use CloudRegion
 *     .apiKey("...")
 *     .projectId("...")
 *     .build();
 * }</pre>
 *
 */
public class WatsonxTokenCountEstimator implements TokenCountEstimator {

    private final TokenizationService tokenizationService;

    private WatsonxTokenCountEstimator(Builder builder) {
        var tokenizationServiceBuilder = TokenizationService.builder();
        if (nonNull(builder.authenticationProvider)) {
            tokenizationServiceBuilder.authenticationProvider(builder.authenticationProvider);
        } else {
            tokenizationServiceBuilder.authenticationProvider(
                    IAMAuthenticator.builder().apiKey(builder.apiKey).build());
        }

        tokenizationService = tokenizationServiceBuilder
                .url(builder.url)
                .modelId(builder.modelName)
                .version(builder.version)
                .projectId(builder.projectId)
                .spaceId(builder.spaceId)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .build();
    }

    @Override
    public int estimateTokenCountInText(String text) {
        return estimateTokenCountInText(text, null);
    }

    /**
     * Estimates the count of tokens in the given text using the specified {@link TokenizationParameters}.
     *
     * @param text the text.
     * @param parameters the tokenization parameters to use.
     * @return the estimated count of tokens.
     */
    public int estimateTokenCountInText(String text, TokenizationParameters parameters) {
        return WatsonxExceptionMapper.INSTANCE.withExceptionMapper(
                () -> tokenizationService.tokenize(text, parameters).result().tokenCount());
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        return WatsonxExceptionMapper.INSTANCE.withExceptionMapper(() -> switch (message.type()) {
            case SYSTEM -> {
                var systemMessage = (SystemMessage) message;
                yield estimateTokenCountInText(systemMessage.text());
            }
            case AI -> {
                var aiMessage = (AiMessage) message;

                List<CompletableFuture<TokenizationResponse>> futures = new ArrayList<>();

                if (isNotNullOrEmpty(aiMessage.thinking()))
                    futures.add(tokenizationService.asyncTokenize(aiMessage.thinking()));

                if (isNotNullOrEmpty(aiMessage.text()))
                    futures.add(tokenizationService.asyncTokenize(aiMessage.text()));

                if (aiMessage.hasToolExecutionRequests()) {
                    for (var toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                        futures.add(tokenizationService.asyncTokenize(toolExecutionRequest.id()));
                        futures.add(tokenizationService.asyncTokenize(toolExecutionRequest.name()));
                        if (!isNullOrBlank(toolExecutionRequest.arguments()))
                            futures.add(tokenizationService.asyncTokenize(toolExecutionRequest.arguments()));
                    }
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .join();
                yield futures.stream()
                        .map(CompletableFuture::join)
                        .map(TokenizationResponse::result)
                        .mapToInt(Result::tokenCount)
                        .sum();
            }
            case USER -> {
                var userMessage = (UserMessage) message;

                List<CompletableFuture<TokenizationResponse>> futures = new ArrayList<>();

                if (isNotNullOrBlank(userMessage.name()))
                    futures.add(tokenizationService.asyncTokenize(userMessage.name()));

                for (Content content : userMessage.contents()) {
                    switch (content.type()) {
                        case TEXT -> futures.add(tokenizationService.asyncTokenize(((TextContent) content).text()));
                        case AUDIO, IMAGE, PDF, VIDEO ->
                            throw new UnsupportedOperationException(
                                    "The " + content.type().name()
                                            + " content type is not supported in WatsonxTokenCountEstimator");
                    }
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .join();
                yield futures.stream()
                        .map(CompletableFuture::join)
                        .map(TokenizationResponse::result)
                        .mapToInt(Result::tokenCount)
                        .sum();
            }
            case TOOL_EXECUTION_RESULT -> {
                var toolExecutionResult = (ToolExecutionResultMessage) message;
                yield estimateTokenCountInText(toolExecutionResult.text());
            }
            case CUSTOM ->
                throw new UnsupportedOperationException(
                        "The custom message type is not supported in WatsonxTokenCountEstimator");
        });
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        int tokenCount = 0;
        for (ChatMessage chatMessage : messages) tokenCount += estimateTokenCountInMessage(chatMessage);
        return tokenCount;
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * TokenCountEstimator tokenCountEstimator = WatsonxTokenCountEstimator.builder()
     *     .url("https://...") // or use CloudRegion
     *     .apiKey("...")
     *     .projectId("...")
     *     .build();
     * }</pre>
     *
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing {@link WatsonxTokenCountEstimator} instances with configurable parameters.
     */
    public static class Builder extends WatsonxBuilder<Builder> {
        private String modelName;
        private String projectId;
        private String spaceId;
        private Duration timeout;

        private Builder() {}

        public Builder url(CloudRegion cloudRegion) {
            return super.url(cloudRegion.getMlEndpoint());
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder spaceId(String spaceId) {
            this.spaceId = spaceId;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public WatsonxTokenCountEstimator build() {
            return new WatsonxTokenCountEstimator(this);
        }
    }
}
