package dev.langchain4j.rag.query;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

class QueryTest {

    @Test
    void create() {

        // given
        String queryText = "query";

        // when
        Query query = Query.from(queryText);

        // then
        assertThat(query.text()).isEqualTo(queryText);
        assertThat(query.metadata()).isNull();
    }

    @Test
    void create_with_metadata() {

        // given
        String queryText = "query";

        Metadata metadata = Metadata.from(
                UserMessage.from("user message"),
                42,
                asList(UserMessage.from("Hello"), AiMessage.from("Hi, how can I help you today?")));

        // when
        Query query = Query.from(queryText, metadata);

        // then
        assertThat(query.text()).isEqualTo(queryText);
        assertThat(query.metadata()).isSameAs(metadata);
    }

    @Test
    void equals_hash_code() {

        // given
        Metadata metadata1 = Metadata.from(
                UserMessage.from("user message"),
                42,
                asList(UserMessage.from("Hello"), AiMessage.from("Hi, how can I help you today?")));
        Query query1 = Query.from("query", metadata1);

        Metadata metadata2 = Metadata.from(
                UserMessage.from("another user message"),
                666,
                asList(UserMessage.from("Bye"), AiMessage.from("Bye-bye")));
        Query query2 = Query.from("query 2", metadata2);

        Metadata metadata3 = Metadata.from(
                UserMessage.from("user message"),
                42,
                asList(UserMessage.from("Hello"), AiMessage.from("Hi, how can I help you today?")));
        Query query3 = Query.from("query", metadata3);

        // then
        assertThat(query1).isNotEqualTo(query2).doesNotHaveSameHashCodeAs(query2);

        assertThat(query1).isEqualTo(query3).hasSameHashCodeAs(query3);
    }

    @Test
    void to_string() {

        // given
        Metadata metadata = Metadata.from(
                UserMessage.from("user message"),
                42,
                asList(UserMessage.from("Hello"), AiMessage.from("Hi, how can I help you today?")));
        Query query = Query.from("query", metadata);

        // when
        String toString = query.toString();

        // then
        assertThat(toString)
                .isEqualTo("Query { " + "text = \"query\", "
                        + "metadata = Metadata { "
                        + "chatMessage = UserMessage { name = null contents = [TextContent { text = \"user message\" }] }, "
                        + "chatMemoryId = 42, "
                        + "chatMemory = [UserMessage { name = null contents = [TextContent { text = \"Hello\" }] }, AiMessage { text = \"Hi, how can I help you today?\" toolExecutionRequests = [] }] "
                        + "} "
                        + "}");
    }
}
