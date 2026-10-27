package dev.langchain4j.test;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.test.guardrail.InputGuardrailResultAssert;
import dev.langchain4j.test.guardrail.OutputGuardrailResultAssert;
import dev.langchain4j.test.judge.Judge;
import dev.langchain4j.test.model.ChatRequestAssert;
import org.assertj.core.api.Assertions;

/**
 * The entry point for the deterministic LangChain4j assertions and all AssertJ assertions, with a single static
 * import:
 * <pre>{@code
 * import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
 *
 * assertThat(request).hasUserMessageContaining("MS-777");
 * assertThat(guardrailResult).isSuccessful();
 * assertThat(answer).contains("cancelled");
 * }</pre>
 * These assertions are deterministic: they check what happened, without calling a model. To check a text with
 * natural-language criteria, using a model as a judge, see {@link Judge}.
 */
@Experimental
public class LangChain4jAssertions extends Assertions {

    protected LangChain4jAssertions() {}

    /**
     * Starts assertions on the given request sent to a chat model.
     */
    public static ChatRequestAssert assertThat(ChatRequest actual) {
        return new ChatRequestAssert(actual);
    }

    /**
     * Starts assertions on the given input guardrail result.
     */
    public static InputGuardrailResultAssert assertThat(InputGuardrailResult actual) {
        return InputGuardrailResultAssert.assertThat(actual);
    }

    /**
     * Starts assertions on the given output guardrail result.
     */
    public static OutputGuardrailResultAssert assertThat(OutputGuardrailResult actual) {
        return OutputGuardrailResultAssert.assertThat(actual);
    }
}
