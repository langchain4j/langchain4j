package dev.langchain4j.model.sensenova;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

import dev.langchain4j.model.sensenova.chat.ChatCompletionRequest;
import dev.langchain4j.model.sensenova.chat.ChatCompletionResponse;

import dev.langchain4j.model.sensenova.spi.SenseNovaChatModelBuilderFactory;
import lombok.Builder;

import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.sensenova.DefaultSenseNovaHelper.*;
import static dev.langchain4j.model.sensenova.chat.ChatCompletionModel.SENSE_CHAT_5;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents a Sense Nova language model with a chat completion interface, such as SenseChat-5.
 * You can find description of parameters <a href="https://open.bigmodel.cn/dev/api">here</a>.
 */

public class SenseNovaChatModel implements ChatLanguageModel {

	private String baseUrl;
	private Double temperature;
	private Double topP;
	private String model;
	private Double repetitionPenalty;
	private Integer maxRetries;
	private Integer maxToken;
	private Integer maxNewTokens;
	private SenseNovaClient client;

	@Builder
	public SenseNovaChatModel(
			String baseUrl,
			String apiKeyId,
			String apiKeySecret,
			Double temperature,
			Double topP,
			String model,
			Double repetitionPenalty,
			Integer maxRetries,
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
		this.maxRetries = getOrDefault(maxRetries, 3);
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

	public static SenseNovaChatModelBuilder builder() {
		for (SenseNovaChatModelBuilderFactory factories : loadFactories(SenseNovaChatModelBuilderFactory.class)) {
			return factories.get();
		}
		return new SenseNovaChatModelBuilder();
	}

	@Override
	public Response<AiMessage> generate(List<ChatMessage> messages) {
		return generate(messages, (ToolSpecification) null);
	}

	@Override
	public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
		ensureNotEmpty(messages, "messages");

		ChatCompletionRequest.ChatCompletionRequestBuilder builder = ChatCompletionRequest.builder()
				.model(this.model)
				.maxNewTokens(maxNewTokens)
				.stream(false)
				.topP(topP)
				.temperature(temperature)
				.repetitionPenalty(repetitionPenalty)
				.messages(DefaultSenseNovaHelper.toSenseNovaMessages(messages));

		if (!isNullOrEmpty(toolSpecifications)) {
			builder.plugins(DefaultSenseNovaHelper.toPlugins(toolSpecifications));
			builder.tools(DefaultSenseNovaHelper.toTools(toolSpecifications));
		}

		ChatCompletionResponse response = withRetry(() -> client.chatCompletion(builder.build()), maxRetries);
		return Response.from(
				aiMessageFrom(response),
				tokenUsageFrom(response.getData().getUsage()),
				finishReasonFrom(response.getData().getChoices().get(0).getFinishReason())
		);
	}

	@Override
	public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
		return generate(messages, toolSpecification != null ? Collections.singletonList(toolSpecification) : null);
	}

}
