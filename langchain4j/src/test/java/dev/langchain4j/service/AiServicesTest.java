package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

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

    private final Assistant assistant = AiServices.builder(Assistant.class)
            .chatModel(chatModel)
            .build();

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
        assertThat(assistant.equals(AiServices.create(Assistant.class, chatModel))).isFalse();
    }

    @Test
    void test_toString() throws Exception {
        assertThat(assistant.toString()).startsWith("dev.langchain4j.service.AiServicesTest$Assistant@");
    }
}
