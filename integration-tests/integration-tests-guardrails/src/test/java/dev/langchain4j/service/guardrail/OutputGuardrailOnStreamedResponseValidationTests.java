package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OutputGuardrailOnStreamedResponseValidationTests extends BaseGuardrailTests {
    MyAiService aiService = MyAiService.create();

    @Test
    void ok() throws InterruptedException {
        assertThat(execute(() -> aiService.ok("1"))).isEqualTo("Hi! World! ");
    }

    @Test
    void ko() {
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> execute(() -> aiService.ko("2")))
                .withMessageContaining("KO");
    }

    @Test
    void retryOk() throws InterruptedException {
        var retry = SingletonClassInstanceFactory.getInstance(RetryingGuardrail.class);

        assertThat(execute(() -> aiService.retry("3"))).isEqualTo("Hi! World! ");
        assertThat(retry.spy()).isEqualTo(2);
    }

    @Test
    void fetryFail() {
        var retryFail = SingletonClassInstanceFactory.getInstance(RetryingButFailGuardrail.class);
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> execute(() -> aiService.retryButFail("4")))
                .withMessageContaining("maximum number of retries");
        assertThat(retryFail.spy()).isEqualTo(3);
    }

    @Test
    void fatalException() {
        var fatal = SingletonClassInstanceFactory.getInstance(KOFatalGuardrail.class);
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> execute(() -> aiService.fatal("5")))
                .withMessageContaining("Fatal");
        assertThat(fatal.spy()).isEqualTo(1);
    }

    @Test
    void rewritingWhileStreaming() throws InterruptedException {
        var rewriting = SingletonClassInstanceFactory.getInstance(RewritingGuardrail.class);
        assertThat(execute(() -> aiService.rewriting("1"))).isEqualTo("Hi! World! ,1");
        assertThat(rewriting.spy()).isEqualTo(1);
    }

    public interface MyAiService {
        @UserMessage("Say Hi!")
        @OutputGuardrails(OKGuardrail.class)
        TokenStream ok(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOGuardrail.class)
        TokenStream ko(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(value = RetryingGuardrail.class, maxRetries = 3)
        TokenStream retry(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(value = RetryingButFailGuardrail.class, maxRetries = 3)
        TokenStream retryButFail(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOFatalGuardrail.class)
        TokenStream fatal(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails({RewritingGuardrail.class})
        TokenStream rewriting(@MemoryId String mem);

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

    public static class KOGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            return failure("KO");
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class RetryingGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            int v = spy.incrementAndGet();
            if (v >= 2) {
                return OutputGuardrailResult.success();
            }
            return retry("KO");
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class RetryingButFailGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            return retry("KO");
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class KOFatalGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            throw new IllegalArgumentException("Fatal");
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class RewritingGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            String text = responseFromLLM.text();
            return successWith(text + ",1");
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
