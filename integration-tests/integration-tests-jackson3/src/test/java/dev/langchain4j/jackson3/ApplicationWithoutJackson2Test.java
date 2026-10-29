package dev.langchain4j.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Jackson 3 opt-in as an application actually assembles it: {@code langchain4j-core}, the main
 * module, a provider, and {@code langchain4j-jackson3} - with Jackson 2 excluded from the
 * whole dependency graph by this module's pom.
 *
 * <p>The codec module's own tests show the codec is correct. These show that an application built
 * this way runs, which is a different claim: it fails if any code LangChain4j reaches on these
 * paths still touches a Jackson 2 class, since there is none to load.
 */
class ApplicationWithoutJackson2Test {

    // ---------------------------------------------------------------
    // The premise
    // ---------------------------------------------------------------

    @Test
    void jackson2_is_absent() {
        assertThatThrownBy(() -> Class.forName("com.fasterxml.jackson.databind.ObjectMapper"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.fasterxml.jackson.core.JsonParser"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void jackson3_and_the_shared_annotations_are_present() throws Exception {
        assertThat(Class.forName("tools.jackson.databind.ObjectMapper")).isNotNull();
        // jackson-annotations keeps the 2.x coordinates and is a dependency of Jackson 3 itself
        assertThat(Class.forName("com.fasterxml.jackson.annotation.JsonProperty")).isNotNull();
    }

    // ---------------------------------------------------------------
    // AI Services - the path the codec module's tests do not reach
    // ---------------------------------------------------------------

    record Person(String name, int age) {}

    interface Extractor {
        Person extract(String text);
    }

    @Test
    void an_ai_service_parses_structured_output() {
        Extractor extractor = AiServices.create(Extractor.class, replying("{\"name\":\"Ada\",\"age\":36}"));

        Person person = extractor.extract("Ada is 36");

        assertThat(person.name()).isEqualTo("Ada");
        assertThat(person.age()).isEqualTo(36);
    }

    static class Weather {

        String askedFor;

        @Tool("Returns the weather in a city")
        String weather(@P(name = "city", description = "the city") String city) {
            askedFor = city;
            return "sunny";
        }
    }

    interface Assistant {
        String chat(String message);
    }

    @Test
    void an_ai_service_builds_a_tool_schema_and_reads_tool_arguments() {
        Weather weather = new Weather();
        ChatModel model = replying(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("call_1")
                        .name("weather")
                        .arguments("{\"city\":\"Berlin\"}")
                        .build()),
                AiMessage.from("It is sunny in Berlin."));

        Assistant assistant =
                AiServices.builder(Assistant.class).chatModel(model).tools(weather).build();

        assertThat(assistant.chat("What is the weather in Berlin?")).isEqualTo("It is sunny in Berlin.");
        assertThat(weather.askedFor).isEqualTo("Berlin");
    }

    // ---------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------

    @Test
    void chat_memory_round_trips() {
        List<ChatMessage> messages =
                List.of(SystemMessage.from("be brief"), UserMessage.from("hi"), AiMessage.from("hello"));

        List<ChatMessage> restored =
                ChatMessageDeserializer.messagesFromJson(ChatMessageSerializer.messagesToJson(messages));

        assertThat(restored).hasSize(3);
        assertThat(((AiMessage) restored.get(2)).text()).isEqualTo("hello");
    }

    @Test
    void in_memory_embedding_store_round_trips() {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        store.add(Embedding.from(new float[] {0.1f, 0.2f}), TextSegment.from("hello"));

        InMemoryEmbeddingStore<TextSegment> restored = InMemoryEmbeddingStore.fromJson(store.serializeToJson());

        assertThat(restored
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(Embedding.from(new float[] {0.1f, 0.2f}))
                                .maxResults(1)
                                .build())
                        .matches())
                .singleElement()
                .satisfies(match -> assertThat(match.embedded().text()).isEqualTo("hello"));
    }

    // ---------------------------------------------------------------

    private static ChatModel replying(String text) {
        return replying(AiMessage.from(text));
    }

    private static ChatModel replying(AiMessage... replies) {
        Deque<AiMessage> remaining = new ArrayDeque<>(List.of(replies));
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return ChatResponse.builder()
                        .aiMessage(remaining.size() > 1 ? remaining.poll() : remaining.peek())
                        .build();
            }
        };
    }
}
