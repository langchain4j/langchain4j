package dev.langchain4j.rag.query.transformer;

import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class ExpandingQueryTransformerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "query 1\nquery 2\nquery 3",
            "query 1\n\nquery 2\n\nquery 3"
    })
    void should_expand_query(String queriesString) {

        // given
        Query query = Query.from("query");

        ChatModelMock model = ChatModelMock.withStaticResponse(queriesString);

        QueryTransformer transformer = new ExpandingQueryTransformer(model);

        // when
        Collection<Query> queries = transformer.transform(query);

        // then
        assertThat(queries).containsExactly(
                Query.from("query 1"),
                Query.from("query 2"),
                Query.from("query 3")
        );
        assertThat(model.userMessageText()).isEqualTo(
                "Generate 3 different versions of a provided user query. " +
                        "Each version should be worded differently, using synonyms or alternative sentence structures, " +
                        "but they should all retain the original meaning. " +
                        "These versions will be used to retrieve relevant documents. " +
                        "It is very important to provide each query version on a separate line, " +
                        "without enumerations, hyphens, or any additional formatting!\n" +
                        "User query: query"
        );
    }

    @Test
    void should_expand_query_with_custom_N() {

        // given
        int n = 5;

        ChatModelMock model = ChatModelMock.withStaticResponse("does not matter");

        QueryTransformer transformer = new ExpandingQueryTransformer(model, n);

        Query query = Query.from("query");

        // when
        transformer.transform(query);

        // then
        assertThat(model.userMessageText()).contains("Generate 5 different versions");
    }

    @Test
    void should_expand_query_with_custom_prompt_template() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("Generate 7 variations of {{query}}");

        ChatModelMock model = ChatModelMock.withStaticResponse("does not matter");

        QueryTransformer transformer = new ExpandingQueryTransformer(model, promptTemplate);

        Query query = Query.from("query");

        // when
        transformer.transform(query);

        // then
        assertThat(model.userMessageText()).isEqualTo("Generate 7 variations of query");
    }

    @Test
    void should_expand_query_with_custom_prompt_template_and_n_builder() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("Generate {{n}} variations of {{query}}");

        ChatModelMock model = ChatModelMock.withStaticResponse("does not matter");

        QueryTransformer transformer = ExpandingQueryTransformer.builder()
                .chatLanguageModel(model)
                .promptTemplate(promptTemplate)
                .n(7)
                .build();

        Query query = Query.from("query");

        // when
        transformer.transform(query);

        // then
        assertThat(model.userMessageText()).isEqualTo("Generate 7 variations of query");
    }
}