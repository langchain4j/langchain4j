package dev.langchain4j.model.deliverance;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.teknek.deliverance.math.WrappedForkJoinPool;
import io.teknek.deliverance.model.AutoModelForCausaLm;
import io.teknek.deliverance.tensor.operations.ConfigurableTensorProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveranceChatModelIT {

    static ChatModel model;

    @BeforeAll
    static void setup() {
        // New directory each run
        // File tmpDir = DeliveranceTestUtils.tempDir();
        AutoModelForCausaLm.Builder builder = DeliveranceModels.builder((Path) null,
                DeliveranceTestUtils.GEMMA_MODEL_NAME);
        builder.withTensorProvider(new ConfigurableTensorProvider(builder.getAllocator(),
                new WrappedForkJoinPool(WrappedForkJoinPool.autoSizeByCores())));

        model = DeliveranceChatModel.builder()
                .modelBuilder(builder)
                .defaultRequestParameters(parameters -> parameters
                        .temperature(0.0)
                        .topP(0.9)
                        .maxOutputTokens(64))
                .build();
    }

    @Test
    void should_send_messages_and_return_response() {
        List<ChatMessage> messages = singletonList(UserMessage.from("When is the best time of year to visit Japan?"));
        ChatResponse response = model.chat(messages);
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @Test
    void should_accept_per_request_top_p_and_stop_sequences() {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Answer in one short sentence about spring in Japan."))
                .parameters(DeliveranceChatRequestParameters.builder()
                        .topP(0.75)
                        .stopSequences("###")
                        .maxOutputTokens(24)
                        .build())
                .build();

        ChatResponse response = model.chat(request);

        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        System.out.println(response.aiMessage());
    }
}
