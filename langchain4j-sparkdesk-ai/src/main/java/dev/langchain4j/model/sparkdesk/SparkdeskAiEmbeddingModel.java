package dev.langchain4j.model.sparkdesk;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.sparkdesk.client.embedding.*;
import dev.langchain4j.model.sparkdesk.client.message.UserMessage;
import dev.langchain4j.model.sparkdesk.spi.SparkdeskAiEmbeddingModelBuilderFactory;
import lombok.Builder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.sparkdesk.DefaultSparkdeskAiHelper.toEmbed;
import static dev.langchain4j.model.sparkdesk.Json.OBJECT_MAPPER;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents an Sparkdesk embedding model,detail https://www.xfyun.cn/doc/spark/Embedding_api.html
 */
public class SparkdeskAiEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final String appId;
    private final Domain domain;
    private final Encoding encoding;
    private final Compress compress;
    private final Format format;
    private final Integer maxRetries;
    private final SparkdeskAiHttpClient client;

    @Builder
    public SparkdeskAiEmbeddingModel(
            String baseUrl,
            String appId,
            String apiKey,
            String apiSecret,
            Domain domain,
            Encoding encoding,
            Compress compress,
            Format format,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.domain = getOrDefault(domain, Domain.QUERY);
        this.encoding = getOrDefault(encoding, Encoding.UTF8);
        this.compress = getOrDefault(compress, Compress.RAW);
        this.format = getOrDefault(format, Format.PLAIN);
        this.appId = SparkUtils.isNullOrEmpty(appId, "The appId field cannot be null or an empty string");
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.client = SparkdeskAiHttpClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://emb-cn-huabei-1.xf-yun.com/"))
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    public static SparkdeskAiEmbeddingModelBuilder builder() {
        for (SparkdeskAiEmbeddingModelBuilderFactory factories : loadFactories(SparkdeskAiEmbeddingModelBuilderFactory.class)) {
            return factories.get();
        }
        return new SparkdeskAiEmbeddingModelBuilder();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<EmbeddingResponse> reponses = textSegments.stream()
                .map(item -> {
                    List<UserMessage> userMessageList = Collections.singletonList(UserMessage.from(item.text()));
                    ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
                    objectNode.set("messages", OBJECT_MAPPER.valueToTree(userMessageList));
                    return EmbeddingRequest.builder()
                            .header(EmbeddingRequestHeader.builder().appId(this.appId).build())
                            .parameter(EmbeddingParameter.builder().emb(Emb.builder()
                                            .domain(this.domain).feature(RequestFeature.builder()
                                                    .compress(this.compress)
                                                    .format(this.format)
                                                    .encoding(this.encoding)
                                                    .build())
                                            .build())
                                    .build())
                            .payload(EmbeddingRequestPayload.builder()
                                    .messages(EmbeddingMessages.builder()
                                            .compress(this.compress)
                                            .format(this.format)
                                            .encoding(this.encoding)
                                            .status(3)
                                            .text(Base64.getEncoder().encodeToString(Json.toJson(objectNode).getBytes(StandardCharsets.UTF_8)))
                                            .build())
                                    .build())
                            .build();
                }).map(request -> withRetry(() -> client.embedAll(request), maxRetries))
                .collect(Collectors.toList());
        return Response.from(
                toEmbed(reponses)
        );
    }

    public static class SparkdeskAiEmbeddingModelBuilder {
        public SparkdeskAiEmbeddingModelBuilder() {
        }
    }
}
