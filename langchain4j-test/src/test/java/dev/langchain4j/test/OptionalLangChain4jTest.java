package dev.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.spi.observability.ObservabilityListenersProvider;
import dev.langchain4j.test.service.internal.InvocationRegistry;
import dev.langchain4j.test.service.internal.CaptureListenersProvider;
import java.util.ServiceLoader;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class OptionalLangChain4jTest {

    @Test
    @EnabledIfSystemProperty(named = "withoutLangChain4j", matches = "true")
    void langchain4j_should_be_absent_in_the_run_without_it() {
        assertThatThrownBy(() -> Class.forName("dev.langchain4j.service.Result"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void entry_point_should_work_without_langchain4j() {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Cancel MS-777"))
                .build();

        LangChain4jAssertions.assertThat(request).hasUserMessageContaining("MS-777");
        LangChain4jAssertions.assertThat("cancelled").contains("cancel");
        LangChain4jAssertions.assertThat(InputGuardrailResult.success()).isSuccessful();
    }

    @Test
    void recording_listeners_should_load_and_ignore_events_when_nothing_is_recorded() {
        assertThat(ServiceLoader.load(ObservabilityListenersProvider.class).stream().map(ServiceLoader.Provider::get))
                .hasAtLeastOneElementOfType(CaptureListenersProvider.class);

        AiServiceListenerRegistrar registrar = AiServiceListenerRegistrar.newInstance();
        InvocationContext invocationContext = InvocationContext.builder()
                .invocationId(UUID.randomUUID())
                .timestampNow()
                .build();

        assertThatCode(() -> {
                    registrar.fireEvent(AiServiceStartedEvent.builder()
                            .invocationContext(invocationContext)
                            .userMessage(UserMessage.from("Hello"))
                            .build());
                    registrar.fireEvent(AiServiceCompletedEvent.builder()
                            .invocationContext(invocationContext)
                            .build());
                })
                .doesNotThrowAnyException();
        assertThat(InvocationRegistry.isListening()).isTrue();
    }
}
