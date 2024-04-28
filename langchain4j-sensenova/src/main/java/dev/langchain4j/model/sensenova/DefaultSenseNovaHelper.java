package dev.langchain4j.model.sensenova;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.sensenova.chat.*;
import dev.langchain4j.model.sensenova.embedding.EmbeddingResponse;
import dev.langchain4j.model.sensenova.shared.Usage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.output.FinishReason.*;

class DefaultSenseNovaHelper {

	private static final String PLUGIN_WEB_SEARCH = "plugin_web_search";
	private static final String PLUGIN_ASSOCIATED_KNOWLEDGE = "plugin_associated_knowledge";

	public static List<Embedding> toEmbed(List<EmbeddingResponse> response) {
		return response.stream()
				.map(embedding -> Embedding.from(embedding.getEmbedding()))
				.collect(Collectors.toList());
	}

	public static Plugins toPlugins(List<ToolSpecification> toolSpecifications) {
		ToolSpecification webSearchTool = toolSpecifications.stream()
				.filter(DefaultSenseNovaHelper::isPluginWebSearch)
				.findFirst().orElse(null);
		ToolSpecification knowledgeTool = toolSpecifications.stream()
				.filter(DefaultSenseNovaHelper::isPluginAssociatedKnowledge)
				.findFirst().orElse(null);

		WebSearch webSearch = null;
		if (null != webSearchTool) {
			webSearch = WebSearch.builder()
					.searchEnable((boolean) webSearchTool.parameters()
							.properties().getOrDefault("searchEnable", new HashMap<>())
							.getOrDefault("value", false))
					.resultEnable((boolean) webSearchTool.parameters()
							.properties().getOrDefault("resultEnable", new HashMap<>())
							.getOrDefault("value", false))
					.build();
		}

		Knowledge knowledge = null;
		if (null != knowledgeTool) {
			knowledge = Knowledge.builder()
					.content((String) knowledgeTool.parameters()
							.properties().getOrDefault("content", new HashMap<>())
							.getOrDefault("value", null))
					.mode((KnowledgeInjectionMode) knowledgeTool.parameters()
							.properties().getOrDefault("model", new HashMap<>())
							.getOrDefault("value", KnowledgeInjectionMode.OVERRIDE))
					.build();
		}

		if (null == webSearch && null == knowledge) {
			return null;
		}

		Plugins plugins = new Plugins();
		plugins.setWebSearch(webSearch);
		plugins.setKnowledge(knowledge);

		return plugins;
	}

	public static List<Tool> toTools(List<ToolSpecification> toolSpecifications) {
		return toolSpecifications.stream()
				.filter(toolSpecification -> !isPlugin(toolSpecification))
				.map(toolSpecification -> Tool.from(toFunction(toolSpecification)))
				.collect(Collectors.toList());
	}

	private static Function toFunction(ToolSpecification toolSpecification) {
		return Function.builder()
				.name(toolSpecification.name())
				.description(toolSpecification.description())
				.parameters(toFunctionParameters(toolSpecification.parameters()))
				.build();
	}

	private static Parameters toFunctionParameters(ToolParameters toolParameters) {
		return Parameters.builder()
				.properties(toolParameters.properties())
				.required(toolParameters.required())
				.build();
	}


	public static List<Message> toSenseNovaMessages(List<ChatMessage> messages) {
		return messages.stream()
				.map(DefaultSenseNovaHelper::toSenseNovaMessage)
				.collect(Collectors.toList());
	}

	private static Message toSenseNovaMessage(ChatMessage message) {

		if (message instanceof UserMessage) {
			UserMessage userMessage = (UserMessage) message;
			return dev.langchain4j.model.sensenova.chat.UserMessage.builder()
					.content(userMessage.singleText())
					.build();
		}

		if (message instanceof AiMessage) {
			AiMessage aiMessage = (AiMessage) message;
			if (!aiMessage.hasToolExecutionRequests()) {
				return AssistantMessage.builder()
						.content(aiMessage.text())
						.build();
			}
			List<ToolCall> toolCallArrayList = new ArrayList<>();
			for (ToolExecutionRequest executionRequest : aiMessage.toolExecutionRequests()) {
				toolCallArrayList.add(ToolCall.builder()
						.function(new FunctionCall(executionRequest.name(), executionRequest.arguments()))
						.type(ToolType.FUNCTION)
						.id(executionRequest.id())
						.build()
				);
			}
			return AssistantMessage.builder()
					.content(aiMessage.text())
					.toolCalls(toolCallArrayList)
					.build();
		}

		if (message instanceof ToolExecutionResultMessage) {
			ToolExecutionResultMessage resultMessage = (ToolExecutionResultMessage) message;
			return ToolMessage.builder()
					.content(resultMessage.text())
					.toolCallId(resultMessage.id())
					.build();
		}

		throw illegalArgument("Unknown message type: " + message.type());
	}

	public static AiMessage aiMessageFrom(ChatCompletionResponse response) {
		ChatCompletionChoice chatCompletionChoice = response.getData().getChoices().get(0);
		if (isNullOrEmpty(chatCompletionChoice.getToolCalls())) {
			return AiMessage.from(chatCompletionChoice.getMessage());
		}

		return AiMessage.from(specificationsFrom(chatCompletionChoice.getToolCalls()));
	}

	public static List<ToolExecutionRequest> specificationsFrom(List<ToolCall> toolCalls) {
		List<ToolExecutionRequest> specifications = new ArrayList<>(toolCalls.size());
		for (ToolCall toolCall : toolCalls) {
			specifications.add(
					ToolExecutionRequest.builder()
							.id(toolCall.getId())
							.name(toolCall.getFunction().getName())
							.arguments(toolCall.getFunction().getArguments())
							.build()
			);
		}
		return specifications;
	}

	public static Usage getEmbeddingUsage(List<EmbeddingResponse> responses) {
		Usage tokenUsage = Usage.builder()
				.completionTokens(0)
				.promptTokens(0)
				.knowledgeTokens(0)
				.totalTokens(0)
				.build();

		for (EmbeddingResponse response : responses) {
			tokenUsage.add(response.getUsage());
		}
		return tokenUsage;
	}


	public static TokenUsage tokenUsageFrom(Usage usage) {
		if (usage == null) {
			return null;
		}
		return new TokenUsage(
				usage.getPromptTokens() + usage.getKnowledgeTokens(),
				usage.getCompletionTokens(),
				usage.getTotalTokens()
		);
	}

	public static FinishReason finishReasonFrom(String finishReason) {
		if (finishReason == null) {
			return null;
		}
		switch (finishReason) {
			case "stop":
				return STOP;
			case "length":
				return LENGTH;
			case "tool_calls":
				return TOOL_EXECUTION;
			default:
				return OTHER;
		}
	}

	public static boolean isPlugin(ToolSpecification toolSpecification) {
		return isPluginWebSearch(toolSpecification) || isPluginAssociatedKnowledge(toolSpecification);
	}

	public static boolean isPluginWebSearch(ToolSpecification toolSpecification) {
		return PLUGIN_WEB_SEARCH.contentEquals(toolSpecification.name());
	}

	public static boolean isPluginAssociatedKnowledge(ToolSpecification toolSpecification) {

		return PLUGIN_ASSOCIATED_KNOWLEDGE.contentEquals(toolSpecification.name());
	}
}
