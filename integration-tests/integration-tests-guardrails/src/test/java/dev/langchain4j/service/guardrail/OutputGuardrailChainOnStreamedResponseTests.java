package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class OutputGuardrailChainOnStreamedResponseTests extends BaseGuardrailTests {
    MyAiService aiService = MyAiService.create();

    @Test
    void guardrailChainsAreInvoked() throws InterruptedException {
        var firstGuardrail = SingletonClassInstanceFactory.getInstance(FirstGuardrail.class);
        var secondGuardrail = SingletonClassInstanceFactory.getInstance(SecondGuardrail.class);

        var value = execute(() -> aiService.firstOneTwo("1", "foo"));
        assertThat(value).isEqualTo("Hi! World! ");
        assertThat(firstGuardrail.spy()).isEqualTo(1);
        assertThat(secondGuardrail.spy()).isEqualTo(1);
        assertThat(firstGuardrail.lastAccess()).isLessThan(secondGuardrail.lastAccess());
    }

    @Test
    void guardrailOrderIsCorrect() throws InterruptedException {
        var firstGuardrail = SingletonClassInstanceFactory.getInstance(FirstGuardrail.class);
        var secondGuardrail = SingletonClassInstanceFactory.getInstance(SecondGuardrail.class);

        var value = execute(() -> aiService.twoAndFirst("1", "foo"));
        assertThat(value).isEqualTo("Hi! World! ");
        assertThat(firstGuardrail.spy()).isEqualTo(1);
        assertThat(secondGuardrail.spy()).isEqualTo(1);
        assertThat(secondGuardrail.lastAccess()).isLessThan(firstGuardrail.lastAccess());
    }

    @Test
    void retryRestartsTheChain() throws InterruptedException {
        var firstGuardrail = SingletonClassInstanceFactory.getInstance(FirstGuardrail.class);
        var secondGuardrail = SingletonClassInstanceFactory.getInstance(SecondGuardrail.class);
        var failingGuardrail = SingletonClassInstanceFactory.getInstance(FailingGuardrail.class);
        var value = execute(() -> aiService.failingFirstTwo("1", "foo"));
        assertThat(value).isEqualTo("Hi! World! ");
        assertThat(firstGuardrail.spy()).isEqualTo(2);
        assertThat(secondGuardrail.spy()).isEqualTo(1);
        assertThat(failingGuardrail.spy()).isEqualTo(2);
        assertThat(firstGuardrail.lastAccess()).isLessThan(secondGuardrail.lastAccess());
    }

    public interface MyAiService {
        @OutputGuardrails({FirstGuardrail.class, SecondGuardrail.class})
        TokenStream firstOneTwo(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({SecondGuardrail.class, FirstGuardrail.class})
        TokenStream twoAndFirst(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({FirstGuardrail.class, FailingGuardrail.class, SecondGuardrail.class})
        TokenStream failingFirstTwo(@MemoryId String mem, @UserMessage String message);

        static MyAiService create() {
            return createAiService(
                    MyAiService.class, builder -> builder.streamingChatModel(new MyStreamingChatModel()));
        }
    }

    public static class FirstGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);
        private volatile AtomicLong lastAccess = new AtomicLong();

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            lastAccess.set(System.nanoTime());
            try {
                TimeUnit.MILLISECONDS.sleep(1);
            } catch (InterruptedException e) {
                // Ignore me
            }
            return success();
        }

        public int spy() {
            return spy.get();
        }

        public long lastAccess() {
            return lastAccess.get();
        }
    }

    public static class SecondGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);
        private volatile AtomicLong lastAccess = new AtomicLong();

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            lastAccess.set(System.nanoTime());
            try {
                TimeUnit.MILLISECONDS.sleep(1);
            } catch (InterruptedException e) {
                // Ignore me
            }
            return success();
        }

        public int spy() {
            return spy.get();
        }

        public long lastAccess() {
            return lastAccess.get();
        }
    }

    public static class FailingGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            if (spy.incrementAndGet() == 1) {
                return reprompt("Retry", "Retry");
            }
            return success();
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
