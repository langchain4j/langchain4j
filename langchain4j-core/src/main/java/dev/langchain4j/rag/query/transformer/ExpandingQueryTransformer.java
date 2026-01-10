package dev.langchain4j.rag.query.transformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * A {@link QueryTransformer} that utilizes a {@link ChatModel} to expand a given {@link Query}.
 * <br>
 * Refer to {@link #DEFAULT_PROMPT_TEMPLATE} and implementation for more details.
 * <br>
 * <br>
 * Configurable parameters (optional):
 * <br>
 * - {@link #promptTemplate}: The prompt template used to instruct the LLM to expand the provided {@link Query}.
 * <br>
 * - {@link #n}: The number of {@link Query}s to generate. Default value is 3.
 *
 * @see DefaultQueryTransformer
 * @see CompressingQueryTransformer
 */
public class ExpandingQueryTransformer implements QueryTransformer {
    private static final Logger log = LoggerFactory.getLogger(ExpandingQueryTransformer.class);
    public static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from(
            """
                    Generate EXACTLY {{n}} different versions of the provided user query. \
                    CONSTRAINTS: \
                    Each version should be worded differently, using synonyms or alternative sentence structures, \
                    but they should all retain the original meaning. \
                    INPUT: \
                    User query: {{query}}"""
    );

    public static final int DEFAULT_N = 3;

    protected final ChatModel chatModel;
    protected final PromptTemplate promptTemplate;
    protected final int n;

    public ExpandingQueryTransformer(ChatModel chatModel) {
        this(chatModel, DEFAULT_PROMPT_TEMPLATE, DEFAULT_N);
    }

    public ExpandingQueryTransformer(ChatModel chatModel, int n) {
        this(chatModel, DEFAULT_PROMPT_TEMPLATE, n);
    }

    public ExpandingQueryTransformer(ChatModel chatModel, PromptTemplate promptTemplate) {
        this(chatModel, ensureNotNull(promptTemplate, "promptTemplate"), DEFAULT_N);
    }

    public ExpandingQueryTransformer(ChatModel chatModel, PromptTemplate promptTemplate, Integer n) {
        this.chatModel = ensureNotNull(chatModel, "chatModel");
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
        this.n = ensureGreaterThanZero(getOrDefault(n, DEFAULT_N), "n");
    }

    public static ExpandingQueryTransformerBuilder builder() {
        return new ExpandingQueryTransformerBuilder();
    }

    @Override
    public Collection<Query> transform(Query query) {
        Prompt prompt = createPrompt(query);
        ResponseFormat responseFormat = getResponseFormat();
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(prompt.toUserMessage())
                .responseFormat(responseFormat)
                .build();
        ChatResponse response = chatModel.chat(chatRequest);

        try {
            return parse(response.aiMessage().text(), query.metadata());
        } catch (Exception e) {
            log.error("Failed to expand the query to {} different versions. Returning the original query.", n, e);
            return List.of(query);
        }
    }

    protected Prompt createPrompt(Query query) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("query", query.text());
        variables.put("n", n);
        return promptTemplate.apply(variables);
    }

    protected Collection<Query> parse(String response, Metadata metadata)  {
        JsonNode root = extractJsonNode(response);
        List<String> queries = new ArrayList<>();
        JsonNode queriesNode = root.get("queries");

        if (queriesNode == null || !queriesNode.isArray()) {
            throw new IllegalArgumentException("value of queries is empty or not a valid array.");
        }

        for (JsonNode queryNode : queriesNode) {
            queries.add(queryNode.asText().trim());
        }

        return queries.stream()
                .filter(queryText -> !queryText.isEmpty())
                .distinct()
                .limit(n)
                .map(queryText -> metadata == null
                        ? Query.from(queryText)
                        : Query.from(queryText, metadata))
                .toList();
    }

    protected JsonNode extractJsonNode(String response) {
        int jsonStartIndex = response.indexOf("{");
        int jsonEndIndex = response.lastIndexOf("}");

        if (jsonStartIndex == -1 || jsonEndIndex == -1 || jsonStartIndex >= jsonEndIndex) {
            throw new IllegalArgumentException("Response doesn't contain a valid JSON.");
        }
        String JsonResponse = response.substring(jsonStartIndex, jsonEndIndex + 1);
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readTree(JsonResponse);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    protected ResponseFormat getResponseFormat() {
        JsonArraySchema queriesString = JsonArraySchema.builder()
                                                        .items(JsonStringSchema
                                                                .builder()
                                                                .build())
                                                        .build();
        JsonObjectSchema jsonObjectSchema = JsonObjectSchema.builder()
                                                            .addProperty("queries", queriesString)
                                                            .build();
        JsonSchema schema = JsonSchema.builder()
                                        .rootElement(jsonObjectSchema)
                                        .build();

        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(schema)
                .build();
    }

    public static class ExpandingQueryTransformerBuilder {
        private ChatModel chatModel;
        private PromptTemplate promptTemplate;
        private Integer n;

        ExpandingQueryTransformerBuilder() {
        }

        public ExpandingQueryTransformerBuilder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public ExpandingQueryTransformerBuilder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public ExpandingQueryTransformerBuilder n(Integer n) {
            this.n = n;
            return this;
        }

        public ExpandingQueryTransformer build() {
            return new ExpandingQueryTransformer(this.chatModel, this.promptTemplate, this.n);
        }
    }
}
