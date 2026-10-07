package dev.langchain4j.rag.query.transformer;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class StepBackQueryTransformerTest {

    @Test
    void should_generate_step_back_query_using_chat_memory() {

        // given
        SystemMessage systemMessage = SystemMessage.from("Be polite");

        List<ChatMessage> chatMemory = asList(
                systemMessage, // ignored
                UserMessage.from("Tell me about CNNs"),
                AiMessage.from("CNNs are neural networks used for image processing"));

        UserMessage userMessage = UserMessage.from("How does backpropagation work in them?");
        Metadata metadata = Metadata.from(userMessage, systemMessage, "default", chatMemory);

        Query query = Query.from(userMessage.singleText(), metadata);

        String stepBackQuery = "What is backpropagation in neural networks?";

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(stepBackQuery);

        StepBackQueryTransformer transformer = new StepBackQueryTransformer(model);

        // when
        Collection<Query> queries = transformer.transform(query);

        // then
        assertThat(queries).containsExactly(query, Query.from(stepBackQuery, metadata));

        assertThat(model.userMessageText())
                .contains("Conversation:")
                .contains("User: Tell me about CNNs")
                .contains("AI: CNNs are neural networks used for image processing")
                .contains("User query:")
                .contains("How does backpropagation work in them?");
    }

    @Test
    void should_generate_step_back_query_without_chat_memory() {

        // given
        List<ChatMessage> chatMemory = emptyList();

        SystemMessage systemMessage = SystemMessage.from("Be polite");
        UserMessage userMessage = UserMessage.from("How does backpropagation work in CNNs?");
        Metadata metadata = Metadata.from(userMessage, systemMessage, "default", chatMemory);

        Query query = Query.from(userMessage.singleText(), metadata);

        String stepBackQuery = "What is backpropagation in neural networks?";

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(stepBackQuery);

        StepBackQueryTransformer transformer = new StepBackQueryTransformer(model);

        // when
        Collection<Query> queries = transformer.transform(query);

        // then
        assertThat(queries).containsExactly(query, Query.from(stepBackQuery, metadata));
    }

    @Test
    void should_generate_step_back_query_using_custom_prompt_template() {

        // given
        PromptTemplate promptTemplate =
                PromptTemplate.from("Given the conversation {{chatMemory}} generate a broader question for {{query}}");

        List<ChatMessage> chatMemory = asList(
                UserMessage.from("Tell me about CNNs"), AiMessage.from("CNNs are neural networks used for images"));

        SystemMessage systemMessage = SystemMessage.from("Be polite");
        UserMessage userMessage = UserMessage.from("How does backpropagation work?");
        Metadata metadata = Metadata.from(userMessage, systemMessage, "default", chatMemory);

        Query query = Query.from(userMessage.singleText(), metadata);

        String stepBackQuery = "What is backpropagation in neural networks?";

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(stepBackQuery);

        StepBackQueryTransformer transformer = new StepBackQueryTransformer(model, promptTemplate);

        // when
        Collection<Query> queries = transformer.transform(query);

        // then
        assertThat(queries).containsExactly(query, Query.from(stepBackQuery, metadata));

        assertThat(model.userMessageText())
                .isEqualTo(
                        """
        Given the conversation \
        User: Tell me about CNNs
        AI: CNNs are neural networks used for images
         generate a broader question for How does backpropagation work?""");
    }

    @Test
    void should_return_original_query_and_step_back_query() {

        // given
        SystemMessage systemMessage = SystemMessage.from("Be polite");

        List<ChatMessage> chatMemory = asList(
                UserMessage.from("Tell me about transformers"),
                AiMessage.from("Transformers are deep learning models"));

        UserMessage userMessage = UserMessage.from("How do they work?");
        Metadata metadata = Metadata.from(userMessage, systemMessage, "default", chatMemory);

        Query query = Query.from(userMessage.singleText(), metadata);

        String stepBackQuery = "What are transformer models in machine learning?";

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(stepBackQuery);

        StepBackQueryTransformer transformer = new StepBackQueryTransformer(model);

        // when
        Collection<Query> queries = transformer.transform(query);

        // then
        assertThat(queries).hasSize(2);

        assertThat(queries).containsExactly(query, Query.from(stepBackQuery, metadata));
    }
}
