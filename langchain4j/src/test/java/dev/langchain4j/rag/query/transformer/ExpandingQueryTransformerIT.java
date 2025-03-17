package dev.langchain4j.rag.query.transformer;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ExpandingQueryTransformerIT {

    @ParameterizedTest
    @MethodSource
    void should_expand_query(ChatLanguageModel model) {

        // given
        Query query = Query.from("Tell me about dogs");

        QueryTransformer transformer = new ExpandingQueryTransformer(model);

        // when
        Collection<Query> queries = transformer.transform(query);

        // then
        assertThat(queries).hasSize(3);

        queries.forEach(q -> assertThat(q.text())
                .doesNotStartWith("1")
                .doesNotStartWith("-")
        );
    }

    static Stream<Arguments> should_expand_query() {
        return Stream.of(
                Arguments.of(
                        OpenAiChatModel.builder()
                                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                                .apiKey(System.getenv("OPENAI_API_KEY"))
                                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                                .logRequests(true)
                                .logResponses(true)
                                .build()
                )
                // TODO add more models
        );
    }
}