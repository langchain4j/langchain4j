package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OutputGuardrailRepromptingTests extends BaseGuardrailTests {
    MyAiService aiService = MyAiService.create();

    @Test
    void repromptingOkAfterOneRetry() {
        var repromptingOne = SingletonClassInstanceFactory.getInstance(RepromptingOne.class);
        aiService.one("1", "foo");
        assertThat(repromptingOne.getSpy()).isEqualTo(2);
    }

    @Test
    void repromptingOkAfterTwoRetries() {
        var repromptingTwo = SingletonClassInstanceFactory.getInstance(RepromptingTwo.class);
        aiService.two("2", "foo");
        assertThat(repromptingTwo.getSpy()).isEqualTo(3);
    }

    @Test
    void repromptingFailing() {
        var repromptingFailed = SingletonClassInstanceFactory.getInstance(RepromptingFailed.class);
        assertThatExceptionOfType(OutputGuardrailException.class).isThrownBy(() -> aiService.fail("3", "foo"));
        assertThat(repromptingFailed.getSpy()).isEqualTo(3);
    }

    @SystemMessage("Say Hi!")
    public interface MyAiService {
        @OutputGuardrails(RepromptingOne.class)
        String one(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails(RepromptingTwo.class)
        String two(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails(RepromptingFailed.class)
        String fail(@MemoryId String mem, @UserMessage String message);

        static MyAiService create() {
            return createAiService(MyAiService.class, builder -> builder.chatLanguageModel(new MyChatModel()));
        }
    }

    public static class RepromptingOne implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            if (spy.incrementAndGet() == 1) {
                return reprompt("Retry", "Retry");
            }

            return success();
        }

        public int getSpy() {
            return spy.get();
        }
    }

    public static class RepromptingTwo implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest params) {
            int v = spy.incrementAndGet();
            var messages = params.commonParams().chatMemory().messages();

            if (v == 1) {
                ChatMessage last = messages.get(messages.size() - 1);
                assertThat(last).isInstanceOfSatisfying(AiMessage.class, am -> assertThat(am.text())
                        .isEqualTo("Nope"));
                assertThat(params.responseFromLLM().aiMessage().text()).isEqualTo("Nope");
                return reprompt("Retry", "Retry");
            }

            if (v == 2) {
                // Check that it's in memory
                ChatMessage last = messages.get(messages.size() - 1);
                ChatMessage beforeLast = messages.get(messages.size() - 2);

                assertThat(last).isInstanceOfSatisfying(AiMessage.class, am -> assertThat(am.text())
                        .isEqualTo("Hello"));
                assertThat(params.responseFromLLM().aiMessage().text()).isEqualTo("Hello");
                assertThat(beforeLast)
                        .isInstanceOfSatisfying(
                                dev.langchain4j.data.message.UserMessage.class,
                                um -> assertThat(um.singleText()).isEqualTo("Retry"));

                return reprompt("Retry", "Retry");
            }

            if (v != 3) {
                throw new IllegalArgumentException("Unexpected call");
            }

            return success();
        }

        public int getSpy() {
            return spy.get();
        }
    }

    public static class RepromptingFailed implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest params) {
            int v = spy.incrementAndGet();
            var messages = params.commonParams().chatMemory().messages();

            if (v == 1) {
                ChatMessage last = messages.get(messages.size() - 1);
                assertThat(last).isInstanceOfSatisfying(AiMessage.class, am -> assertThat(am.text())
                        .isEqualTo("Nope"));
                return reprompt("Retry", "Retry Once");
            }

            if (v == 2) {
                // Check that it's in memory
                ChatMessage last = messages.get(messages.size() - 1);
                ChatMessage beforeLast = messages.get(messages.size() - 2);

                assertThat(last).isInstanceOfSatisfying(AiMessage.class, am -> assertThat(am.text())
                        .isEqualTo("Hello"));
                assertThat(beforeLast)
                        .isInstanceOfSatisfying(
                                dev.langchain4j.data.message.UserMessage.class,
                                um -> assertThat(um.singleText()).isEqualTo("Retry Once"));
                return reprompt("Retry", "Retry Twice");
            }

            return reprompt("Retry", "Retry Again");
        }

        public int getSpy() {
            return spy.get();
        }
    }

    public static class MyChatModel implements ChatLanguageModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            var messages = chatRequest.messages();
            var last = messages.get(messages.size() - 1);

            if (last instanceof dev.langchain4j.data.message.UserMessage um) {
                if ("foo".equals(um.singleText())) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from("Nope"))
                            .build();
                }

                if (um.singleText().contains("Retry")) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from("Hello"))
                            .build();
                }
            }

            throw new IllegalArgumentException("Unexpected message: " + messages);
        }
    }
}
