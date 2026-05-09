package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.AiServices;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Validates {@link AiServices#toolProviderRequestFactory(java.util.function.Function)}: the
 * factory is consulted when constructing the {@link ToolProviderRequest} passed to a
 * {@link ToolProvider}, and a custom subclass returned by the factory is the exact instance
 * the provider receives.
 *
 * <p>Downstream framework integrators (e.g. Quarkus) use this hook to attach extra context
 * (such as MCP client filtering hints) to the provider request without modifying upstream.
 */
class ToolProviderRequestFactoryTest {

    interface Assistant {
        String chat(String message);
    }

    /** A custom request subclass carrying an extra field. */
    static class CustomToolProviderRequest extends ToolProviderRequest {
        final String tag;

        CustomToolProviderRequest(Builder builder, String tag) {
            super(builder);
            this.tag = tag;
        }
    }

    @Test
    void factory_is_invoked_and_subclass_reaches_provider() {
        AtomicReference<ToolProviderRequest> seenByProvider = new AtomicReference<>();

        ToolProvider provider = request -> {
            seenByProvider.set(request);
            // Return a single tool. We don't actually need to execute it; we just need the
            // provider to be invoked once.
            return ToolProviderResult.builder()
                    .add(
                            ToolSpecification.builder().name("noop").description("noop").build(),
                            (ToolExecutor) (ToolExecutionRequest req, Object memId) -> "ok")
                    .build();
        };

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(AiMessage.from("done"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .toolProvider(provider)
                .toolProviderRequestFactory(builder -> new CustomToolProviderRequest(builder, "tag-42"))
                .build();

        assistant.chat("hello");

        assertThat(seenByProvider.get()).isInstanceOf(CustomToolProviderRequest.class);
        assertThat(((CustomToolProviderRequest) seenByProvider.get()).tag).isEqualTo("tag-42");
    }

    @Test
    void without_factory_provider_receives_a_plain_request() {
        AtomicReference<ToolProviderRequest> seenByProvider = new AtomicReference<>();

        ToolProvider provider = request -> {
            seenByProvider.set(request);
            return ToolProviderResult.builder()
                    .add(
                            ToolSpecification.builder().name("noop").description("noop").build(),
                            (ToolExecutor) (ToolExecutionRequest req, Object memId) -> "ok")
                    .build();
        };

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(AiMessage.from("done"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .toolProvider(provider)
                .build();

        assistant.chat("hello");

        // No factory configured: the request is a plain ToolProviderRequest, not a subclass.
        assertThat(seenByProvider.get()).isInstanceOf(ToolProviderRequest.class);
        assertThat(seenByProvider.get()).isNotInstanceOf(CustomToolProviderRequest.class);
    }
}
