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
class DeepSeekReasonerModelIT {

    /**
     * Refer to the model's documentation: <a href="https://api-docs.deepseek.com/guides/reasoning_model">...</a>
     * Note: Due to the official servers of DeepSeek, in order to ensure the smooth progress of the test cases,
     * the test cases adopted the third-party API of Silicon Mobility, which is consistent with the official output.
     * @param modelName the name of the reasoner model to test.
     */
    @ParameterizedTest
    @CsvSource(value = {"deepseek-ai/DeepSeek-R1-Distill-Qwen-32B"})
    void should_answer_with_reasoning_content(String modelName) {

        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("DEEPSEEK_BASE_URL")) // you can use "https://api.siliconflow.cn/v1" temporarily
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .modelName(modelName)
                // .temperature(0.0)   unsupported by the model, will be ignored
                .logRequests(true)
                .logResponses(true)
                .build();

        // given
        UserMessage userMessage = userMessage("what is the capital of China after 1949?");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        // then
        assertThat(response.content().text()).containsIgnoringCase("Beijing");
        assertThat(response.content().reasoningContent()).isNotBlank();
    }
}
