package dev.langchain4j.model.sensenova;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.sensenova.embedding.EmbeddingRequest;
import dev.langchain4j.model.sensenova.embedding.EmbeddingResponse;
import dev.langchain4j.model.sensenova.shared.Usage;
import dev.langchain4j.model.sensenova.spi.SenseNovaEmbeddingModelBuilderFactory;
import lombok.Builder;

import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.sensenova.DefaultSenseNovaHelper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents a SenseNova embedding model, such as nova-embedding-stable.
 */
public class SenseNovaEmbeddingModel implements EmbeddingModel {

	private final String baseUrl;
	private final Integer maxRetries;
	private final String model;
	private final SenseNovaClient client;

	@Builder
	public SenseNovaEmbeddingModel(
			String baseUrl,
			String apiKeyId,
			String apiKeySecret,
			String model,
			Integer maxRetries,
			Boolean logRequests,
			Boolean logResponses
	) {
		this.baseUrl = getOrDefault(baseUrl, "https://api.sensenova.cn/");
		this.model = getOrDefault(model, dev.langchain4j.model.sensenova.embedding.EmbeddingModel.NOVA_EMBEDDING_STABLE.toString());
		this.maxRetries = getOrDefault(maxRetries, 3);
		this.client = SenseNovaClient.builder()
				.baseUrl(this.baseUrl)
				.apiKeyId(apiKeyId)
				.apiKeySecret(apiKeySecret)
				.logRequests(getOrDefault(logRequests, false))
				.logResponses(getOrDefault(logResponses, false))
				.build();
	}

	public static SenseNovaEmbeddingModelBuilder builder() {
		for (SenseNovaEmbeddingModelBuilderFactory factories : loadFactories(SenseNovaEmbeddingModelBuilderFactory.class)) {
			return factories.get();
		}
		return new SenseNovaEmbeddingModelBuilder();
	}

	/**
	 * SenseNova supports batch text in one request.
	 *
	 * @param textSegments the text segments to embed.
	 * @return singletonList of the embed response.
	 */
	@Override
	public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

		EmbeddingRequest request = EmbeddingRequest.builder()
				.input(textSegments.stream().map(TextSegment::text).toArray(String[]::new))
				.model(this.model)
				.build();

		final EmbeddingResponse embeddingResponse = withRetry(() -> client.embedAll(request), maxRetries);

		List<EmbeddingResponse> embeddingRequests = Collections.singletonList(embeddingResponse);

		Usage usage = getEmbeddingUsage(embeddingRequests);

		return Response.from(
				toEmbed(embeddingRequests),
				tokenUsageFrom(usage)
		);
	}

	public static class SenseNovaEmbeddingModelBuilder {
		public SenseNovaEmbeddingModelBuilder() {
		}
	}
}
