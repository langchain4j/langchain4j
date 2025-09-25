import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.gpullama3.GPULlama3ChatModel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class GPULlama3ChatModelIT extends AbstractChatModelIT {

    static GPULlama3ChatModel model;

    @BeforeAll
    static void setup() {
        // @formatter:off
        Path modelPath = Paths.get("beehive-llama-3.2-1b-instruct-fp16.gguf");
        model = GPULlama3ChatModel.builder()
                .modelPath(modelPath)
                .onGPU(Boolean.TRUE) // if false, runs on CPU though a lightweight implementation of llama3.java
                .build();
    }

    @Tag("gpu")
    @Test
    void should_get_non_empty_response() {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a joke"))
                .build();

        ChatResponse response = model.chat(request);

        AiMessage aiMessage = response.aiMessage();
        System.out.println(aiMessage.text());
        assertThat(aiMessage.text()).isNotBlank();
    }

    @Override
    protected List<ChatModel> models() {
        return List.of(model);
    }
}
