package dev.langchain4j.model.watsonx;

import static java.util.Objects.requireNonNull;

import com.ibm.watsonx.ai.tokenization.TokenizationParameters;
import com.ibm.watsonx.ai.tokenization.TokenizationService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;

/**
 * A {@link TokenCountEstimator} implementation that integrates IBM watsonx.ai with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * TokenizationService tokenizationService = TokenizationService.builder()
 *     .url("https://...") // or use CloudRegion
 *     .authenticationProvider(authProvider)
 *     .projectId("my-project-id")
 *     .modelId("ibm/granite-3-8b-instruct")
 *     .build();
 *
 * TokenCountEstimator tokenCountEstimator = new WatsonxTokenCountEstimator(tokenizationService);
 * }</pre>
 *
 * @see TokenizationService
 */
public class WatsonxTokenCountEstimator implements TokenCountEstimator {

    private final TokenizationService tokenizationService;

    public WatsonxTokenCountEstimator(TokenizationService tokenizationService) {
        requireNonNull(tokenizationService, "tokenizationService is required");
        this.tokenizationService = tokenizationService;
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
}
