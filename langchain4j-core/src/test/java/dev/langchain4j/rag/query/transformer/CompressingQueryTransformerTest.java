package dev.langchain4j.rag.query.transformer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class CompressingQueryTransformerTest {

    @Test
    void should_compress_query_and_chat_memory_into_single_query() {

        // given
        List<ChatMessage> chatMemory = asList(
                UserMessage.from("Tell me about Klaus Heisler"),
                AiMessage.from("He is a cool guy")
        );

        UserMessage userMessage = UserMessage.from("How old is he?");
        Metadata metadata = Metadata.from(userMessage, "default", chatMemory);

        Query query = Query.from(userMessage.text(), metadata);

        String expectedResultingQuery = "How old is Klaus Heisler?";

        ChatModelMock model = ChatModelMock.withStaticResponse(expectedResultingQuery);
        CompressingQueryTransformer transformer = new CompressingQueryTransformer(model);

        // when
        Collection<Query> queries = transformer.transform(query);

        // then
        assertThat(queries).containsExactly(Query.from(expectedResultingQuery));

        assertThat(model.userMessageText()).isEqualTo(
                "Read and understand the conversation between the User and the AI. " +
                        "Then, analyze the new query from the User. " +
                        "Identify all relevant details, terms, and context from both the conversation " +
                        "and the new query. Reformulate this query into a clear, concise, " +
                        "and self-contained format suitable for information retrieval.\n" +
                        "\n" +
                        "Conversation:\n" +
                        "User: Tell me about Klaus Heisler\n" +
                        "AI: He is a cool guy\n" +
                        "\n" +
                        "User query: How old is he?\n" +
                        "\n" +
                        "It is very important that you provide only reformulated query and nothing else! " +
                        "Do not prepend a query with anything!"
        );
    }

    @Test
    void should_compress_query_and_chat_memory_into_single_query_using_custom_prompt_template() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from(
                "Given the following conversation: {{chatMemory}} reformulate the following query: {{query}}");

        List<ChatMessage> chatMemory = asList(
                UserMessage.from("Tell me about Klaus Heisler"),
                AiMessage.from("He is a cool guy")
        );
        UserMessage userMessage = UserMessage.from("How old is he?");
        Metadata metadata = Metadata.from(userMessage, "default", chatMemory);
        Query query = Query.from(userMessage.text(), metadata);

        String expectedResultingQuery = "How old is Klaus Heisler?";
        ChatModelMock model = ChatModelMock.withStaticResponse(expectedResultingQuery);

        CompressingQueryTransformer transformer = new CompressingQueryTransformer(model, promptTemplate);

        // when
        Collection<Query> queries = transformer.transform(query);

        // then
        assertThat(queries).containsExactly(Query.from(expectedResultingQuery));

        assertThat(model.userMessageText()).isEqualTo(
                "Given the following conversation: " +
                        "User: Tell me about Klaus Heisler\n" +
                        "AI: He is a cool guy " +
                        "reformulate the following query: How old is he?"
        );
    }

    @Test
    void should_compress_query_and_chat_memory_into_single_query_using_custom_prompt_template_builder() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from(
                "Given the following conversation: {{chatMemory}} reformulate the following query: {{query}}");

        List<ChatMessage> chatMemory = asList(
                UserMessage.from("Tell me about Klaus Heisler"),
                AiMessage.from("He is a cool guy")
        );
        UserMessage userMessage = UserMessage.from("How old is he?");
        Metadata metadata = Metadata.from(userMessage, "default", chatMemory);
        Query query = Query.from(userMessage.text(), metadata);

        String expectedResultingQuery = "How old is Klaus Heisler?";
        ChatModelMock model = ChatModelMock.withStaticResponse(expectedResultingQuery);

        CompressingQueryTransformer transformer = CompressingQueryTransformer.builder()
                .chatLanguageModel(model)
                .promptTemplate(promptTemplate)
                .build();

        // when
        Collection<Query> queries = transformer.transform(query);

        // then
        assertThat(queries).containsExactly(Query.from(expectedResultingQuery));

        assertThat(model.userMessageText()).isEqualTo(
                "Given the following conversation: " +
                        "User: Tell me about Klaus Heisler\n" +
                        "AI: He is a cool guy " +
                        "reformulate the following query: How old is he?"
        );
    }
}