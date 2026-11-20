package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class GuardrailServiceTest {

    private final InputGuardrailRequest inputRequest = mock(InputGuardrailRequest.class);
    private final OutputGuardrailRequest outputRequest = mock(OutputGuardrailRequest.class);

    @Test
    void executeGuardrailsAsync_of_input_should_return_a_failed_future_when_the_input_guardrails_throw() {
        GuardrailService service = mock(GuardrailService.class, CALLS_REAL_METHODS);
        IllegalStateException failure = new IllegalStateException("cannot run the input guardrails");
        doThrow(failure).when(service).executeInputGuardrailsAsync(any(), any(InputGuardrailRequest.class));

        CompletableFuture<UserMessage> future = service.executeGuardrailsAsync("method", inputRequest);

        assertThat(future)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isSameAs(failure);
    }

    @Test
    void executeGuardrailsAsync_of_input_should_return_a_failed_future_when_the_input_guardrails_return_null() {
        GuardrailService service = mock(GuardrailService.class, CALLS_REAL_METHODS);
        doReturn(null).when(service).executeInputGuardrailsAsync(any(), any(InputGuardrailRequest.class));

        CompletableFuture<UserMessage> future = service.executeGuardrailsAsync("method", inputRequest);

        assertThat(future)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessageEndingWith(".executeInputGuardrailsAsync(Object, InputGuardrailRequest) returned null");
    }

    @Test
    void executeGuardrailsAsync_of_output_should_return_a_failed_future_when_the_output_guardrails_throw() {
        GuardrailService service = mock(GuardrailService.class, CALLS_REAL_METHODS);
        IllegalStateException failure = new IllegalStateException("cannot run the output guardrails");
        doThrow(failure).when(service).executeOutputGuardrailsAsync(any(), any(OutputGuardrailRequest.class));

        CompletableFuture<String> future = service.executeGuardrailsAsync("method", outputRequest);

        assertThat(future)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isSameAs(failure);
    }

    @Test
    void executeGuardrailsAsync_of_output_should_return_a_failed_future_when_the_output_guardrails_return_null() {
        GuardrailService service = mock(GuardrailService.class, CALLS_REAL_METHODS);
        doReturn(null).when(service).executeOutputGuardrailsAsync(any(), any(OutputGuardrailRequest.class));

        CompletableFuture<String> future = service.executeGuardrailsAsync("method", outputRequest);

        assertThat(future)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessageEndingWith(".executeOutputGuardrailsAsync(Object, OutputGuardrailRequest) returned null");
    }
}
