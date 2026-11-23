package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromMessageToGContent;
import static java.util.Collections.singletonList;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.media.DefaultMediaFetcher;
import dev.langchain4j.data.media.MediaFetcher;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.internal.MediaPrefetcher;
import dev.langchain4j.model.TokenCountEstimator;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class GoogleAiGeminiTokenCountEstimator implements TokenCountEstimator {

    private final GeminiService geminiService;
    private final String modelName;
    private final Integer maxRetries;
    private final MediaFetcher mediaFetcher;

    public GoogleAiGeminiTokenCountEstimator(Builder builder) {
        this.geminiService = new GeminiService(
                builder.httpClientBuilder,
                builder.apiKey,
                builder.baseUrl,
                getOrDefault(builder.logRequestsAndResponses, false),
                getOrDefault(builder.logRequests, false),
                getOrDefault(builder.logResponses, false),
                builder.logger,
                builder.timeout,
                null);
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.mediaFetcher = getOrDefault(builder.mediaFetcher, MediaPrefetcher::defaultMediaFetcher);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int estimateTokenCountInText(String text) {
        return estimateTokenCountInMessages(singletonList(UserMessage.from(text)));
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        return estimateTokenCountInMessages(singletonList(message));
    }

    public int estimateTokenCountInToolExecutionRequests(Iterable<ToolExecutionRequest> toolExecutionRequests) {
        List<ToolExecutionRequest> allToolRequests = new LinkedList<>();
        toolExecutionRequests.forEach(allToolRequests::add);

        return estimateTokenCountInMessage(AiMessage.from(allToolRequests));
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        List<ChatMessage> allMessages = new LinkedList<>();
        messages.forEach(allMessages::add);

        List<GeminiContent> geminiContentList = fromMessageToGContent(allMessages, null, false, mediaFetcher);
        GeminiCountTokensRequest countTokensRequest = new GeminiCountTokensRequest(geminiContentList, null);

        return estimateTokenCount(countTokensRequest);
    }

    public int estimateTokenCountInToolSpecifications(Iterable<ToolSpecification> toolSpecifications) {
        List<ToolSpecification> allTools = new LinkedList<>();
        toolSpecifications.forEach(allTools::add);

        GeminiContent dummyContent = new GeminiContent(
                // This string contains 2 tokens
                singletonList(
                        GeminiContent.GeminiPart.builder().text("Dummy content").build()),
                null);

        GeminiCountTokensRequest countTokensRequestWithDummyContent = new GeminiCountTokensRequest(
                null,
                GeminiGenerateContentRequest.builder()
                        .model("models/" + this.modelName)
                        .contents(singletonList(dummyContent))
                        .tools(FunctionMapper.fromToolSpecsToGTools(allTools, false, false, false, false, false))
                        .build());

        // The API doesn't allow us to make a request to count the tokens of the tool specifications only.
        // Instead, we take the approach of adding a dummy content in the request, and subtract the tokens for the dummy
        // request.
        // The string "Dummy content" accounts for 2 tokens. So let's subtract 2 from the overall count.
        return estimateTokenCount(countTokensRequestWithDummyContent) - 2;
    }

    private int estimateTokenCount(GeminiCountTokensRequest countTokensRequest) {
        GeminiCountTokensResponse countTokensResponse = withRetryMappingExceptions(
                () -> this.geminiService.countTokens(this.modelName, countTokensRequest), this.maxRetries);
        return countTokensResponse.totalTokens();
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String modelName;
        private String apiKey;

        private String baseUrl;
        private Boolean logRequestsAndResponses;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private Duration timeout;
        private Integer maxRetries;
        private @Nullable MediaFetcher mediaFetcher;

        Builder() {}

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the {@link MediaFetcher} that downloads images given by an {@code http} or {@code https} URL, for
         * example with {@link ImageContent#from(URI)}. Gemini receives such images as content, so they are
         * downloaded before their tokens are counted. Other media URLs are passed on to Gemini as they are.
         * <p>
         * When not set, a {@link DefaultMediaFetcher} with the default settings is used: it downloads only from
         * public hosts, with a timeout and a size limit, and never reads local files. To download images from your
         * own servers, or to change the limits, configure a {@link DefaultMediaFetcher} and set it here.
         *
         * @param mediaFetcher the fetcher that downloads images, or {@code null} to use the default one.
         * @return {@code this}.
         */
        @Experimental
        public Builder mediaFetcher(@Nullable MediaFetcher mediaFetcher) {
            this.mediaFetcher = mediaFetcher;
            return this;
        }

        public GoogleAiGeminiTokenCountEstimator build() {
            return new GoogleAiGeminiTokenCountEstimator(this);
        }
    }
}
