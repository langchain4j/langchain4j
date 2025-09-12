import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.gpullama3.GPULlama3ChatModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Requires TornadoVM locally configured with GPU support")
public class GPULlama3ChatModelIT {

    static GPULlama3ChatModel model;
    @BeforeAll
    static void setup() {
        // @formatter:off
        Path modelPath = Paths.get("Phi-3-mini-4k-instruct-fp16.gguf");
        model = GPULlama3ChatModel.builder()
                .modelPath(modelPath)
                .onGPU(Boolean.TRUE) //if false, runs on CPU though a lightweight implementation of llama3.java
                .build();
    }

    @Test
    void should_get_non_empty_response() {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a joke"))
                .build();

        ChatResponse response = model.chat(request);

        AiMessage aiMessage = response.aiMessage();

        assertThat(aiMessage.text()).isNotBlank();
    }

}
