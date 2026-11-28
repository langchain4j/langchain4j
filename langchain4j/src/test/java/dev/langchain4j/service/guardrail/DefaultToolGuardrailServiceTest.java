package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailExecutor;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailExecutor;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.guardrail.config.ToolOutputGuardrailsConfig;
import dev.langchain4j.invocation.InvocationContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class DefaultToolGuardrailServiceTest {

    interface Assistant {}

    private static final ToolExecutionRequest CALL = ToolExecutionRequest.builder()
            .id("1")
            .name("search")
            .arguments("{}")
            .build();
    private static final GuardrailRequestParams PARAMS = GuardrailRequestParams.builder()
            .userMessageTemplate("t")
            .variables(Map.of())
            .invocationContext(InvocationContext.builder().build())
            .build();
    private static final ToolInputGuardrailRequest INPUT_REQUEST = ToolInputGuardrailRequest.builder()
            .toolExecutionRequest(CALL)
            .toolSpecification(ToolSpecification.builder().name("search").build())
            .requestParams(PARAMS)
            .build();
    private static final ToolOutputGuardrailRequest OUTPUT_REQUEST = ToolOutputGuardrailRequest.builder()
            .toolExecutionRequest(CALL)
            .toolSpecification(ToolSpecification.builder().name("search").build())
            .requestParams(PARAMS)
            .resultContents(List.of(TextContent.from("found")))
            .isError(false)
            .build();

    private static final ToolInputGuardrail ACCEPTING_INPUT = new ToolInputGuardrail() {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return success();
        }
    };
    private static final ToolOutputGuardrail ACCEPTING_OUTPUT = new ToolOutputGuardrail() {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            return success();
        }
    };

    /**
     * An input executor that returns {@code null} instead of a result and of a future.
     */
    private static final class NullInputExecutor extends ToolInputGuardrailExecutor {
        NullInputExecutor(List<ToolInputGuardrail> guardrails) {
            super(ToolInputGuardrailsConfig.builder().build(), guardrails);
        }

        @Override
        public ToolInputGuardrailResult execute(ToolInputGuardrailRequest request) {
            return null;
        }

        @Override
        public CompletableFuture<ToolInputGuardrailResult> executeAsync(ToolInputGuardrailRequest request) {
            return null;
        }
    }

    /**
     * An output executor that returns {@code null} instead of a result and of a future.
     */
    private static final class NullOutputExecutor extends ToolOutputGuardrailExecutor {
        NullOutputExecutor(List<ToolOutputGuardrail> guardrails) {
            super(ToolOutputGuardrailsConfig.builder().build(), guardrails);
        }

        @Override
        public ToolOutputGuardrailResult execute(ToolOutputGuardrailRequest request) {
            return null;
        }

        @Override
        public CompletableFuture<ToolOutputGuardrailResult> executeAsync(ToolOutputGuardrailRequest request) {
            return null;
        }
    }

    private static DefaultToolGuardrailService service() {
        return (DefaultToolGuardrailService)
                ToolGuardrailService.builder(Assistant.class).build();
    }

    private static DefaultToolKey keyOf(
            DefaultToolGuardrailService service,
            Optional<ToolInputGuardrailExecutor> inputExecutor,
            Optional<ToolOutputGuardrailExecutor> outputExecutor) {
        return new DefaultToolKey(
                service,
                ToolSpecification.builder().name("search").build(),
                ToolSource.programmatic(),
                inputExecutor,
                outputExecutor);
    }

    @Test
    void a_null_result_of_an_executor_fails_instead_of_passing_as_no_guardrails() {
        var service = service();
        var key = keyOf(
                service,
                Optional.of(new NullInputExecutor(List.of(ACCEPTING_INPUT))),
                Optional.of(new NullOutputExecutor(List.of(ACCEPTING_OUTPUT))));

        assertThatThrownBy(() -> service.executeInputGuardrails(key, INPUT_REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("result of the guardrail executor");
        assertThatThrownBy(() -> service.executeOutputGuardrails(key, OUTPUT_REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("result of the guardrail executor");
    }

    @Test
    void a_null_future_of_an_executor_fails_the_future_instead_of_passing_as_no_guardrails() {
        var service = service();
        var key = keyOf(
                service,
                Optional.of(new NullInputExecutor(List.of(ACCEPTING_INPUT))),
                Optional.of(new NullOutputExecutor(List.of(ACCEPTING_OUTPUT))));

        assertThat(service.executeInputGuardrailsAsync(key, INPUT_REQUEST))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessageContaining("returned null");
        assertThat(service.executeOutputGuardrailsAsync(key, OUTPUT_REQUEST))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessageContaining("returned null");
    }

    @Test
    void a_key_without_executors_has_no_guardrails_and_succeeds() {
        var service = service();
        var key = keyOf(service, Optional.empty(), Optional.empty());

        assertThat(service.hasInputGuardrails(key)).isFalse();
        assertThat(service.hasOutputGuardrails(key)).isFalse();
        assertThat(service.executeInputGuardrails(key, INPUT_REQUEST).isSuccess())
                .isTrue();
        assertThat(service.executeOutputGuardrails(key, OUTPUT_REQUEST).isSuccess())
                .isTrue();
        assertThat(service.executeInputGuardrailsAsync(key, INPUT_REQUEST))
                .succeedsWithin(Duration.ofSeconds(5))
                .satisfies(result -> assertThat(result.isSuccess()).isTrue());
        assertThat(service.executeOutputGuardrailsAsync(key, OUTPUT_REQUEST))
                .succeedsWithin(Duration.ofSeconds(5))
                .satisfies(result -> assertThat(result.isSuccess()).isTrue());
    }

    @Test
    void an_executor_without_guardrails_does_not_count_as_guardrails() {
        var service = service();
        var key = keyOf(
                service,
                Optional.of(new NullInputExecutor(List.of())),
                Optional.of(new NullOutputExecutor(List.of())));

        assertThat(service.hasInputGuardrails(key)).isFalse();
        assertThat(service.hasOutputGuardrails(key)).isFalse();
    }

    @Test
    void the_input_config_is_the_one_given_to_the_builder_or_one_default_config() {
        var config = ToolInputGuardrailsConfig.builder().build();
        var configured = ToolGuardrailService.builder(Assistant.class)
                .inputGuardrailsConfig(config)
                .build();
        var unconfigured = ToolGuardrailService.builder(Assistant.class).build();

        assertThat(configured.toolInputGuardrailsConfig()).isSameAs(config);
        // read for every tool result, so it is not resolved anew each time
        assertThat(unconfigured.toolInputGuardrailsConfig())
                .isNotNull()
                .isSameAs(unconfigured.toolInputGuardrailsConfig());
    }
}
