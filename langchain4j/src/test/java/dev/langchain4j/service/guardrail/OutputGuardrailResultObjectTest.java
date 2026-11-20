package dev.langchain4j.service.guardrail;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * An output guardrail can attach a result object ({@link OutputGuardrailResult#successWithResult(Object)}), which is
 * returned to the caller instead of the parsed response, also when the AI Service method returns a {@link Result}.
 */
class OutputGuardrailResultObjectTest {

    record Person(String name) {}

    static final Person KLAUS = new Person("Klaus");

    static class ReturnsPerson implements OutputGuardrail {

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return successWithResult(KLAUS);
        }

        @Override
        public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    static class ReturnsNames implements OutputGuardrail {

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return successWithResult(List.of("Klaus"));
        }
    }

    interface PersonExtractor {

        Person extract(String text);
    }

    interface ResultPersonExtractor {

        Result<Person> extract(String text);
    }

    interface AsyncResultPersonExtractor {

        CompletableFuture<Result<Person>> extract(String text);
    }

    interface NamesExtractor {

        List<String> extract(String text);
    }

    static ChatModel chatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("Klaus"))
                        .tokenUsage(new TokenUsage(10, 5))
                        .finishReason(FinishReason.STOP)
                        .build();
            }

            @Override
            public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
                return CompletableFuture.completedFuture(doChat(chatRequest));
            }
        };
    }

    @Test
    void should_return_the_result_object_as_the_content_of_a_result() {

        // given
        ResultPersonExtractor extractor = AiServices.builder(ResultPersonExtractor.class)
                .chatModel(chatModel())
                .outputGuardrails(new ReturnsPerson())
                .build();

        // when
        Result<Person> result = extractor.extract("Klaus was here");

        // then
        assertThat(result.content()).isSameAs(KLAUS);
        assertThat(result.tokenUsage()).isEqualTo(new TokenUsage(10, 5));
        assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_return_the_result_object_as_the_content_of_a_result_in_async_ai_service() throws Exception {

        // given
        AsyncResultPersonExtractor extractor = AiServices.builder(AsyncResultPersonExtractor.class)
                .chatModel(chatModel())
                .outputGuardrails(new ReturnsPerson())
                .build();

        // when
        Result<Person> result = extractor.extract("Klaus was here").get(10, SECONDS);

        // then
        assertThat(result.content()).isSameAs(KLAUS);
        assertThat(result.tokenUsage()).isEqualTo(new TokenUsage(10, 5));
    }

    @Test
    void should_return_a_result_object_whose_class_is_a_subtype_of_the_return_type() {

        // given
        NamesExtractor extractor = AiServices.builder(NamesExtractor.class)
                .chatModel(chatModel())
                .outputGuardrails(new ReturnsNames())
                .build();

        // when
        List<String> names = extractor.extract("Klaus was here");

        // then
        assertThat(names).containsExactly("Klaus");
    }

    @Test
    void should_return_the_result_object() {

        // given
        PersonExtractor extractor = AiServices.builder(PersonExtractor.class)
                .chatModel(chatModel())
                .outputGuardrails(new ReturnsPerson())
                .build();

        // when
        Person person = extractor.extract("Klaus was here");

        // then
        assertThat(person).isSameAs(KLAUS);
    }
}
