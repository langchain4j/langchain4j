package dev.langchain4j.rag.content.retriever.neo4j;

import static dev.langchain4j.Neo4jUtils.getBacktickText;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.graph.neo4j.Neo4jGraph;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Type;
import org.neo4j.driver.types.TypeSystem;

/**
 * A {@link ContentRetriever} that retrieves from an {@link Neo4jGraph}.
 * It converts a natural language question into a Neo4j cypher query and then retrieves relevant {@link Content}s by executing the query on Neo4j.
 */
public class Neo4jContentRetriever implements ContentRetriever {

    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from(
            """
            Based on the Neo4j graph schema below, write a Cypher query that would answer the user's question:
            {{schema}}

            Question: {{question}}
            Cypher query:
            """);

    private static final Type NODE = TypeSystem.getDefault().NODE();

    private final Neo4jGraph graph;

    private final ChatLanguageModel chatLanguageModel;

    private final PromptTemplate promptTemplate;

    @Builder
    public Neo4jContentRetriever(Neo4jGraph graph, ChatLanguageModel chatLanguageModel, PromptTemplate promptTemplate) {

        this.graph = ensureNotNull(graph, "graph");
        this.chatLanguageModel = ensureNotNull(chatLanguageModel, "chatLanguageModel");
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
    }

    @Override
    public List<Content> retrieve(Query query) {

        String question = query.text();
        String schema = graph.getSchema();
        String cypherQuery = generateCypherQuery(schema, question);
        List<String> response = executeQuery(cypherQuery);
        return response.stream().map(Content::from).toList();
    }

    private String generateCypherQuery(String schema, String question) {

        Prompt cypherPrompt = promptTemplate.apply(Map.of("schema", schema, "question", question));
        String cypherQuery = chatLanguageModel.generate(cypherPrompt.text());
        return getBacktickText(cypherQuery);
    }

    private List<String> executeQuery(String cypherQuery) {

        List<Record> records = graph.executeRead(cypherQuery);
        return records.stream()
                .flatMap(r -> r.values().stream())
                .map(value -> NODE.isTypeOf(value) ? value.asMap().toString() : value.toString())
                .toList();
    }
}
