package dev.langchain4j.model.cohere;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class CohereClientIT {

    private static final String DEFAULT_BASE_URL = "https://api.cohere.ai/v1/";

    private static CohereClient client;

    @BeforeAll
    static void setup() {
        client = CohereClient.builder()
                .baseUrl(DEFAULT_BASE_URL)
                .apiKey(System.getenv("COHERE_API_KEY"))
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Test
    void should_use_documents() {

        // GIVEN
        List<Map<String, String>> documents = new ArrayList<>();

        // First Document
        HashMap<String, String> doc1 = new HashMap<>();
        doc1.put("title", "Tall penguins");
        doc1.put("snippet", "Emperor penguins are the tallest.");

        // Second Document
        HashMap<String, String> doc2 = new HashMap<>();
        doc2.put("title", "Penguin habitats");
        doc2.put("snippet", "Emperor penguins only live in Antarctica.");

        // Third Document
        HashMap<String, String> doc3 = new HashMap<>();
        doc3.put("title", "What are animals?");
        doc3.put("snippet", "Animals are different from plants.");

        documents.add(doc1);
        documents.add(doc2);
        documents.add(doc3);

        CohereChatRequest request = CohereChatRequest.builder()
                .message("Where do the tallest penguins live?")
                .model("command")
                .stream(false)
                .documents(documents)
                .temperature(0.3)
                .maxTokens(1024)
                .build();

        // WHEN
        CohereChatResponse response = client.chat(request);

        // THEN
        assertThat(response.text).contains("Antarctica");
        assertFalse(response.citations.isEmpty());

    }

    @Test
    void should_use_web_search_connector() {

        // GIVEN
        Connector webSearch = Connector.builder().id("web-search").build();
        List<Connector> connectors = new ArrayList<>();
        connectors.add(webSearch);

        CohereChatRequest request = CohereChatRequest.builder()
                .message("Where do the tallest penguins live?")
                .model("command-r")
                .stream(false)
                .connectors(connectors)
                .temperature(0.3)
                .maxTokens(1024)
                .build();

        // WHEN
        CohereChatResponse response = client.chat(request);

        // THEN
        assertThat(response.text).contains("Antarctica");
        assertFalse(response.documents.isEmpty());
        assertTrue(response.documents.stream().anyMatch(map -> map.get("url").contains("http")));
    }

    @Test
    void should_return_search_queries() {
        // GIVEN
        CohereChatRequest request = CohereChatRequest.builder()
                .message("Where do the tallest penguins live?")
                .model("command-r")
                .stream(false)
                .searchQueriesOnly(true)
                .p(0.72)
                .k(300)
                .temperature(0.35)
                .maxTokens(1024)
                .build();

        // WHEN
        CohereChatResponse response = client.chat(request);

        // THEN
        assertFalse(response.searchQueries.isEmpty());
    }

    @Test
    void should_use_tools() {
        List<Tool> tools = new ArrayList<>();
        Map<String, ParameterDefinition> param1 = new HashMap<>();
        param1.put("day", ParameterDefinition.builder()
                .type("str")
                .description("Retrieves sales data for this day, formatted as YYYY-MM-DD.")
                .required(true)
                .build());
        Tool tool1 = Tool.builder()
                .name("query_daily_sales_report")
                .description("Connects to a database to retrieve overall sales volumes " +
                        "and sales information for a given day.")
                .parameterDefinitions(param1)
                .build();

        Map<String, ParameterDefinition> param2 = new HashMap<>();
        param2.put("category", ParameterDefinition.builder()
                .type("str")
                .description("Retrieves product information data for all products in this category.")
                .required(true)
                .build());
        Tool tool2 = Tool.builder()
                .name("query_product_catalog")
                .description("Connects to a a product catalog with information about all the products being sold," +
                        " including categories, prices, and stock levels.")
                .parameterDefinitions(param2)
                .build();

        tools.add(tool1);
        tools.add(tool2);

        String preamble = "## Task & Context\n" +
                "You help people answer their questions and other requests interactively. " +
                "You will be asked a very wide array of requests on all kinds of topics. " +
                "You will be equipped with a wide range of search engines or similar tools to help you, " +
                "which you use to research your answer. " +
                "You should focus on serving the user's needs as best you can, which will be wide-ranging.\n" +
                "\n" +
                "## Style Guide\n" +
                "Unless the user asks for a different style of answer, you should answer in full sentences, " +
                "using proper grammar and spelling.";

        CohereChatRequest request = CohereChatRequest.builder()
                .message("Can you provide a sales summary for 29th September 2023," +
                        " and also give me some details about the products in the 'Electronics' category," +
                        " for example their prices and stock levels?")
                .model("command-r")
                .preamble(preamble)
                .stream(false)
                .tools(tools)
                .temperature(0.35)
                .maxTokens(4000)
                .build();

        // WHEN
        CohereChatResponse response = client.chat(request);

        // THEN
        assertTrue(response.toolCalls.stream().anyMatch(toolCall ->
                toolCall.parameters.containsKey("day") && toolCall.parameters.get("day").equals("2023-09-29")));
        assertTrue(response.toolCalls.stream().anyMatch(toolCall -> toolCall.parameters.containsKey("category")
                && toolCall.parameters.get("category").equalsIgnoreCase("Electronics")));

    }
}
