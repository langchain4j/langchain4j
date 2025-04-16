package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Index.atIndex;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InputGuardrailValidationTests extends BaseGuardrailTests {
    MyAiService aiService = MyAiService.create();

    @Test
    void ok() {
        var okGuardrail = SingletonClassInstanceFactory.getInstance(OKGuardrail.class);
        aiService.ok("1");
        assertThat(okGuardrail.spy()).isEqualTo(1);
        aiService.ok("2");
        assertThat(okGuardrail.spy()).isEqualTo(2);
    }

    @Test
    void ko() {
        assertThatThrownBy(() -> aiService.ko("2"))
                .isInstanceOf(InputGuardrailException.class)
                .hasMessageContaining("KO");
    }

    @Test
    void okStreaming() throws InterruptedException {
        var latch = new CountDownLatch(1);
        var text = new AtomicReference<String>();
        var partialResponses = new ArrayList<String>();

        aiService
                .okStream("1")
                .onError(t -> latch.countDown())
                .onPartialResponse(partialResponses::add)
                .onCompleteResponse(response -> {
                    text.set(response.aiMessage().text());
                    latch.countDown();
                })
                .start();

        latch.await(10, TimeUnit.SECONDS);

        assertThat(String.join(" ", text.get())).isEqualTo("Streaming hi !");
        assertThat(String.join(" ", partialResponses)).isEqualTo(text.get());

        var okGuardrail = SingletonClassInstanceFactory.getInstance(OKGuardrail.class);
        assertThat(okGuardrail.spy()).isEqualTo(1);
    }

    @Test
    void koStreaming() {
        assertThatExceptionOfType(InputGuardrailException.class)
                .isThrownBy(() -> aiService.koStream("2"))
                .withMessageContaining("KO");

        var koGuardrail = SingletonClassInstanceFactory.getInstance(KOGuardrail.class);
        assertThat(koGuardrail.spy()).isEqualTo(1);
    }

    @Test
    void fatalException() {
        assertThatExceptionOfType(InputGuardrailException.class)
                .isThrownBy(() -> aiService.fatal("5"))
                .withMessageContaining("Fatal");

        var fatal = SingletonClassInstanceFactory.getInstance(KOFatalGuardrail.class);
        assertThat(fatal.spy()).isEqualTo(1);
    }

    @Test
    void memoryCheck() {
        var memoryCheck = SingletonClassInstanceFactory.getInstance(MemoryCheck.class);
        aiService.test("1", "foo");
        assertThat(memoryCheck.spy()).isEqualTo(1);

        aiService.test("1", "bar");
        assertThat(memoryCheck.spy()).isEqualTo(2);
    }

    public interface MyAiService {
        @UserMessage("Say Hi!")
        @InputGuardrails(OKGuardrail.class)
        String ok(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @InputGuardrails(KOGuardrail.class)
        String ko(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @InputGuardrails(OKGuardrail.class)
        TokenStream okStream(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @InputGuardrails(KOGuardrail.class)
        TokenStream koStream(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @InputGuardrails(KOFatalGuardrail.class)
        String fatal(@MemoryId String mem);

        @InputGuardrails(MemoryCheck.class)
        String test(@MemoryId String name, @UserMessage String message);

        static MyAiService create() {
            return createAiService(MyAiService.class, builder -> builder.chatModel(new MyChatModel())
                    .streamingChatModel(new MyStreamingChatModel()));
        }
    }

    public static class OKGuardrail implements InputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage um) {
            spy.incrementAndGet();
            return success();
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class KOGuardrail implements InputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage um) {
            spy.incrementAndGet();
            return failure("KO");
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class KOFatalGuardrail implements InputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage um) {
            spy.incrementAndGet();
            throw new IllegalArgumentException("Fatal");
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class MemoryCheck implements InputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public InputGuardrailResult validate(InputGuardrailRequest params) {
            spy.incrementAndGet();
            var messages = Optional.ofNullable(params.requestParams().chatMemory())
                    .map(ChatMemory::messages)
                    .orElseGet(List::of);

            if (messages.isEmpty()) {
                assertThat(params.userMessage().singleText()).isEqualTo("foo");
            }

            if (messages.size() == 2) {
                assertThat(messages)
                        .satisfies(
                                message -> assertThat(message)
                                        .isInstanceOf(dev.langchain4j.data.message.UserMessage.class)
                                        .extracting(m -> ((dev.langchain4j.data.message.UserMessage) m).singleText())
                                        .isEqualTo("foo"),
                                atIndex(0))
                        .satisfies(
                                message -> assertThat(message)
                                        .isInstanceOf(AiMessage.class)
                                        .extracting(m -> ((AiMessage) m).text())
                                        .isEqualTo("Hi!"),
                                atIndex(1));

                assertThat(params.userMessage().singleText()).isEqualTo("bar");
            }
            return success();
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from("Hi!")).build();
        }
    }

    public static class MyStreamingChatModel implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            handler.onPartialResponse("Streaming hi");
            handler.onPartialResponse("!");
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("Streaming hi !"))
                    .build());
        }
    }
}
