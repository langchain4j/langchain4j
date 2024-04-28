package dev.langchain4j.sensenova;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.sensenova.SenseNovaStreamingChatModel;
import dev.langchain4j.model.sensenova.chat.KnowledgeInjectionMode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SENSENOVA_API_KEY_ID", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SenseNovaStreamingChatModelIT {
	private String apiKeyId = System.getenv("SENSENOVA_API_KEY_ID");
	private String apiKeySecret = System.getenv("SENSENOVA_API_KEY_SECRET");

	private SenseNovaStreamingChatModel chatModel = SenseNovaStreamingChatModel.builder()
			.apiKeyId(apiKeyId)
			.apiKeySecret(apiKeySecret)
			.logRequests(true)
			.logResponses(true)
			.build();

	ToolSpecification calculator = ToolSpecification.builder()
			.name("calculator")
			.description("returns a sum of two numbers")
			.addParameter("first", INTEGER, JsonSchemaProperty.from("description", "first number"))
			.addParameter("second", INTEGER, JsonSchemaProperty.from("description", "second number"))
			.build();

	@Test
	@Order(1)
	void should_stream_answer() {

		sleep();

		TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();

		chatModel.generate("中国首都在哪里", handler);

		Response<AiMessage> response = handler.get();

		assertThat(response.content().text()).containsIgnoringCase("北京");
		TokenUsage tokenUsage = response.tokenUsage();
		assertThat(tokenUsage.totalTokenCount())
				.isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

		assertThat(response.finishReason()).isEqualTo(STOP);
	}

	@Test
	@Order(2)
	void should_generate_stream_answer_via_web_search_plugin() {

		sleep();

		// given
		UserMessage userMessage = userMessage("中国首都在哪里");

		ToolSpecification webSearch = ToolSpecification.builder()
				.name("plugin_web_search")
				.description("returns content from internet")
				.addParameter("searchEnable", JsonSchemaProperty.BOOLEAN, JsonSchemaProperty.from("value", true))
				.addParameter("resultEnable", JsonSchemaProperty.BOOLEAN, JsonSchemaProperty.from("value", true))
				.build();

		TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
		// when
		chatModel.generate(singletonList(userMessage), singletonList(webSearch), handler);
		Response<AiMessage> response = handler.get();

		// then
		assertThat(response.content().text()).contains("北京");

		assertThat(response.finishReason()).isEqualTo(STOP);
	}

	@Test
	@Order(3)
	void should_generate_stream_answer_via_associated_knowledge_plugin() {

		sleep();

		// given
		UserMessage userMessage = userMessage("今天下午几点吃饭");

		ToolSpecification associatedKnowledge = ToolSpecification.builder()
				.name("plugin_associated_knowledge")
				.description("returns content from associated knowledge")
				.addParameter("content", JsonSchemaProperty.STRING, JsonSchemaProperty.from("value", "大家决定今天下午四点钟去吃饭"))
				.addParameter("mode", JsonSchemaProperty.STRING, JsonSchemaProperty.from("value", KnowledgeInjectionMode.OVERRIDE))
				.build();
		TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
		// when
		chatModel.generate(singletonList(userMessage), singletonList(associatedKnowledge), handler);
		Response<AiMessage> response = handler.get();

		// then
		assertThat(response.content().text()).contains("四点");

		assertThat(response.finishReason()).isEqualTo(STOP);
	}

	@Test
	@Order(4)
	void should_execute_a_tool_then_stream_answer() {

		sleep();

		// given
		UserMessage userMessage = userMessage("2+2=?");
		List<ToolSpecification> toolSpecifications = singletonList(calculator);

		// when
		TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();

		chatModel.generate(singletonList(userMessage), toolSpecifications, handler);

		Response<AiMessage> response = handler.get();
		AiMessage aiMessage = response.content();

		// then
		assertThat(aiMessage.text()).isNullOrEmpty();

		List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
		assertThat(toolExecutionRequests).hasSize(1);

		ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(0);
		assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
		assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

		TokenUsage tokenUsage = response.tokenUsage();
		assertThat(tokenUsage.totalTokenCount())
				.isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

		assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

		// given
		ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");

		List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

		// when
		TestStreamingResponseHandler<AiMessage> secondHandler = new TestStreamingResponseHandler<>();

		chatModel.generate(messages, secondHandler);

		Response<AiMessage> secondResponse = secondHandler.get();
		AiMessage secondAiMessage = secondResponse.content();

		// then
		assertThat(secondAiMessage.text()).contains("4");
		assertThat(secondAiMessage.toolExecutionRequests()).isNull();

		TokenUsage secondTokenUsage = secondResponse.tokenUsage();
		assertThat(secondTokenUsage.totalTokenCount())
				.isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

		assertThat(secondResponse.finishReason()).isEqualTo(STOP);
	}

	private void sleep() {
		try {
			Thread.sleep(1000L);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
