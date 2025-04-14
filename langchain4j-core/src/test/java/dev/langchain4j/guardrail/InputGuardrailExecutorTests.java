package dev.langchain4j.guardrail;

import static dev.langchain4j.test.guardrail.GuardrailAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.AggregateWith;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.aggregator.ArgumentsAggregationException;
import org.junit.jupiter.params.aggregator.ArgumentsAggregator;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class InputGuardrailExecutorTests {
    @ParameterizedTest(name = "{0}")
    @MethodSource("successGuardrails")
    void allSuccessfulGuardrails(
            @SuppressWarnings("unused") String testDesc,
            int howManyShouldExecute,
            @AggregateWith(InputGuardrailAggregator.class) InputGuardrail... guardrails) {

        var spiedGuardrails = Stream.of(guardrails).map(Mockito::spy).toArray(InputGuardrail[]::new);
        var params = from(UserMessage.from("test"));
        var executor =
                InputGuardrailExecutor.builder().guardrails(spiedGuardrails).build();
        var result = executor.execute(params);

        assertThat(result).isSuccessful();

        IntStream.range(0, howManyShouldExecute)
                .mapToObj(i -> (SuccessInputGuardrail) spiedGuardrails[i])
                .forEach(guardrail -> {
                    assertThat(guardrail.shouldBeExecuted).isTrue();
                    verify(guardrail).validate(params);
                });

        IntStream.range(howManyShouldExecute, spiedGuardrails.length)
                .mapToObj(i -> (SuccessInputGuardrail) spiedGuardrails[i])
                .forEach(guardrail -> {
                    assertThat(guardrail.shouldBeExecuted).isFalse();
                    verify(guardrail, never()).validate(params);
                });
    }

    @Test
    void noGuardrails() {
        var params = from(UserMessage.from("test"));
        var executor = InputGuardrailExecutor.builder().build();
        var result = executor.execute(params);

        assertThat(result).isSuccessful();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failedFatalGuardrails")
    void failedFatal(
            @SuppressWarnings("unused") String testDesc,
            int howManyShouldExecute,
            int howManyFailures,
            @AggregateWith(InputGuardrailAggregator.class) InputGuardrail... guardrails) {

        var spiedGuardrails = Stream.of(guardrails).map(Mockito::spy).toArray(InputGuardrail[]::new);
        var params = from(UserMessage.from("test"));
        var executor = InputGuardrailExecutor.builder()
                .guardrails(spiedGuardrails)
                .config(InputGuardrailsConfig.builder().build())
                .build();

        assertThatExceptionOfType(InputGuardrailException.class)
                .isThrownBy(() -> executor.execute(params))
                .withMessageMatching("The guardrail " + getClass().getName()
                        + "\\$.+Guardrail failed with this message: failure \\d");

        IntStream.range(0, howManyShouldExecute)
                .mapToObj(i -> spiedGuardrails[i])
                .forEach(guardrail -> {
                    var shouldBeExecuted = (guardrail instanceof SuccessInputGuardrail s)
                            ? s.shouldBeExecuted
                            : ((FailureInputGuardrail) guardrail).shouldBeExecuted;

                    assertThat(shouldBeExecuted).isTrue();
                    verify(guardrail).validate(params);
                });

        IntStream.range(howManyShouldExecute, spiedGuardrails.length)
                .mapToObj(i -> spiedGuardrails[i])
                .forEach(guardrail -> {
                    var shouldBeExecuted = (guardrail instanceof SuccessInputGuardrail s)
                            ? s.shouldBeExecuted
                            : ((FailureInputGuardrail) guardrail).shouldBeExecuted;

                    assertThat(shouldBeExecuted).isFalse();
                    verify(guardrail, never()).validate(params);
                });

        var numFailedGuardrails = Stream.of(spiedGuardrails)
                .filter(FailureInputGuardrail.class::isInstance)
                .map(FailureInputGuardrail.class::cast)
                .filter(guardrail -> guardrail.shouldBeExecuted)
                .count();

        assertThat(numFailedGuardrails).isEqualTo(howManyFailures);
    }

    static Stream<Arguments> successGuardrails() {
        return Stream.of(
                Arguments.of("No guardrails", 0),
                Arguments.of("One successful guardrail", 1, new SuccessInputGuardrail()),
                Arguments.of("Two successful guardrails", 2, new SuccessInputGuardrail(), new SuccessInputGuardrail()),
                Arguments.of(
                        "Three successful guardrails",
                        3,
                        new SuccessInputGuardrail(),
                        new SuccessInputGuardrail(),
                        new SuccessInputGuardrail()));
    }

    static Stream<Arguments> failedFatalGuardrails() {
        return Stream.of(
                Arguments.of(
                        "One successful one fatal guardrail",
                        2,
                        1,
                        new SuccessInputGuardrail(),
                        new FatalInputGuardrail(1)),
                Arguments.of(
                        "One fatal one successful guardrail",
                        1,
                        1,
                        new FatalInputGuardrail(1),
                        new SuccessInputGuardrail(false)),
                Arguments.of(
                        "One successful one fatal one successful guardrails",
                        2,
                        1,
                        new SuccessInputGuardrail(),
                        new FatalInputGuardrail(1),
                        new SuccessInputGuardrail(false)),
                Arguments.of(
                        "One successful one fatal one failed guardrails",
                        2,
                        1,
                        new SuccessInputGuardrail(),
                        new FatalInputGuardrail(1),
                        new FailureInputGuardrail<>(2).shouldNotBeExecuted()),
                Arguments.of(
                        "One failure one successful guardrail",
                        2,
                        1,
                        new FailureInputGuardrail<>(1),
                        new SuccessInputGuardrail()),
                Arguments.of(
                        "One successful one failure one successful guardrails",
                        3,
                        1,
                        new SuccessInputGuardrail(),
                        new FailureInputGuardrail<>(1),
                        new SuccessInputGuardrail()),
                Arguments.of(
                        "One successful one fatal one failure guardrails",
                        2,
                        1,
                        new SuccessInputGuardrail(),
                        new FatalInputGuardrail(1),
                        new FailureInputGuardrail<>(2).shouldNotBeExecuted()),
                Arguments.of(
                        "Two failure guardrails", 2, 2, new FailureInputGuardrail<>(1), new FailureInputGuardrail<>(2)),
                Arguments.of(
                        "One successful one failure one fatal one failure guardrails",
                        3,
                        2,
                        new SuccessInputGuardrail(),
                        new FailureInputGuardrail<>(2),
                        new FatalInputGuardrail(1),
                        new FailureInputGuardrail<>(3).shouldNotBeExecuted()));
    }

    public static InputGuardrailRequest from(UserMessage userMessage) {
        var newCommonParams = GuardrailRequestParams.builder()
                .chatMemory(null)
                .augmentationResult(null)
                .userMessageTemplate("")
                .variables(Map.of())
                .build();

        return InputGuardrailRequest.builder()
                .userMessage(userMessage)
                .commonParams(newCommonParams)
                .build();
    }

    private static class FatalInputGuardrail extends FailureInputGuardrail<FatalInputGuardrail> {
        private FatalInputGuardrail(int failureNumber) {
            super(failureNumber);
        }

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return fatal(this.failureMessage);
        }
    }

    private static class FailureInputGuardrail<G extends FailureInputGuardrail> implements InputGuardrail {
        protected final String failureMessage;
        private boolean shouldBeExecuted = true;

        private FailureInputGuardrail(int failureNumber) {
            this("failure " + failureNumber);
        }

        private FailureInputGuardrail(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        G shouldNotBeExecuted() {
            this.shouldBeExecuted = false;
            return (G) this;
        }

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return failure(this.failureMessage);
        }
    }

    private static class SuccessInputGuardrail implements InputGuardrail {
        private boolean shouldBeExecuted = true;

        SuccessInputGuardrail(boolean shouldBeExecuted) {
            this.shouldBeExecuted = shouldBeExecuted;
        }

        SuccessInputGuardrail() {
            this(true);
        }

        @Override
        public InputGuardrailResult validate(final UserMessage userMessage) {
            return InputGuardrailResult.success();
        }
    }

    static class InputGuardrailAggregator implements ArgumentsAggregator {
        @Override
        public Object aggregateArguments(ArgumentsAccessor accessor, ParameterContext context)
                throws ArgumentsAggregationException {

            return accessor.toList().stream()
                    .skip(context.getIndex())
                    .map(InputGuardrail.class::cast)
                    .toArray(InputGuardrail[]::new);
        }
    }
}
