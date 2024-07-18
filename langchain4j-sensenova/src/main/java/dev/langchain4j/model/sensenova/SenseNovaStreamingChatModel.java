package dev.langchain4j.model.sensenova;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.sensenova.chat.ChatCompletionRequest;
import dev.langchain4j.model.sensenova.spi.SenseNovaStreamingChatModelBuilderFactory;
import lombok.Builder;

import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.sensenova.chat.ChatCompletionModel.SENSE_CHAT_5;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class SenseNovaStreamingChatModel implements StreamingChatLanguageModel {

	private String baseUrl;
	private Double temperature;
	private Double topP;
	private String model;
	private Double repetitionPenalty;
	private Integer maxToken;
	private Integer maxNewTokens;
	private SenseNovaClient client;


	@Builder
	public SenseNovaStreamingChatModel(
			String baseUrl,
			String apiKeyId,
			String apiKeySecret,
			Double temperature,
			Double topP,
			String model,
			Double repetitionPenalty,
			Integer maxToken,
			Integer maxNewTokens,
			Boolean logRequests,
			Boolean logResponses
	) {
		this.baseUrl = getOrDefault(baseUrl, "https://api.sensenova.cn/");
		this.temperature = getOrDefault(temperature, 0.8);
		this.topP = getOrDefault(topP, 0.7);
		this.model = getOrDefault(model, SENSE_CHAT_5.toString());
		this.repetitionPenalty = getOrDefault(repetitionPenalty, 1.05);
		this.maxToken = getOrDefault(maxToken, 2048);
		this.maxNewTokens = getOrDefault(maxNewTokens, 1024);
		this.client = SenseNovaClient.builder()
				.baseUrl(this.baseUrl)
				.apiKeyId(apiKeyId)
				.apiKeySecret(apiKeySecret)
				.logRequests(getOrDefault(logRequests, false))
				.logResponses(getOrDefault(logResponses, false))
				.build();
	}

	public static SenseNovaStreamingChatModelBuilder builder() {
		for (SenseNovaStreamingChatModelBuilderFactory factories : loadFactories(SenseNovaStreamingChatModelBuilderFactory.class)) {
			return factories.get();
		}
		return new SenseNovaStreamingChatModelBuilder();
	}

	@Override
	public void generate(String userMessage, StreamingResponseHandler<AiMessage> handler) {
		this.generate(Collections.singletonList(UserMessage.from(userMessage)), handler);
	}

	@Override
	public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
		this.generate(messages, (ToolSpecification) null, handler);
	}

	@Override
	public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
		ensureNotEmpty(messages, "messages");

		ChatCompletionRequest.ChatCompletionRequestBuilder builder = ChatCompletionRequest.builder()
				.model(this.model)
				.maxNewTokens(maxNewTokens)
				.stream(true)
				.topP(topP)
				.temperature(temperature)
				.repetitionPenalty(repetitionPenalty)
				.messages(DefaultSenseNovaHelper.toSenseNovaMessages(messages));

		if (!isNullOrEmpty(toolSpecifications)) {
			builder.plugins(DefaultSenseNovaHelper.toPlugins(toolSpecifications));
			builder.tools(DefaultSenseNovaHelper.toTools(toolSpecifications));
		}

		client.streamingChatCompletion(builder.build(), handler);
	}

	@Override
	public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
		this.generate(messages, toolSpecification == null ? null : Collections.singletonList(toolSpecification), handler);
	}

	public static class SenseNovaStreamingChatModelBuilder {
		public SenseNovaStreamingChatModelBuilder() {

		}
	}
}
