package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;

class InputGuardrailRewritingTests extends BaseGuardrailTests {
    @Test
    void rewriting() {
        assertThat(MyAiService.create().test("first prompt", "second prompt"))
                .hasSize(MessageTruncatingGuardrail.MAX_LENGTH);
    }

    public interface MyAiService {
        @UserMessage("Given {{first}} and {{second}} do something")
        @InputGuardrails(MessageTruncatingGuardrail.class)
        String test(@V("first") String first, @V("second") String second);

        static MyAiService create() {
            return createAiService(MyAiService.class, builder -> builder.chatModel(new EchoChatModel()));
        }
    }

    public static class MessageTruncatingGuardrail implements InputGuardrail {
        static final int MAX_LENGTH = 20;

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage um) {
            String text = um.singleText();
            return successWith(text.substring(0, MAX_LENGTH));
        }
    }
}
