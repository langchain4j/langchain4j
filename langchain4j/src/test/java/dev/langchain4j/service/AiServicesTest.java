package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.mockito.NotExtensible;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

class AiServicesTest {

    interface Assistant {

        String chat(String message);

        default int getInt() {
            return 42;
        }

        default int getException() {
            throw new RuntimeException("oops");
        }
    }

    private final ChatModel chatModel = mock(ChatModel.class);

    private final Assistant assistant =
            AiServices.builder(Assistant.class).chatModel(chatModel).build();
    private <T> T buildService(Class<T> type) {
        return AiServices.builder(type).chatModel(chatModel).build();
    }

    private void stubChatModelToReturnHi() {
        when(chatModel.chat((ChatRequest) any()))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.builder().text("Hi").build())
                        .build());
    }
    @Test
    void should_not_call_chatModel_when_Object_methods_are_called() throws Exception {

        // when
        assistant.getClass();
        assistant.hashCode();
        assistant.equals(assistant);
        assistant.toString();
        synchronized (assistant) {
            assistant.notify();
            assistant.notifyAll();
        }

        // then
        verifyNoInteractions(chatModel);
    }

    @Test
    void test_default_method() {
        assertThat(assistant.getInt()).isEqualTo(42);
        verifyNoInteractions(chatModel);
    }

    @Test
    void test_default_method_throwing_exception() {
        assertThatThrownBy(() -> assistant.getException())
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("oops");
        verifyNoInteractions(chatModel);
    }

    @Test
    void test_getClass() {
        // dynamic proxies don't intercept final methods
        assertThat(assistant.getClass()).isNotEqualTo(Assistant.class);
    }

    @Test
    void test_hashCode() {
        assertThat(assistant.hashCode())
                .isNotZero()
                .isEqualTo(assistant.hashCode())
                .isEqualTo(System.identityHashCode(assistant))
                .isEqualTo(System.identityHashCode(assistant))
                .isNotEqualTo(AiServices.create(Assistant.class, chatModel).hashCode());
    }

    @Test
    void test_equals() throws Exception {
        assertThat(assistant.equals(assistant)).isTrue();
        assertThat(assistant.equals(new Object())).isFalse();
        assertThat(assistant.equals(AiServices.create(Assistant.class, chatModel)))
                .isFalse();
    }

    @Test
    void test_toString() throws Exception {
        assertThat(assistant.toString()).startsWith("dev.langchain4j.service.AiServicesTest$Assistant@");
    }

    @Test
    void should_handle_null_chat_model() {
        assertThatThrownBy(() ->
                        AiServices.builder(Assistant.class).chatModel(null).build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("chatModel");
    }

    @Test
    void should_handle_non_interface_class() {
        assertThatThrownBy(() ->
                        AiServices.builder(String.class).chatModel(chatModel).build())
                .isInstanceOf(IllegalConfigurationException.class);
    }

    @Test
    void should_create_different_instances_for_same_interface() {
        Assistant assistant1 =
                AiServices.builder(Assistant.class).chatModel(chatModel).build();

        Assistant assistant2 =
                AiServices.builder(Assistant.class).chatModel(chatModel).build();

        assertThat(assistant1).isNotSameAs(assistant2);
        assertThat(assistant1.equals(assistant2)).isFalse();
        assertThat(assistant1.hashCode()).isNotEqualTo(assistant2.hashCode());
    }

    @Test
    void should_preserve_interface_type() {
        assertThat(assistant).isInstanceOf(Assistant.class);
        assertThat(Assistant.class.isAssignableFrom(assistant.getClass())).isTrue();
    }


    /**
     * Regression test for https://github.com/langchain4j/langchain4j/issues/3091
     * Verifies that single-argument defaulting still works when a non-langchain4j
     * annotation is present on the parameter.
     */
    @Test
    void should_detect_single_parameter_as_user_message_even_with_other_annotations() {
        interface TaskService {
            // Using @NotExtensible here on purpose as a non-langchain4j annotation.
            // It is already available on the classpath; no additional dependency is added for this test.
            String task(@NotExtensible String msg);
        }

        stubChatModelToReturnHi();
        TaskService taskService = buildService(TaskService.class);

        assertThat(taskService.task("Hello")).isEqualTo("Hi");
    }

    @Test
    void should_use_annotated_user_message_with_multiple_parameters() {
        interface TaskService {
            String task(@UserMessage String msg, @V("other") String other);
        }

        stubChatModelToReturnHi(); // when(chatModel.chat(any(ChatRequest.class))) → "Hi"
        TaskService taskService = buildService(TaskService.class);

        assertThat(taskService.task("Hello", "ignored")).isEqualTo("Hi");
    }

    @Test
    void should_fail_when_no_user_message_and_multiple_parameters() {
        interface TaskService {
            String task(String a, String b);
        }

        stubChatModelToReturnHi();
        TaskService taskService = buildService(TaskService.class);

        assertThatThrownBy(() -> taskService.task("a", "b")).isInstanceOf(IllegalConfigurationException.class);
    }

    @Test
    void should_use_annotated_user_message_when_present() {
        interface TaskService {
            String task(@UserMessage String msg);
        }

        stubChatModelToReturnHi();
        TaskService taskService = buildService(TaskService.class);

        assertThat(taskService.task("Hello")).isEqualTo("Hi");
    }

    @Test
    void should_detect_single_parameter_as_user_message_without_annotations() {
        interface TaskService {
            String task(String msg);
        }

        stubChatModelToReturnHi();
        TaskService taskService = buildService(TaskService.class);

        assertThat(taskService.task("Hello")).isEqualTo("Hi");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    private @interface ExternalAnnotation1 {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    private @interface ExternalAnnotation2 {}

    @Test
    void should_detect_single_parameter_as_user_message_with_multiple_external_annotations() {
        interface TaskService {
            // Multiple non-langchain4j annotations used intentionally for this test case.
            String task(@ExternalAnnotation1 @ExternalAnnotation2 String msg);
        }

        stubChatModelToReturnHi();
        TaskService taskService = buildService(TaskService.class);

        assertThat(taskService.task("Hello")).isEqualTo("Hi");
    }
}
