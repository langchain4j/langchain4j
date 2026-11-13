package dev.langchain4j.jackson3;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Chat memory and {@link InMemoryEmbeddingStore} files outlive the process that wrote them, so an
 * application that switches to Jackson 3 has to keep reading what Jackson 2 wrote. These fixtures
 * were produced by the Jackson 2 codecs and are read here by the Jackson 3 ones.
 *
 * <p>If a change makes one of these unreadable, that is a migration break for every user with
 * persisted data - not a test that needs updating.
 */
class Jackson3WireCompatibilityTest {

    private static final String MESSAGES_WRITTEN_BY_JACKSON_2 =
            """
            [{"text":"You are helpful","type":"SYSTEM"},\
            {"contents":[{"text":"What is the weather in Munich?","type":"TEXT"}],"type":"USER"},\
            {"text":"Let me check","thinking":"The user wants weather",\
            "toolExecutionRequests":[{"id":"call_1","name":"getWeather","arguments":"{\\"city\\":\\"Munich\\"}"}],\
            "attributes":{},"type":"AI"},\
            {"id":"call_1","toolName":"getWeather","contents":[{"text":"18C","type":"TEXT"}],\
            "attributes":{},"type":"TOOL_EXECUTION_RESULT"}]""";

    private static final String STORE_WRITTEN_BY_JACKSON_2 =
            """
            {"entries":[{"id":"fcb486f5-9cf0-43b9-a7dc-f0607ab2b52d","embedding":{"vector":[0.1,0.2,0.3]},\
            "embedded":{"text":"hello","metadata":{"metadata":{}}}}]}""";

    @Test
    void reads_chat_messages_written_by_jackson_2() {
        List<ChatMessage> messages = ChatMessageDeserializer.messagesFromJson(MESSAGES_WRITTEN_BY_JACKSON_2);

        assertThat(messages).hasSize(4);
        assertThat(((SystemMessage) messages.get(0)).text()).isEqualTo("You are helpful");
        assertThat(((UserMessage) messages.get(1)).singleText()).isEqualTo("What is the weather in Munich?");

        AiMessage aiMessage = (AiMessage) messages.get(2);
        assertThat(aiMessage.text()).isEqualTo("Let me check");
        assertThat(aiMessage.thinking()).isEqualTo("The user wants weather");
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        assertThat(aiMessage.toolExecutionRequests().get(0).name()).isEqualTo("getWeather");
        assertThat(aiMessage.toolExecutionRequests().get(0).arguments()).isEqualTo("{\"city\":\"Munich\"}");

        ToolExecutionResultMessage result = (ToolExecutionResultMessage) messages.get(3);
        assertThat(result.id()).isEqualTo("call_1");
        assertThat(result.toolName()).isEqualTo("getWeather");
        assertThat(result.text()).isEqualTo("18C");
    }

    @Test
    void reads_an_embedding_store_written_by_jackson_2() {
        InMemoryEmbeddingStore<TextSegment> store = InMemoryEmbeddingStore.fromJson(STORE_WRITTEN_BY_JACKSON_2);

        var matches = store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(new float[] {0.1f, 0.2f, 0.3f}))
                        .maxResults(1)
                        .build())
                .matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embeddingId()).isEqualTo("fcb486f5-9cf0-43b9-a7dc-f0607ab2b52d");
        assertThat(matches.get(0).embedded().text()).isEqualTo("hello");
        assertThat(matches.get(0).embedding().vector()).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void writes_chat_messages_jackson_2_could_read_back() {
        // Same shape going the other way: what Jackson 3 writes has to stay readable by an
        // application that has not switched yet.
        String written = ChatMessageSerializer.messagesToJson(
                ChatMessageDeserializer.messagesFromJson(MESSAGES_WRITTEN_BY_JACKSON_2));

        assertThat(written).isEqualTo(MESSAGES_WRITTEN_BY_JACKSON_2);
    }

    @Test
    void writes_an_embedding_store_jackson_2_could_read_back() {
        String written = InMemoryEmbeddingStore.fromJson(STORE_WRITTEN_BY_JACKSON_2).serializeToJson();

        assertThat(written).isEqualTo(STORE_WRITTEN_BY_JACKSON_2);
    }
}
