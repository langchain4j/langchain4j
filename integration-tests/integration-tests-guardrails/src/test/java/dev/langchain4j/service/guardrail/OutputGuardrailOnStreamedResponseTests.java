package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OutputGuardrailOnStreamedResponseTests extends BaseGuardrailTests {
    MyAiService aiService = MyAiService.create();

    @Test
    void outputGuardrailsAreInvoked() throws InterruptedException {
        var okGuardrail = SingletonClassInstanceFactory.getInstance(OKGuardrail.class);
        assertThat(okGuardrail.spy()).isEqualTo(0);
        execute(() -> aiService.ok("1"));
        assertThat(okGuardrail.spy()).isEqualTo(1);
        execute(() -> aiService.ok("2"));
        assertThat(okGuardrail.spy()).isEqualTo(2);
    }

    @Test
    void guardrailCanThrowValidationException() {
        var koGuardrail = SingletonClassInstanceFactory.getInstance(KOGuardrail.class);
        assertThat(koGuardrail.spy()).isEqualTo(0);
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> execute(() -> aiService.ko("1")))
                .withCauseExactlyInstanceOf(ValidationException.class);
        assertThat(koGuardrail.spy()).isEqualTo(1);
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> execute(() -> aiService.ko("1")))
                .withCauseExactlyInstanceOf(ValidationException.class);
        assertThat(koGuardrail.spy()).isEqualTo(2);
    }

    @Test
    void streamingPartialResponsesBuffered() {
        var okPartials = new ArrayList<String>();
        var okComplete = new AtomicReference<ChatResponse>();

        this.aiService
                .ok("1")
                .onPartialResponse(okPartials::add)
                .onError(t -> fail(t.getMessage()))
                .onCompleteResponse(okComplete::set)
                .start();

        assertThat(okPartials).hasSize(3).containsExactly("Hi!", " ", "World!");
        assertThat(okComplete.get())
                .isNotNull()
                .extracting(m -> m.aiMessage().text())
                .isEqualTo("Hi! World! ");
    }

    @Test
    void streamingPartialResponsesNotShownOnError() {
        var repromptPartials = new ArrayList<String>();
        var repromptComplete = new AtomicReference<ChatResponse>();

        assertThatExceptionOfType(OutputGuardrailException.class).isThrownBy(() -> this.aiService
                .reprompt("1")
                .onPartialResponse(repromptPartials::add)
                .onError(t -> fail(t.getMessage()))
                .onCompleteResponse(repromptComplete::set)
                .start());

        assertThat(repromptPartials).isEmpty();
        assertThat(repromptComplete.get()).isNull();
    }

    public interface MyAiService {
        @UserMessage("Say Hi!")
        @OutputGuardrails(OKGuardrail.class)
        TokenStream ok(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOGuardrail.class)
        TokenStream ko(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(RepromptGuardrail.class)
        TokenStream reprompt(@MemoryId String mem);

        static MyAiService create() {
            return createAiService(
                    MyAiService.class, builder -> builder.streamingChatModel(new MyStreamingChatModel()));
        }
    }

    public static class OKGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            return success();
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class RepromptGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return reprompt("reorompt", "reprompt");
        }
    }

    public static class KOGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            if (responseFromLLM.text().length() > 3) { // Accumulated response.
                return failure("KO", new ValidationException("KO"));
            } else { // Chunk, do not fail on the first chunk
                if (responseFromLLM.text().contains("Hi!")) {
                    return success();
                } else {
                    return failure("KO", new ValidationException("KO"));
                }
            }
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class MyStreamingChatModel implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            handler.onPartialResponse("Hi!");
            handler.onPartialResponse(" ");
            handler.onPartialResponse("World!");
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("Hi! World! "))
                    .build());
        }
    }
}
