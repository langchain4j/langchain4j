package dev.langchain4j.model.openai;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
public class DeepSeekReasonerModelIT {
    // Refer to the model's documentation: https://api-docs.deepseek.com/zh-cn/guides/reasoning_model

    @ParameterizedTest
    @CsvSource(value = {"deepseek-reasoner"})
    void should_answer_with_reasoning_content(String modelName) {

        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("DEEPSEEK_BASE_URL"))
                .apiKey(System.getenv("DEEPSEEK_BASE_URL"))
                .modelName(modelName)
                // .temperature(0.0)   unsupported by the model, will be ignored
                .logRequests(true)
                .logResponses(true)
                .build();

        // given
        UserMessage userMessage = userMessage("What is the capital of China?");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        // then
        assertThat(response.content().text()).containsIgnoringCase("Beijing");
        assertThat(response.content().reasoningContent()).isNotBlank();
    }
}
