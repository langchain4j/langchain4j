package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.graph.neo4j.Neo4jGraph;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Type;
import org.neo4j.driver.types.TypeSystem;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A {@link ContentRetriever} that retrieves from an {@link Neo4jGraph}.
 * It converts a natural language question into a Neo4j cypher query and then retrieves relevant {@link Content}s by executing the query on Neo4j.
 */
public class Neo4jContentRetriever implements ContentRetriever {

    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from("""
            Based on the Neo4j graph schema below, write a Cypher query that would answer the user's question:
            {{schema}}
            
            Question: {{question}}
            Cypher query:
            """);

    private static final Pattern BACKTICKS_PATTERN = Pattern.compile("```(.*?)```", Pattern.MULTILINE | Pattern.DOTALL);
    private static final Type NODE = TypeSystem.getDefault().NODE();

    private final Neo4jGraph graph;

    private final ChatLanguageModel chatLanguageModel;

    private final PromptTemplate promptTemplate;

    public Neo4jContentRetriever(Neo4jGraph graph, ChatLanguageModel chatLanguageModel, PromptTemplate promptTemplate) {
        this.graph = graph;
        this.chatLanguageModel = chatLanguageModel;
        this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
    }

    public static Neo4jContentRetrieverBuilder builder() {
        return new Neo4jContentRetrieverBuilder();
    }

    @Override
    public List<Content> retrieve(Query query) {

        String question = query.text();
        String schema = graph.getSchema();
        String cypherQuery = generateCypherQuery(schema, question);
        if (cypherQuery == null || cypherQuery.trim().isEmpty()) {
            throw new IllegalArgumentException("Generated Cypher query is empty.");
        }
        List<String> response = executeQuery(cypherQuery);
        return response.stream().map(Content::from).collect(Collectors.toList());
    }

    private String generateCypherQuery(String schema, String question) {

        Prompt cypherPrompt = promptTemplate.apply(Map.of("schema", schema, "question", question));
        String cypherQuery = chatLanguageModel.chat(cypherPrompt.text());
        Matcher matcher = BACKTICKS_PATTERN.matcher(cypherQuery);
        return matcher.find() ? matcher.group(1) : cypherQuery;  // Directly return cypherQuery without redundant processing
    }

    private List<String> executeQuery(String cypherQuery) {
        return graph.executeRead(cypherQuery).stream()
                .flatMap(record -> record.values().stream())
                .map(value -> NODE.isTypeOf(value) ? value.asMap().toString() : value.toString())
                .collect(Collectors.toList());  // Using collect() for better performance in large datasets
    }

    public static class Neo4jContentRetrieverBuilder {
        private Neo4jGraph graph;
        private ChatLanguageModel chatLanguageModel;
        private PromptTemplate promptTemplate;

        Neo4jContentRetrieverBuilder() {
        }

        public Neo4jContentRetrieverBuilder graph(Neo4jGraph graph) {
            this.graph = graph;
            return this;
        }

        public Neo4jContentRetrieverBuilder chatLanguageModel(ChatLanguageModel chatLanguageModel) {
            this.chatLanguageModel = chatLanguageModel;
            return this;
        }

        public Neo4jContentRetrieverBuilder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public Neo4jContentRetriever build() {
            return new Neo4jContentRetriever(this.graph, this.chatLanguageModel, this.promptTemplate);
        }

        public String toString() {
            return "Neo4jContentRetriever.Neo4jContentRetrieverBuilder(graph=" + this.graph + ", chatLanguageModel=" + this.chatLanguageModel + ", promptTemplate=" + this.promptTemplate + ")";
        }
    }
}
