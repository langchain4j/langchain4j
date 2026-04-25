package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.aiMessageFrom;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.thinkingFrom;
import static org.assertj.core.api.Assertions.assertThat;

import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionMessage;
import org.junit.jupiter.api.Test;

class InternalOpenAiOfficialHelperTest {

    @Test
    void should_include_thinking_content_when_returnThinking_is_true() {
        ChatCompletion chatCompletion = chatCompletionWithReasoning("Let me think first");

        assertThat(aiMessageFrom(chatCompletion, true).thinking()).isEqualTo("Let me think first");
        assertThat(aiMessageFrom(chatCompletion, true).text()).isEqualTo("Answer");
    }

    @Test
    void should_exclude_thinking_content_when_returnThinking_is_false() {
        ChatCompletion chatCompletion = chatCompletionWithReasoning("Let me think first");

        assertThat(aiMessageFrom(chatCompletion, false).thinking()).isNull();
    }

    @Test
    void should_extract_thinking_from_streaming_delta() {
        ChatCompletionChunk.Choice.Delta delta = ChatCompletionChunk.Choice.Delta.builder()
                .content("Answer")
                .putAdditionalProperty("reasoning_content", JsonValue.from("Let me think first"))
                .build();

        assertThat(thinkingFrom(delta)).isEqualTo("Let me think first");
    }

    @Test
    void should_return_null_when_streaming_delta_has_no_thinking() {
        ChatCompletionChunk.Choice.Delta delta =
                ChatCompletionChunk.Choice.Delta.builder().content("Answer").build();

        assertThat(thinkingFrom(delta)).isNull();
    }

    private static ChatCompletion chatCompletionWithReasoning(String reasoningContent) {
        return ChatCompletion.builder()
                .id("chatcmpl-1")
                .created(1L)
                .model("gpt-4.1")
                .object_(JsonValue.from("chat.completion"))
                .addChoice(ChatCompletion.Choice.builder()
                        .index(0)
                        .finishReason(ChatCompletion.Choice.FinishReason.STOP)
                        .logprobs((ChatCompletion.Choice.Logprobs) null)
                        .message(ChatCompletionMessage.builder()
                                .role(JsonValue.from("assistant"))
                                .content("Answer")
                                .refusal((String) null)
                                .putAdditionalProperty("reasoning_content", JsonValue.from(reasoningContent))
                                .build())
                        .build())
                .build();
    }
}
