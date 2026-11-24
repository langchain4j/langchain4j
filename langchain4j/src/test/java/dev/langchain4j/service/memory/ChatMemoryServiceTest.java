package dev.langchain4j.service.memory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatMemoryServiceTest {

    @Test
    void should_return_the_single_chat_memory_for_a_default_id_that_is_equal_but_not_the_same_instance() {

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        ChatMemoryService service = new ChatMemoryService(chatMemory);

        // for example read from configuration or parsed from JSON
        String defaultId = new String(ChatMemoryService.DEFAULT);

        assertThat(defaultId).isNotSameAs(ChatMemoryService.DEFAULT);
        assertThat(service.getChatMemory(defaultId)).isSameAs(chatMemory);
    }

    @Test
    void should_return_no_chat_memory_for_another_id_when_there_is_a_single_chat_memory() {

        ChatMemoryService service = new ChatMemoryService(MessageWindowChatMemory.withMaxMessages(10));

        assertThat(service.getChatMemory("user-42")).isNull();
        assertThat(service.getChatMemory(null)).isNull();
    }

    @Test
    void invocation_chat_memory_should_forward_every_method_of_chat_memory() throws Exception {
        // a method that is not overridden would run the interface's default instead of the user's chat memory
        Class<?> invocationChatMemory = Class.forName(ChatMemoryService.class.getName() + "$InvocationChatMemory");

        List<String> notForwarded = Arrays.stream(ChatMemory.class.getMethods())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> !isDeclaredBy(invocationChatMemory, method))
                .map(Method::toGenericString)
                .toList();

        assertThat(notForwarded).isEmpty();
    }

    private static boolean isDeclaredBy(Class<?> type, Method method) {
        try {
            type.getDeclaredMethod(method.getName(), method.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
