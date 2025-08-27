package dev.langchain4j.model.watsonx;

import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.core.auth.iam.IAMAuthenticator;
import com.ibm.watsonx.ai.tokenization.TokenizationParameters;
import com.ibm.watsonx.ai.tokenization.TokenizationService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;
import java.time.Duration;

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
                .httpClient(builder.httpClient)
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
        throw new UnsupportedOperationException("Unimplemented method 'estimateTokenCountInMessage'");
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        throw new UnsupportedOperationException("Unimplemented method 'estimateTokenCountInMessages'");
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
