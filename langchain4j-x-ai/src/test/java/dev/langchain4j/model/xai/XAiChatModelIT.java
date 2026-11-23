package dev.langchain4j.model.xai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "XAI_API_KEY", matches = ".+")
class XAiChatModelIT {

    XAiChatModel model = XAiChatModel.builder()
            .apiKey(System.getenv("XAI_API_KEY"))
            .modelName(XAiChatModelName.GROK_4_7)
            .store(false)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_answer() {
        ChatResponse response = model.chat(UserMessage.from("What is the capital of France? Answer in one word."));

        assertThat(response.aiMessage().text()).containsIgnoringCase("Paris");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.tokenUsage().totalTokenCount()).isPositive();
    }

    @Test
    void should_call_tool_and_use_its_result() {
        ToolSpecification weather = ToolSpecification.builder()
                .name("getWeather")
                .description("Returns the current weather in the given city")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();

        UserMessage question = UserMessage.from("What is the weather in Paris?");
        ChatResponse first = model.chat(ChatRequest.builder()
                .messages(question)
                .toolSpecifications(weather)
                .build());

        AiMessage toolCall = first.aiMessage();
        assertThat(toolCall.toolExecutionRequests()).hasSize(1);
        assertThat(toolCall.toolExecutionRequests().get(0).name()).isEqualTo("getWeather");

        ChatResponse second = model.chat(ChatRequest.builder()
                .messages(
                        question,
                        toolCall,
                        ToolExecutionResultMessage.from(
                                toolCall.toolExecutionRequests().get(0), "Sunny, 25°C"))
                .toolSpecifications(weather)
                .build());

        assertThat(second.aiMessage().text()).containsIgnoringCase("sunny");
    }

    @Test
    void language_model_should_generate() {
        XAiLanguageModel languageModel = XAiLanguageModel.builder()
                .apiKey(System.getenv("XAI_API_KEY"))
                .modelName(XAiChatModelName.GROK_4_7)
                .store(false)
                .build();

        Response<String> response = languageModel.generate("What is the capital of Germany? Answer in one word.");

        assertThat(response.content()).containsIgnoringCase("Berlin");
    }
}
