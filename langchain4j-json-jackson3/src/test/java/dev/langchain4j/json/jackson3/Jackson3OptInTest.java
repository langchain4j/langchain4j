package dev.langchain4j.json.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.internal.Json;
import java.util.List;
import org.junit.jupiter.api.Test;

class Jackson3OptInTest {

    // ---------------------------------------------------------------
    // 1. Jackson 2 must be genuinely absent from the runtime classpath
    // ---------------------------------------------------------------

    @Test
    void jackson2_databind_is_not_on_the_classpath() {
        assertThatThrownBy(() -> Class.forName("com.fasterxml.jackson.databind.ObjectMapper"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.fasterxml.jackson.core.JsonParser"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void jackson_annotations_ARE_still_present_and_that_is_correct() throws Exception {
        // jackson-annotations stays on 2.x coordinates and is a required dependency of Jackson 3
        assertThat(Class.forName("com.fasterxml.jackson.annotation.JsonProperty")).isNotNull();
        assertThat(Class.forName("tools.jackson.databind.ObjectMapper")).isNotNull();
    }

    // ---------------------------------------------------------------
    // 2. The SPI must actually pick up the Jackson 3 codec
    // ---------------------------------------------------------------

    @Test
    void core_Json_facade_resolves_to_the_jackson3_codec() {
        // If the SPI were not found, core would fall back to JacksonJsonCodec,
        // which would fail with NoClassDefFoundError since Jackson 2 is excluded.
        String json = Json.toJson(new Pojo("bob", 42));
        assertThat(json).isEqualTo("{\"name\":\"bob\",\"age\":42}");
    }

    @Test
    void core_Json_roundtrip() {
        Pojo original = new Pojo("alice", 7);
        Pojo restored = Json.fromJson(Json.toJson(original), Pojo.class);
        assertThat(restored.name).isEqualTo("alice");
        assertThat(restored.age).isEqualTo(7);
    }

    @SuppressWarnings("unused")
    private List<Pojo> genericTypeHolder;

    @Test
    void core_Json_fromJson_with_generic_Type() throws Exception {
        // java.lang.reflect.Type, obtained without any Jackson type at all
        java.lang.reflect.Type listOfPojo =
                Jackson3OptInTest.class.getDeclaredField("genericTypeHolder").getGenericType();

        List<Pojo> list = Json.fromJson(
                "[{\"name\":\"a\",\"age\":1},{\"name\":\"b\",\"age\":2}]", listOfPojo);
        assertThat(list).hasSize(2);
        assertThat(list.get(1).name).isEqualTo("b");
    }

    // ---------------------------------------------------------------
    // 3. ChatMessage serialization — the chat-memory persistence path
    // ---------------------------------------------------------------

    @Test
    void chat_messages_roundtrip_through_jackson3() {
        List<ChatMessage> messages = List.of(
                SystemMessage.from("you are helpful"),
                UserMessage.from("hello"),
                AiMessage.builder()
                        .text("hi there")
                        .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                .id("call_1")
                                .name("getWeather")
                                .arguments("{\"city\":\"Berlin\"}")
                                .build()))
                        .build(),
                ToolExecutionResultMessage.from("call_1", "getWeather", "sunny"));

        String json = ChatMessageSerializer.messagesToJson(messages);
        List<ChatMessage> restored = ChatMessageDeserializer.messagesFromJson(json);

        assertThat(restored).hasSize(4);
        assertThat(restored.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(restored.get(1)).isInstanceOf(UserMessage.class);
        assertThat(restored.get(2)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) restored.get(2)).toolExecutionRequests())
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.id()).isEqualTo("call_1");
                    assertThat(r.name()).isEqualTo("getWeather");
                });
        assertThat(restored.get(3)).isInstanceOf(ToolExecutionResultMessage.class);
    }

    /**
     * Chat memory written by a Jackson 2 build must still be readable by the Jackson 3 codec.
     * This payload was produced by the Jackson 2 codec on the same LangChain4j version.
     */
    @Test
    void reads_chat_memory_written_by_the_jackson2_codec() {
        String jackson2Payload =
                "[{\"text\":\"you are helpful\",\"type\":\"SYSTEM\"},"
                        + "{\"contents\":[{\"text\":\"hello\",\"type\":\"TEXT\"}],\"type\":\"USER\"},"
                        + "{\"text\":\"hi there\",\"toolExecutionRequests\":[],\"attributes\":{},\"type\":\"AI\"}]";

        List<ChatMessage> restored = ChatMessageDeserializer.messagesFromJson(jackson2Payload);

        assertThat(restored).hasSize(3);
        assertThat(((SystemMessage) restored.get(0)).text()).isEqualTo("you are helpful");
        assertThat(((UserMessage) restored.get(1)).singleText()).isEqualTo("hello");
        assertThat(((AiMessage) restored.get(2)).text()).isEqualTo("hi there");
    }

    @Test
    void tool_specification_codec_resolves_to_jackson3() {
        String json = dev.langchain4j.internal.ToolSpecificationJsonUtils.toJson(
                dev.langchain4j.agent.tool.ToolSpecification.builder()
                        .name("getWeather")
                        .description("returns the weather")
                        .build());

        assertThat(json).contains("getWeather").contains("returns the weather");
    }

    @dev.langchain4j.model.input.structured.StructuredPrompt("Hello {{name}}, you are {{age}}")
    static class Greeting {
        String name;
        Integer age;
    }

    @Test
    void structured_prompt_factory_resolves_to_jackson3() {
        Greeting greeting = new Greeting();
        greeting.name = "bob";
        greeting.age = 42;

        assertThat(dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt(greeting)
                        .text())
                .isEqualTo("Hello bob, you are 42");
    }

    @Test
    void in_memory_embedding_store_round_trips_through_jackson3() {
        dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<dev.langchain4j.data.segment.TextSegment>
                store = new dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<>();
        store.add(
                dev.langchain4j.data.embedding.Embedding.from(new float[] {0.1f, 0.2f}),
                dev.langchain4j.data.segment.TextSegment.from("hello"));

        String json = store.serializeToJson();
        var restored = dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore.fromJson(json);

        var matches = restored.search(dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                        .queryEmbedding(dev.langchain4j.data.embedding.Embedding.from(new float[] {0.1f, 0.2f}))
                        .maxResults(1)
                        .build())
                .matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embedded().text()).isEqualTo("hello");
    }

    static class Pojo {
        String name;
        int age;

        Pojo() {}

        Pojo(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }
}
