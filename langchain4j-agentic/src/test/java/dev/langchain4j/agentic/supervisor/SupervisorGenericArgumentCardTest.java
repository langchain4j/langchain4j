package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Tests the agent cards the supervisor puts in its planning prompt, for arguments whose declared
 * Java type is not a plain class.
 *
 * <p>An argument declared as {@code List<String>} carries a
 * {@link java.lang.reflect.ParameterizedType}, an argument declared as {@code String[]} carries an
 * array class, and so on. The card shown to the planning model has to describe those shapes,
 * otherwise the model guesses and sends values the agent cannot use.
 *
 * <p>These assertions are on the rendered card rather than on the {@code AgentArgument} derived
 * upstream. A test that only looks at the derived argument would not catch a regression here,
 * because the two halves are separate: the type is derived correctly today and lost later, when
 * the card is written.
 */
class SupervisorGenericArgumentCardTest {

    record RecordFilter(String name, List<Integer> values) {}

    interface SearchSubAgent {

        @Agent("Searches records in the backing system")
        String search(
                @V("recordType") String recordType,
                @V("fields") List<String> fields,
                @V("recordIds") String[] recordIds,
                @V("filters") Map<String, Object> filters,
                @V("unknown") Object unknown,
                @V("recordFilter") RecordFilter recordFilter);
    }

    /** Captures the first system message it is asked to process, then aborts the planning loop. */
    static class CapturingChatModel implements ChatModel {

        private final AtomicReference<String> systemMessage = new AtomicReference<>();

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            chatRequest.messages().stream()
                    .filter(SystemMessage.class::isInstance)
                    .findFirst()
                    .ifPresent(message -> systemMessage.compareAndSet(null, ((SystemMessage) message).text()));
            throw new IllegalStateException("prompt captured, aborting the planning loop");
        }

        String capturedPrompt() {
            return systemMessage.get();
        }
    }

    @Test
    void agent_card_should_describe_argument_types_that_are_not_plain_classes() {
        CapturingChatModel model = new CapturingChatModel();

        SearchSubAgent subAgent = AgenticServices.agentBuilder(SearchSubAgent.class)
                .chatModel(model)
                .build();

        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(model)
                .subAgents(subAgent)
                .build();

        try {
            supervisor.invoke("find the open tickets");
        } catch (RuntimeException expected) {
            // The capturing model always throws; the prompt is all we need.
        }

        String card = model.capturedPrompt();
        assertThat(card).as("the planner prompt").isNotNull();

        // Scalars rendered like this before the fix too, and still do.
        assertThat(card).contains("recordType: String");

        // Everything below came out as {} before, which left the planner guessing the shape of
        // the argument and sending values the agent could not use.
        assertThat(card).contains("fields: List<String>");
        assertThat(card).contains("recordIds: String[]");
        assertThat(card).contains("filters: Map<String, Object>");
        assertThat(card).contains("unknown: Object");
        assertThat(card).contains("recordFilter: {name: String, values: List<Integer>}");
    }
}
