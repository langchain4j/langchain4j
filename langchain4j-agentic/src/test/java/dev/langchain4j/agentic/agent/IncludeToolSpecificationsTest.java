package dev.langchain4j.agentic.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AgentBuilder#includeToolSpecifications(Object...)}, which has to describe tools to the
 * model without making them callable.
 *
 * <p>The assertions are on the {@link ChatRequest} the model actually receives, not on the builder state or
 * on the specifications derived from it. What matters here is what the model is told and what it is allowed
 * to call, and that is only visible at the request.
 */
class IncludeToolSpecificationsTest {

    enum Unit {
        CELSIUS,
        FAHRENHEIT
    }

    record Slot(String city, int hour) {}

    static class WeatherTools {

        @Tool("Gets the weather for a city")
        public String weather(
                @P(name = "city", description = "the name of the city") String city,
                @P(name = "unit", description = "the unit", required = false) Unit unit) {
            return "sunny";
        }

        @Tool("Gets the forecast for several cities")
        public String forecast(
                @P(name = "cities", description = "the names of the cities") List<String> cities,
                @P(name = "days", description = "the number of days") int days,
                @P(name = "attributes", description = "extra attributes", required = false)
                        Map<String, Object> attributes) {
            return "sunny";
        }

        @Tool("Books a slot")
        public String book(@P(name = "slot", description = "the slot to book") Slot slot) {
            return "booked";
        }
    }

    record Node(String name, List<Node> children) {}

    static class EdgeCaseTools {

        @Tool("Pings the service")
        public String ping() {
            return "pong";
        }

        @Tool("Walks a tree")
        public String walk(@P(name = "root", description = "the root node") Node root) {
            return "walked";
        }
    }

    static class NotATool {

        public String greet(String name) {
            return "hello " + name;
        }
    }

    interface PlanningAgent {

        @Agent("Produces a plan for the given request")
        @UserMessage("{{request}}")
        String plan(@V("request") String request);
    }

    static class RecordingChatModel implements ChatModel {

        private final AtomicReference<ChatRequest> lastRequest = new AtomicReference<>();

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            lastRequest.set(chatRequest);
            return ChatResponse.builder().aiMessage(AiMessage.from("a plan")).build();
        }

        ChatRequest lastRequest() {
            return lastRequest.get();
        }

        String lastSystemMessage() {
            return lastRequest.get().messages().stream()
                    .filter(SystemMessage.class::isInstance)
                    .map(message -> ((SystemMessage) message).text())
                    .findFirst()
                    .orElse(null);
        }
    }

    @Test
    void tool_specifications_should_reach_the_system_message() {
        RecordingChatModel model = new RecordingChatModel();
        PlanningAgent agent = AgenticServices.agentBuilder(PlanningAgent.class)
                .chatModel(model)
                .includeToolSpecifications(new WeatherTools())
                .build();

        agent.plan("what should I wear tomorrow?");

        String systemMessage = model.lastSystemMessage();
        assertThat(systemMessage).isNotNull();
        assertThat(systemMessage)
                .as("nothing is callable, so the model is told so")
                .contains("The following tools exist, but you cannot call them:");
        assertThat(systemMessage)
                .as("name, parameters and description of a tool with a scalar and an optional enum parameter")
                .contains("- weather(city: string, unit: enum(CELSIUS, FAHRENHEIT)?) - Gets the weather for a city")
                .as("a collection parameter, a primitive parameter, and a free form object parameter")
                .contains("- forecast(cities: string[], days: integer, attributes: object?)"
                        + " - Gets the forecast for several cities")
                .as("a record parameter, described through its components")
                .contains("- book(slot: {city: string, hour: integer}) - Books a slot");
    }

    @Test
    void tools_should_not_become_callable() {
        RecordingChatModel model = new RecordingChatModel();
        PlanningAgent agent = AgenticServices.agentBuilder(PlanningAgent.class)
                .chatModel(model)
                .includeToolSpecifications(new WeatherTools())
                .build();

        agent.plan("what should I wear tomorrow?");

        assertThat(model.lastSystemMessage()).as("the tools are described").isNotNull();
        assertThat(model.lastRequest().toolSpecifications())
                .as("but they are not offered for calling")
                .isEmpty();
    }

    @Test
    void an_existing_system_message_transformer_should_still_be_applied() {
        RecordingChatModel model = new RecordingChatModel();
        PlanningAgent agent = AgenticServices.agentBuilder(PlanningAgent.class)
                .chatModel(model)
                .systemMessageTransformer(systemMessage -> "You are a planning assistant.")
                .includeToolSpecifications(new WeatherTools())
                .build();

        agent.plan("what should I wear tomorrow?");

        String systemMessage = model.lastSystemMessage();
        assertThat(systemMessage).isNotNull();
        assertThat(systemMessage).contains("You are a planning assistant.");
        assertThat(systemMessage).contains("Gets the weather for a city");
        assertThat(systemMessage.indexOf("You are a planning assistant."))
                .as("the tool specifications are appended to the existing system message")
                .isLessThan(systemMessage.indexOf("Gets the weather for a city"));
    }

    @Test
    void no_system_message_should_be_added_without_tool_specifications() {
        RecordingChatModel model = new RecordingChatModel();
        PlanningAgent agent = AgenticServices.agentBuilder(PlanningAgent.class)
                .chatModel(model)
                .build();

        agent.plan("what should I wear tomorrow?");

        assertThat(model.lastSystemMessage()).isNull();
        assertThat(model.lastRequest().toolSpecifications()).isEmpty();
    }

    @Test
    void nothing_should_be_added_when_the_given_object_declares_no_tool() {
        RecordingChatModel model = new RecordingChatModel();
        PlanningAgent agent = AgenticServices.agentBuilder(PlanningAgent.class)
                .chatModel(model)
                .includeToolSpecifications(new NotATool())
                .build();

        agent.plan("what should I wear tomorrow?");

        assertThat(model.lastSystemMessage())
                .as("there is nothing to describe, so not even the header is added")
                .isNull();
    }

    @Test
    void the_same_object_can_be_both_described_and_callable() {
        RecordingChatModel model = new RecordingChatModel();
        WeatherTools tools = new WeatherTools();
        PlanningAgent agent = AgenticServices.agentBuilder(PlanningAgent.class)
                .chatModel(model)
                .tools(tools)
                .includeToolSpecifications(tools)
                .build();

        agent.plan("what should I wear tomorrow?");

        assertThat(model.lastSystemMessage()).contains("Gets the weather for a city");
        assertThat(model.lastSystemMessage())
                .as("tools are callable here, so the model must not be told otherwise")
                .contains("The following tools exist:")
                .doesNotContain("cannot call them");
        assertThat(model.lastRequest().toolSpecifications())
                .as("passing the same object to tools() still makes it callable")
                .hasSize(3);
    }

    @Test
    void a_tool_without_parameters_should_be_described_with_empty_parentheses() {
        RecordingChatModel model = new RecordingChatModel();
        PlanningAgent agent = AgenticServices.agentBuilder(PlanningAgent.class)
                .chatModel(model)
                .includeToolSpecifications(new EdgeCaseTools())
                .build();

        agent.plan("what should I wear tomorrow?");

        assertThat(model.lastSystemMessage())
                .as("a tool without parameters carries no schema at all")
                .contains("- ping() - Pings the service");
    }

    @Test
    void a_recursive_parameter_type_should_be_described_without_looping() {
        RecordingChatModel model = new RecordingChatModel();
        PlanningAgent agent = AgenticServices.agentBuilder(PlanningAgent.class)
                .chatModel(model)
                .includeToolSpecifications(new EdgeCaseTools())
                .build();

        agent.plan("what should I wear tomorrow?");

        assertThat(model.lastSystemMessage())
                .as("a recursive occurrence is referenced rather than expanded, and a reference carries no"
                        + " type name to show, so it is described as object")
                .contains("- walk(root: {name: string, children: object[]}) - Walks a tree");
    }
}
