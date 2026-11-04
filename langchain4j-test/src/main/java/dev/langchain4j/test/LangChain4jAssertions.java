package dev.langchain4j.test;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.test.guardrail.InputGuardrailResultAssert;
import dev.langchain4j.test.guardrail.OutputGuardrailResultAssert;
import dev.langchain4j.test.judge.Judge;
import dev.langchain4j.test.model.ChatRequestAssert;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceInvocationAssert;
import dev.langchain4j.test.service.AiServiceCapture;
import org.assertj.core.api.Assertions;

/**
 * The entry point for all LangChain4j assertions, and for all AssertJ assertions, with a single static import of
 * {@code assertThat}. To capture AI Service invocations, also import {@link AiServiceCapture#capture}:
 * <pre>{@code
 * import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
 * import static dev.langchain4j.test.service.AiServiceCapture.capture;
 *
 * AiServiceCapture capture = capture(() -> assistant.chat("Cancel booking MS-777"));
 *
 * assertThat(capture).hasToolExecuted("cancelBooking");
 * assertThat(capture.outputText()).contains("cancelled");
 * }</pre>
 * This works for every return type of the AI Service method, also {@code Result<...>}: capture the call, and use
 * {@code capture.output()} for what it returned. These assertions are deterministic: they check what happened,
 * without calling a model. To check a text with natural-language criteria, using a model as a judge, see
 * {@link Judge}.
 * <p>
 * Only the assertions on captured AI Service invocations ({@link AiServiceCapture} and {@link AiServiceInvocation})
 * need the {@code langchain4j} module, where AI Services are defined: add it with the same version as your other
 * LangChain4j modules to capture AI Service invocations. The other assertions of this class, for example on a
 * {@link ChatRequest} or a guardrail result, also work without it.
 */
@Experimental
public class LangChain4jAssertions extends Assertions {

    protected LangChain4jAssertions() {}

    /**
     * Starts assertions on the given AI Service invocation, captured with {@link AiServiceCapture}.
     */
    public static AiServiceInvocationAssert assertThat(AiServiceInvocation actual) {
        return new AiServiceInvocationAssert(actual);
    }

    /**
     * Starts assertions on the only top-level AI Service invocation of the given capture, made with
     * {@link AiServiceCapture}.
     *
     * @throws AssertionError if the capture has no top-level invocation or more than one: use
     *                        {@code assertThat(capture.invocations().get(i))} then
     */
    public static AiServiceInvocationAssert assertThat(AiServiceCapture actual) {
        return new AiServiceInvocationAssert(actual);
    }

    /**
     * Starts assertions on the given request sent to a chat model, for example {@code capture.firstRequest()}.
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
