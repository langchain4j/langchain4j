package dev.langchain4j.model.dashscope;

import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static dev.langchain4j.model.dashscope.QwenTestHelper.apiKey;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenStreamingLanguageModelIT {

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#languageModelNameProvider")
    public void should_send_messages_and_receive_response(String modelName) {
        StreamingLanguageModel model = QwenStreamingLanguageModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();
        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        model.generate("Please say 'hello' to me", handler);
        Response<String> response = handler.get();
        System.out.println(response);

        assertThat(response.content()).containsIgnoringCase("hello");
    }
}
