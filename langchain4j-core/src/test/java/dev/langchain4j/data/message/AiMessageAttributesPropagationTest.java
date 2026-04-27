package dev.langchain4j.data.message;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

/**
 * Regression test for issue #4844: AiMessage.attributes should be mutable
 * so that tools and middlewares can write context mid-pipeline.
 *
 * This test verifies:
 * 1. AiMessage.attributes() returns a mutable map (ConcurrentHashMap)
 * 2. Tools can modify attributes during execution
 * 3. The immutable map copy semantics work correctly with toBuilder()
 * 4. ChatModelRequestContext.current() provides thread-safe context access
 */
class AiMessageAttributesPropagationTest implements WithAssertions {

    @Test
    void ai_message_attributes_should_be_mutable() {

        // Verify that AiMessage.attributes() returns a mutable map
        AiMessage message = AiMessage.from("test");
        Map<String, Object> attrs = message.attributes();

        // Should be able to add new attributes
        attrs.put("key1", "value1");
        assertThat(attrs).containsEntry("key1", "value1");
        assertThat(message.attributes()).containsEntry("key1", "value1");

        // Should be able to update existing attributes
        attrs.put("key1", "updated-value");
        assertThat(attrs).containsEntry("key1", "updated-value");
        assertThat(message.attributes()).containsEntry("key1", "updated-value");

        // Should be able to remove attributes
        attrs.remove("key1");
        assertThat(attrs).doesNotContainKey("key1");
        assertThat(message.attributes()).doesNotContainKey("key1");
    }

    @Test
    void ai_message_attributes_should_support_concurrent_modification() throws InterruptedException {

        // given
        AiMessage message = AiMessage.builder().build();
        int threadCount = 10;
        int iterations = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when - multiple threads modifying the same map
        for (int t = 0; t < threadCount; t++) {
            final int threadNum = t;
            new Thread(() -> {
                        for (int i = 0; i < iterations; i++) {
                            message.attributes().put("thread" + threadNum, "value" + i);
                        }
                        latch.countDown();
                    })
                    .start();
        }

        // then - should complete without exception
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(message.attributes()).hasSize(threadCount);
    }

    @Test
    void ai_message_builder_should_copy_mutable_attributes() {

        // given
        AiMessage original = AiMessage.builder().text("original text").build();
        original.attributes().put("key1", "value1");
        original.attributes().put("key2", "value2");

        // when
        AiMessage copy = original.toBuilder().build();

        // then
        assertThat(copy.attributes()).containsEntry("key1", "value1");
        assertThat(copy.attributes()).containsEntry("key2", "value2");

        // Verify it's a copy, not the same reference
        assertThat(copy.attributes()).isNotSameAs(original.attributes());

        // Modifying copy should not affect original
        copy.attributes().put("key3", "value3");
        assertThat(original.attributes()).doesNotContainKey("key3");
        assertThat(copy.attributes()).containsEntry("key3", "value3");
    }

    @Test
    void ai_message_with_text_should_have_empty_mutable_attributes() {

        // when
        AiMessage message = AiMessage.from("Hello");

        // then
        assertThat(message.attributes()).isEmpty();
        assertThat(message.attributes()).isInstanceOf(java.util.concurrent.ConcurrentHashMap.class);

        // Should be able to add attributes
        message.attributes().put("customKey", "customValue");
        assertThat(message.attributes()).containsEntry("customKey", "customValue");
    }

    @Test
    void ai_message_with_tool_requests_should_have_empty_mutable_attributes() {

        // when
        AiMessage message = AiMessage.from(ToolExecutionRequest.builder()
                .id("1")
                .name("testTool")
                .arguments("{}")
                .build());

        // then
        assertThat(message.attributes()).isEmpty();
        assertThat(message.attributes()).isInstanceOf(java.util.concurrent.ConcurrentHashMap.class);

        // Should be able to add attributes
        message.attributes().put("toolAttr", "toolValue");
        assertThat(message.attributes()).containsEntry("toolAttr", "toolValue");
    }

    @Test
    void ai_message_with_text_and_tool_requests_should_have_empty_mutable_attributes() {

        // when
        AiMessage message = AiMessage.from(
                "text",
                List.of(ToolExecutionRequest.builder()
                        .id("1")
                        .name("testTool")
                        .arguments("{}")
                        .build()));

        // then
        assertThat(message.attributes()).isEmpty();
        assertThat(message.attributes()).isInstanceOf(java.util.concurrent.ConcurrentHashMap.class);

        // Should be able to add attributes
        message.attributes().put("combinedAttr", "combinedValue");
        assertThat(message.attributes()).containsEntry("combinedAttr", "combinedValue");
    }

    @Test
    void ai_message_builder_with_existing_attributes_should_copy_them() {

        // given
        Map<String, Object> initialAttrs = Map.of("existingKey", "existingValue");

        // when
        AiMessage message =
                AiMessage.builder().text("test").attributes(initialAttrs).build();

        // then
        assertThat(message.attributes()).containsEntry("existingKey", "existingValue");

        // Verify it's a copy, not the same reference
        assertThat(message.attributes()).isNotSameAs(initialAttrs);

        // Modifying message attributes should not affect the original map
        message.attributes().put("newKey", "newValue");
        assertThat(initialAttrs).doesNotContainKey("newKey");
    }

    @Test
    void chat_model_request_context_current_should_be_thread_local() throws InterruptedException {

        // given
        ChatModelRequestContext ctx = new ChatModelRequestContext(
                ChatRequest.builder()
                        .messages(List.of(UserMessage.from("test")))
                        .build(),
                null,
                Map.of("initial", "value"));

        // when
        ChatModelRequestContext.setCurrent(ctx);
        ChatModelRequestContext retrieved = ChatModelRequestContext.current();

        // then
        assertThat(retrieved).isSameAs(ctx);
        assertThat(retrieved.attributes()).containsEntry("initial", "value");

        // Clean up
        ChatModelRequestContext.setCurrent(null);
        assertThat(ChatModelRequestContext.current()).isNull();
    }

    @Test
    void chat_model_request_context_current_should_be_null_when_not_set() {

        // Ensure clean state
        ChatModelRequestContext.setCurrent(null);

        // then
        assertThat(ChatModelRequestContext.current()).isNull();
    }

    @Test
    void chat_model_request_context_current_should_be_isolated_between_threads() throws InterruptedException {

        // given
        ChatModelRequestContext mainCtx = new ChatModelRequestContext(
                ChatRequest.builder()
                        .messages(List.of(UserMessage.from("main")))
                        .build(),
                null,
                Map.of("thread", "main"));

        ChatModelRequestContext otherCtx = new ChatModelRequestContext(
                ChatRequest.builder()
                        .messages(List.of(UserMessage.from("other")))
                        .build(),
                null,
                Map.of("thread", "other"));

        AtomicReference<String> mainThreadValue = new AtomicReference<>();
        AtomicReference<String> otherThreadValue = new AtomicReference<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        // when
        ChatModelRequestContext.setCurrent(mainCtx);

        // Start other thread
        new Thread(() -> {
                    try {
                        startLatch.await();
                        ChatModelRequestContext.setCurrent(otherCtx);
                        otherThreadValue.set(ChatModelRequestContext.current()
                                .attributes()
                                .get("thread")
                                .toString());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                })
                .start();

        // Main thread continues
        startLatch.countDown();
        mainThreadValue.set(
                ChatModelRequestContext.current().attributes().get("thread").toString());
        doneLatch.countDown();

        // then
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(mainThreadValue.get()).isEqualTo("main");
        assertThat(otherThreadValue.get()).isEqualTo("other");

        // Cleanup
        ChatModelRequestContext.setCurrent(null);
    }

    @Test
    void tool_can_add_attribute_to_ai_message_for_propagation() {

        // Simulating a tool setting context data on AiMessage
        class ToolContext {
            static void simulateToolExecution(AiMessage aiMessage) {
                aiMessage.attributes().put("toolExecutionTime", System.currentTimeMillis());
                aiMessage.attributes().put("toolName", "testTool");
            }
        }

        // given
        AiMessage aiMessage = AiMessage.from("initial response");
        assertThat(aiMessage.attributes()).isEmpty();

        // when - tool executes and adds context
        ToolContext.simulateToolExecution(aiMessage);

        // then - attributes are available on the message
        assertThat(aiMessage.attributes()).containsKey("toolExecutionTime");
        assertThat(aiMessage.attributes()).containsKey("toolName");
        assertThat(aiMessage.attributes().get("toolName")).isEqualTo("testTool");
    }
}
