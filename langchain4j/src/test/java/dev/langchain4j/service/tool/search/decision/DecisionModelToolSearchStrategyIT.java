package dev.langchain4j.service.tool.search.decision;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class DecisionModelToolSearchStrategyIT {

    DecisionModel decisionModel = TypeSafeDecisionModel.builder()
            .apiKey(System.getenv("TYPESAFE_API_KEY"))
            .modelName("jev-1.13.0")
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void tool_search_strategy_should_find_relevant_tools() {

        DecisionModelToolSearchStrategy strategy = new DecisionModelToolSearchStrategy(decisionModel);

        List<String> found = strategy.search(ToolSearchRequest.builder()
                        .toolExecutionRequest(ToolExecutionRequest.builder()
                                .name("tool_search_tool")
                                .arguments("{\"query\": \"Will it rain in Berlin tomorrow?\"}")
                                .build())
                        .searchableTools(List.of(
                                tool("send_email", "Sends an email to a recipient"),
                                tool("get_weather_forecast", "Returns the weather forecast for a city and date"),
                                tool("create_invoice", "Creates an invoice for a customer")))
                        .invocationContext(InvocationContext.builder().build())
                        .build())
                .foundToolNames();

        assertThat(found).containsExactly("get_weather_forecast");
    }

    static ToolSpecification tool(String name, String description) {
        return ToolSpecification.builder().name(name).description(description).build();
    }
}
