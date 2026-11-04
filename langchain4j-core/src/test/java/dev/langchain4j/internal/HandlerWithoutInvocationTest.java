package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class HandlerWithoutInvocationTest {

    @Test
    void should_forward_every_method_of_streaming_chat_response_handler() {
        List<String> notForwarded = Arrays.stream(StreamingChatResponseHandler.class.getMethods())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> !isDeclaredBy(HandlerWithoutInvocation.class, method))
                .map(Method::toString)
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
