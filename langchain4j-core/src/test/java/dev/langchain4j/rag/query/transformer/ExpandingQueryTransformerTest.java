package dev.langchain4j.rag.query.transformer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class ExpandingQueryTransformerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            """
                    {"queries":["query 1","query 2","query 3"]}"""
    })
    void should_expand_query(String queriesString) {

        // given
        List<ChatMessage> chatMemory = asList(
                UserMessage.from("Hi"),
                AiMessage.from("Hello")
        );
        UserMessage userMessage = UserMessage.from("query");
        Metadata metadata = Metadata.from(userMessage, "default", chatMemory);

        Query query = Query.from(userMessage.singleText(), metadata);

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(queriesString);

        QueryTransformer transformer = new ExpandingQueryTransformer(model);

        // when
        Collection<Query> queries = transformer.transform(query);

        // then
        assertThat(queries).containsExactly(
                Query.from("query 1", metadata),
                Query.from("query 2", metadata),
                Query.from("query 3", metadata)
        );

        assertThat(model.userMessageText()).isEqualTo(
                """
                    Generate EXACTLY 3 different versions of the provided user query. \
                    CONSTRAINTS: \
                    Each version should be worded differently, using synonyms or alternative sentence structures, \
                    but they should all retain the original meaning. \
                    INPUT: \
                    User query: query"""
                );
    }

    @Test
    void should_expand_query_with_custom_N() {

        // given
        int n = 5;

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("""
                {"queries": ["query 1", "query 2", "query 3", "query 4", "query 5"]}""");

        QueryTransformer transformer = new ExpandingQueryTransformer(model, n);

        Query query = Query.from("query");

        // when
        transformer.transform(query);

        // then
        assertThat(model.userMessageText()).contains("Generate EXACTLY 5 different versions");
    }

    @Test
    void should_expand_query_with_custom_prompt_template() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("Generate 2 variations of {{query}}");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("""
                {"queries": ["query 1", "query 2", "query 3"]}""");

        QueryTransformer transformer = new ExpandingQueryTransformer(model, promptTemplate);

        Query query = Query.from("query");

        // when
        transformer.transform(query);

        // then
        assertThat(model.userMessageText()).isEqualTo("Generate 2 variations of query");
    }

    @Test
    void should_expand_query_with_custom_prompt_template_and_n_builder() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("Generate {{n}} variations of {{query}}");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("""
                {"queries": ["query 1", "query 2"]}""");

        QueryTransformer transformer = ExpandingQueryTransformer.builder()
                .chatModel(model)
                .promptTemplate(promptTemplate)
                .n(2)
                .build();

        Query query = Query.from("query");

        // when
        transformer.transform(query);

        // then
        assertThat(model.userMessageText()).isEqualTo("Generate 2 variations of query");
    }

    @ParameterizedTest
    @MethodSource
    void should_parse_response_with_extra_text(String queriesString) {

        UserMessage userMessage = UserMessage.from("query");

        Query query = Query.from(userMessage.singleText());

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(queriesString);

        QueryTransformer transformer = new ExpandingQueryTransformer(model);

        // when
        Collection<Query> queries = transformer.transform(query);

        // then
        assertThat(queries).containsExactly(
                Query.from("query 1"),
                Query.from("query 2"),
                Query.from("query 3")
        );
    }

    static Stream<String> should_parse_response_with_extra_text() {
        return Stream.of(
                """
                        Sure, Here are the different versions of the query:
                        {
                          "queries": ["query 1", "query 2", "query 3"]
                        }
                        """,
                """
                        ```json
                        {
                          "queries": ["query 1", "query 2", "query 3"]
                        }
                        ```
                        """,
                """
                        ```json
                        {
                          "queries": ["query 1", "query 2", "query 3"]
                        }
                        ```
                        Let me know if you need help with anything.
                        """
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            """
                    {"queries" : ["query 1", "query 2", "query 3"]}"""
    })
    void should_limit_expanded_queries_to_n(String queriesString) {

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(queriesString);

        QueryTransformer transformer = new ExpandingQueryTransformer(model, 2);

        Query query = Query.from("query");

        // when
        Collection<Query> queries = transformer.transform(query);

        // then
        assertThat(queries).containsExactly(
                Query.from("query 1"),
                Query.from("query 2")
        );
    }

    @ParameterizedTest
    @MethodSource
    void should_fail_to_expand_query_and_fallback_to_original_query(String queriesString) {
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(queriesString);

        QueryTransformer transformer = new ExpandingQueryTransformer(model);

        Query query = Query.from("query");

        // when
        Collection<Query> queries = transformer.transform(query);

        // then
        assertThat(queries).containsExactly(
                Query.from("query")
        );
    }

    static Stream<Arguments> should_fail_to_expand_query_and_fallback_to_original_query() {
        return Stream.<Arguments>builder()
                .add(Arguments.of("""
                        {"queries" : "query 1, query 2, query 3"}
                        """))
                .add(Arguments.of("""
                        Here are different versions of the query:
                        queries: \
                         query 1
                         query 2
                         query 3
                        """))
                .build();
    }
}
