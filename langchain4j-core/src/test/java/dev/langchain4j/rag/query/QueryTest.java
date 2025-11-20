package dev.langchain4j.rag.query;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

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
                .isEqualToIgnoringWhitespace("""
                        Query { text = "query", metadata = Metadata { chatMessage = UserMessage { name = null, contents = [TextContent { text = "user message" }], attributes = {} }, chatMemory = [UserMessage { name = null, contents = [TextContent { text = "Hello" }], attributes = {} }, AiMessage { text = "Hi, how can I help you today?", thinking = null, toolExecutionRequests = [], attributes = {} }], invocationContext = DefaultInvocationContext{invocationId=null, interfaceName='null', methodName='null', methodArguments=[], chatMemoryId=42, invocationParameters=null, managedParameters=null, timestamp=null} } }
                        """);
    }

    @Test
    void should_create_query_with_null_text() {
        // when/then
        assertThatThrownBy(() -> Query.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text cannot be null");
    }

    @Test
    void should_handle_multiline_query_text() {
        // given
        String multilineText = "first line\nsecond line\nthird line";

        // when
        Query query = Query.from(multilineText);

        // then
        assertThat(query.text()).isEqualTo(multilineText);
        assertThat(query.metadata()).isNull();
    }

    @Test
    void should_not_be_equal_to_null() {
        // given
        Query query = Query.from("test");

        // then
        assertThat(query).isNotEqualTo(null);
    }

    @Test
    void should_not_be_equal_to_different_type() {
        // given
        Query query = Query.from("test");
        String notAQuery = "test";

        // then
        assertThat(query).isNotEqualTo(notAQuery);
    }

    @Test
    void should_be_equal_to_itself() {
        // given
        Query query = Query.from("test query");

        // then
        assertThat(query).isEqualTo(query);
        assertThat(query).hasSameHashCodeAs(query);
    }

    @Test
    void should_handle_queries_with_same_text_but_different_metadata() {
        // given
        String sameText = "identical text";
        Metadata metadata1 = Metadata.from(UserMessage.from("message1"), 1, asList());
        Metadata metadata2 = Metadata.from(UserMessage.from("message2"), 2, asList());

        Query query1 = Query.from(sameText, metadata1);
        Query query2 = Query.from(sameText, metadata2);

        // then
        assertThat(query1).isNotEqualTo(query2);
        assertThat(query1).doesNotHaveSameHashCodeAs(query2);
    }

    @Test
    void should_handle_queries_with_different_text_but_same_metadata() {
        // given
        Metadata sameMetadata = Metadata.from(UserMessage.from("message"), 1, asList());

        Query query1 = Query.from("text1", sameMetadata);
        Query query2 = Query.from("text2", sameMetadata);

        // then
        assertThat(query1).isNotEqualTo(query2);
        assertThat(query1).doesNotHaveSameHashCodeAs(query2);
    }

    @Test
    void should_have_consistent_hash_code() {
        // given
        Metadata metadata = Metadata.from(UserMessage.from("message"), 1, asList());
        Query query = Query.from("test", metadata);

        // when
        int hashCode1 = query.hashCode();
        int hashCode2 = query.hashCode();

        // then
        assertThat(hashCode1).isEqualTo(hashCode2);
    }

    @Test
    void toString_should_handle_query_without_metadata() {
        // given
        Query query = Query.from("simple query");

        // when
        String toString = query.toString();

        // then
        assertThat(toString).contains("simple query");
        assertThat(toString).contains("metadata = null");
    }

    @Test
    void toString_should_not_be_null_or_empty() {
        // given
        Query queryWithoutMetadata = Query.from("test");
        Query queryWithMetadata = Query.from("test", Metadata.from(UserMessage.from("msg"), 1, asList()));

        // then
        assertThat(queryWithoutMetadata.toString()).isNotNull().isNotEmpty();
        assertThat(queryWithMetadata.toString()).isNotNull().isNotEmpty();
    }
}
