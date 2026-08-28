package dev.langchain4j.agentic;

import static dev.langchain4j.agentic.Models.baseModel;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
public class InvocationParametersIT {

    static final AtomicReference<InvocationParameters> CAPTURED_PARAMS = new AtomicReference<>();

    public static class GreetingTool {

        @Tool("Greet the user by name")
        String greet(String name, InvocationParameters params) {
            CAPTURED_PARAMS.set(params);
            String greeting = params.getOrDefault("greeting", "Hello");
            return greeting + ", " + name + "!";
        }
    }

    public interface GreetingAgent {

        @UserMessage("""
            Greet the user using the greet tool.
            The user's name is: {{name}}.
            Return only the greeting, nothing else.
            """)
        @Agent(description = "Greets a user by name", outputKey = "greeting")
        String greet(@V("name") String name, InvocationParameters params);
    }

    @Test
    void single_agent_receives_invocation_parameters_in_tool() {
        CAPTURED_PARAMS.set(null);

        GreetingAgent agent = AgenticServices.agentBuilder(GreetingAgent.class)
                .chatModel(baseModel())
                .tools(new GreetingTool())
                .build();

        InvocationParameters params = InvocationParameters.from("greeting", "Bonjour");
        agent.greet("Mario", params);

        assertThat(CAPTURED_PARAMS.get()).isNotNull();
        assertThat(CAPTURED_PARAMS.get().<String>get("greeting")).isEqualTo("Bonjour");
    }

    // --- Sequence agent propagation ---

    static final AtomicReference<InvocationParameters> FIRST_AGENT_PARAMS = new AtomicReference<>();
    static final AtomicReference<InvocationParameters> SECOND_AGENT_PARAMS = new AtomicReference<>();

    public static class DraftTool {

        @Tool("Generate a draft sentence about the topic")
        String draft(String topic, InvocationParameters params) {
            FIRST_AGENT_PARAMS.set(params);
            String style = params.getOrDefault("style", "neutral");
            return "A " + style + " draft about " + topic + ".";
        }
    }

    public static class ReviewTool {

        @Tool("Review the given text and return feedback")
        String review(String text, InvocationParameters params) {
            SECOND_AGENT_PARAMS.set(params);
            return "Reviewed: " + text;
        }
    }

    public interface DraftAgent {

        @UserMessage("""
            Write a one-sentence draft about the topic using the draft tool.
            The topic is: {{topic}}.
            Return only the draft text.
            """)
        @Agent(description = "Writes a draft", outputKey = "draft")
        String writeDraft(@V("topic") String topic, InvocationParameters params);

        @ToolsSupplier
        static Object tools() {
            return new DraftTool();
        }
    }

    public interface ReviewAgent {

        @UserMessage("""
            Review the following draft using the review tool and return the reviewed text.
            The draft is: {{draft}}.
            Return only the reviewed text.
            """)
        @Agent(description = "Reviews a draft", outputKey = "reviewed")
        String reviewDraft(@V("draft") String draft, InvocationParameters params);

        @ToolsSupplier
        static Object tools() {
            return new ReviewTool();
        }
    }

    public interface DraftAndReviewSequence {

        @SequenceAgent(outputKey = "reviewed", subAgents = {DraftAgent.class, ReviewAgent.class})
        String draftAndReview(@V("topic") String topic, InvocationParameters params);
    }

    @Test
    void sequence_agent_propagates_invocation_parameters_to_all_sub_agents() {
        FIRST_AGENT_PARAMS.set(null);
        SECOND_AGENT_PARAMS.set(null);

        DraftAndReviewSequence sequence = AgenticServices.createAgenticSystem(DraftAndReviewSequence.class, baseModel());

        InvocationParameters params = InvocationParameters.from("style", "humorous");
        sequence.draftAndReview("cats", params);

        assertThat(FIRST_AGENT_PARAMS.get()).isNotNull();
        assertThat(FIRST_AGENT_PARAMS.get().<String>get("style")).isEqualTo("humorous");

        assertThat(SECOND_AGENT_PARAMS.get()).isNotNull();
        assertThat(SECOND_AGENT_PARAMS.get().<String>get("style")).isEqualTo("humorous");
    }

    // --- Mixed chain: sub-agent without InvocationParameters ---

    public interface DraftAgentNoParams {

        @UserMessage("""
            Write a one-sentence draft about the topic using the draft tool.
            The topic is: {{topic}}.
            Return only the draft text.
            """)
        @Agent(description = "Writes a draft", outputKey = "draft")
        String writeDraft(@V("topic") String topic);

        @ToolsSupplier
        static Object tools() {
            return new DraftTool();
        }
    }

    public interface MixedSequence {

        @SequenceAgent(outputKey = "reviewed", subAgents = {DraftAgentNoParams.class, ReviewAgent.class})
        String draftAndReview(@V("topic") String topic, InvocationParameters params);
    }

    @Test
    void mixed_chain_propagates_parameters_even_when_intermediate_agent_does_not_declare_them() {
        SECOND_AGENT_PARAMS.set(null);

        MixedSequence sequence = AgenticServices.createAgenticSystem(MixedSequence.class, baseModel());

        InvocationParameters params = InvocationParameters.from("style", "formal");
        sequence.draftAndReview("dogs", params);

        assertThat(SECOND_AGENT_PARAMS.get()).isNotNull();
        assertThat(SECOND_AGENT_PARAMS.get().<String>get("style")).isEqualTo("formal");
    }

    // --- No InvocationParameters passed by user ---

    @Test
    void agent_without_invocation_parameters_still_works() {
        DraftAgentNoParams agent = AgenticServices.createAgenticSystem(DraftAgentNoParams.class, baseModel());

        String result = agent.writeDraft("space");
        assertThat(result).isNotBlank();
    }
}
