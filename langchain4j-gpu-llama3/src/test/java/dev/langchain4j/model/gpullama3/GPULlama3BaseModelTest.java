package dev.langchain4j.model.gpullama3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.format.ChatFormat;
import org.beehive.gpullama3.tokenizer.Tokenizer;
import org.junit.jupiter.api.Test;

class GPULlama3BaseModelTest {

    private static final int STOP_TOKEN = 9;

    /** Minimal concrete subclass of the abstract base class; it adds no behaviour of its own. */
    private static class TestModel extends GPULlama3BaseModel {}

    @Test
    void should_return_generated_text_when_generation_stops_at_max_tokens() throws Exception {
        // No stop token is configured, so generation ends because maxTokens is exhausted.
        TestModel model = newModel(Set.of(), new ArrayList<>(List.of(1, 2, 3)), "truncated answer");

        String response = model.modelResponse(chatRequest(), null);

        assertThat(response).isEqualTo("truncated answer");
    }

    @Test
    void should_return_generated_text_when_generation_stops_at_stop_token() throws Exception {
        TestModel model = newModel(Set.of(STOP_TOKEN), new ArrayList<>(List.of(1, 2, STOP_TOKEN)), "complete answer");

        String response = model.modelResponse(chatRequest(), null);

        assertThat(response).isEqualTo("complete answer");
    }

    private static ChatRequest chatRequest() {
        return ChatRequest.builder().messages(UserMessage.from("hi")).build();
    }

    private static TestModel newModel(Set<Integer> stopTokens, List<Integer> responseTokens, String decodedText)
            throws Exception {
        Tokenizer tokenizer = mock(Tokenizer.class);
        when(tokenizer.decode(anyList())).thenReturn(decodedText);

        Model llama = mock(Model.class);
        when(llama.tokenizer()).thenReturn(tokenizer);
        when(llama.generateTokens(any(), anyInt(), anyList(), anySet(), anyInt(), any(), anyBoolean(), any()))
                .thenReturn(responseTokens);

        ChatFormat chatFormat = mock(ChatFormat.class);
        when(chatFormat.getStopTokens()).thenReturn(stopTokens);

        TestModel model = new TestModel();
        model.chatFormat = chatFormat;
        // model, maxTokens and onGPU are private and are only ever assigned by init(), which loads a GGUF
        // file from disk and, on the GPU path, a TornadoVM plan. Setting them reflectively lets this test
        // exercise modelResponse() without a model file or a GPU.
        setField(model, "model", llama);
        setField(model, "maxTokens", 512);
        setField(model, "onGPU", Boolean.FALSE);
        return model;
    }

    private static void setField(GPULlama3BaseModel target, String name, Object value) throws Exception {
        Field field = GPULlama3BaseModel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
