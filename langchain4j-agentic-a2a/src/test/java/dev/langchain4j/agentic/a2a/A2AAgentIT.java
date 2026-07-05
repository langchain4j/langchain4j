package dev.langchain4j.agentic.a2a;

import static dev.langchain4j.agentic.a2a.Models.baseModel;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.a2a.Agents.CreativeWriter;
import dev.langchain4j.agentic.a2a.Agents.DeclarativeA2ACreativeWriter;
import dev.langchain4j.agentic.a2a.Agents.StoryCreatorWithReview;
import dev.langchain4j.agentic.a2a.Agents.StyleEditor;
import dev.langchain4j.agentic.a2a.Agents.StyleReviewLoop;
import dev.langchain4j.agentic.a2a.Agents.StyleScorer;
import dev.langchain4j.agentic.a2a.Agents.StyledWriter;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.service.V;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class A2AAgentIT {

    static final String A2A_SERVER_URL = "http://localhost:8080";

    @Test
    @Disabled("Requires A2A server to be running")
    void a2a_agent_loop_tests() {
        class WriterListener implements AgentListener {
            Object requestedTopic;

            @Override
            public void beforeAgentInvocation(final AgentRequest request) {
                requestedTopic = request.inputs().get("topic");
            }
        }

        WriterListener writerListener = new WriterListener();

        UntypedAgent creativeWriter = AgenticServices.a2aBuilder(A2A_SERVER_URL)
                .listener(writerListener)
                .inputKeys("topic")
                .outputKey("story")
                .build();

        StyleEditor styleEditor = AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleScorer styleScorer = AgenticServices.agentBuilder(StyleScorer.class)
                .chatModel(baseModel())
                .outputKey("score")
                .build();

        UntypedAgent styleReviewLoop = AgenticServices.loopBuilder()
                .subAgents(styleScorer, styleEditor)
                .maxIterations(5)
                .exitCondition(agenticScope -> agenticScope.readState("score", 0.0) >= 0.8)
                .build();

        StyledWriter styledWriter = AgenticServices.sequenceBuilder(StyledWriter.class)
                .subAgents(creativeWriter, styleReviewLoop)
                .outputKey("story")
                .build();

        ResultWithAgenticScope<String> result = styledWriter.writeStoryWithStyle("dragons and wizards", "comedy");
        String story = result.result();
        System.out.println(story);

        AgenticScope agenticScope = result.agenticScope();
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        assertThat(writerListener.requestedTopic).isEqualTo("dragons and wizards");
    }

    public interface A2ACreativeWriter {

        @Agent
        String generateStory(@V("topic") String topic);
    }

    @Test
    @Disabled("Requires A2A server to be running")
    void a2a_agent_supervisor_tests() {
        A2ACreativeWriter creativeWriter = AgenticServices.a2aBuilder(A2A_SERVER_URL, A2ACreativeWriter.class)
                .outputKey("story")
                .build();

        StyleEditor styleEditor = AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleScorer styleScorer = AgenticServices.agentBuilder(StyleScorer.class)
                .chatModel(baseModel())
                .outputKey("score")
                .build();

        Agents.StyleReviewLoop styleReviewLoop = AgenticServices.loopBuilder(StyleReviewLoop.class)
                .subAgents(styleScorer, styleEditor)
                .outputKey("story")
                .maxIterations(5)
                .exitCondition(agenticScope -> agenticScope.readState("score", 0.0) >= 0.8)
                .build();

        SupervisorAgent styledWriter = AgenticServices.supervisorBuilder()
                .chatModel(Models.plannerModel())
                .subAgents(creativeWriter, styleReviewLoop)
                .maxAgentsInvocations(5)
                .outputKey("story")
                .build();

        ResultWithAgenticScope<String> result =
                styledWriter.invokeWithAgenticScope("Write a story about dragons and wizards in the style of a comedy");
        String story = result.result();
        System.out.println(story);

        DefaultAgenticScope agenticScope = (DefaultAgenticScope) result.agenticScope();
        assertThat(agenticScope.readState("topic", "")).contains("dragons and wizards");
        assertThat(agenticScope.readState("style", "")).contains("comedy");
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        assertThat(agenticScope.agentInvocations("Creative Writer")).hasSize(1);

        List<AgentInvocation> scoreAgentCalls = agenticScope.agentInvocations("scoreStyle");
        assertThat(scoreAgentCalls).hasSizeBetween(1, 5);
        System.out.println("Score agent invocations: " + scoreAgentCalls);
        assertThat((Double) scoreAgentCalls.get(scoreAgentCalls.size() - 1).output())
                .isGreaterThanOrEqualTo(0.8);
    }

    @Test
    @Disabled("Requires A2A server to be running")
    void declarative_sequence_and_loop_tests() {
        StoryCreatorWithReview storyCreator =
                AgenticServices.createAgenticSystem(StoryCreatorWithReview.class, baseModel());

        ResultWithAgenticScope<String> result = storyCreator.write("dragons and wizards", "comedy");
        String story = result.result();
        assertThat(story).isNotBlank();

        AgenticScope agenticScope = result.agenticScope();
        assertThat(agenticScope.readState("topic")).isEqualTo("dragons and wizards");
        assertThat(agenticScope.readState("style")).isEqualTo("comedy");
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    @Disabled("Requires A2A server to be running")
    void declarative_single_a2a_agent_tests() {
        DeclarativeA2ACreativeWriter writer =
                AgenticServices.createAgenticSystem(DeclarativeA2ACreativeWriter.class, baseModel());

        String story = writer.generateStory("dragons and wizards");
        assertThat(story).isNotBlank();
    }

    public interface A2AStyleScorer {

        @Agent
        double scoreStyle(@V("story") String story, @V("style") String style);
    }

    @Test
    @Disabled("Requires A2A server to be running")
    void a2a_structured_output_tests() {
        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleEditor styleEditor = AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        A2AStyleScorer styleScorer = AgenticServices.a2aBuilder(A2A_SERVER_URL, A2AStyleScorer.class)
                .outputKey("score")
                .build();

        UntypedAgent styleReviewLoop = AgenticServices.loopBuilder()
                .subAgents(styleScorer, styleEditor)
                .maxIterations(5)
                .exitCondition(agenticScope -> agenticScope.readState("score", 0.0) >= 0.8)
                .build();

        StyledWriter styledWriter = AgenticServices.sequenceBuilder(StyledWriter.class)
                .subAgents(creativeWriter, styleReviewLoop)
                .outputKey("story")
                .build();

        ResultWithAgenticScope<String> result = styledWriter.writeStoryWithStyle("dragons and wizards", "comedy");
        String story = result.result();
        System.out.println(story);

        AgenticScope agenticScope = result.agenticScope();
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);
    }

    static final String A2A_ECHO_SERVER_URL = "http://localhost:8081";

    public interface EchoWithAgenticScopAgent {

        @A2AClientAgent(a2aServerUrl = A2A_ECHO_SERVER_URL, outputKey = "response",
                description = "Echo agent for testing contextId/taskId propagation")
        ResultWithAgenticScope<String> echo(
                @V("question") String question,
                @A2AContextId @V("contextId") String contextId,
                @A2ATaskId @V("taskId") String taskId);
    }

    /**
     * Test for issue #5311: verifies that @A2AContextId and @A2ATaskId parameters
     * are set on the A2A Message envelope instead of becoming TextParts.
     *
     * The test does a two-turn conversation:
     *   1st turn: null contextId/taskId → server generates them, returned in the AgenticScope
     *   2nd turn: reuses those IDs via @A2AContextId/@A2ATaskId → server finds the existing task
     *
     * Requires: a2a-echo-server running on port 8081
     *   cd langchain4j-agentic-a2a/a2a-echo-server
     *   mvn quarkus:dev
     */
    @Test
    @Disabled("Requires a2a-echo-server to be running on port 8081")
    void a2a_client_agent_should_propagate_contextId_and_taskId_on_message_envelope() {
        EchoWithAgenticScopAgent echoAgent = AgenticServices
                .a2aBuilder(A2A_ECHO_SERVER_URL, EchoWithAgenticScopAgent.class)
                .outputKey("response")
                .build();

        // FIRST TURN: no contextId/taskId → server creates new task and context
        ResultWithAgenticScope<String> firstResult = echoAgent.echo("hello", null, null);
        System.out.println("First response: " + firstResult.result());
        assertThat(firstResult.result()).contains("input=hello");

        // Read server-generated IDs from the AgenticScope
        AgenticScope firstScope = firstResult.agenticScope();
        String serverContextId = (String) firstScope.readState("contextId");
        String serverTaskId = (String) firstScope.readState("taskId");
        assertThat(serverContextId).isNotNull();
        assertThat(serverTaskId).isNotNull();

        // SECOND TURN: pass the server-generated IDs to continue the conversation
        // Without the fix this throws TaskNotFoundError because the IDs end up as TextParts
        ResultWithAgenticScope<String> secondResult = echoAgent.echo("follow-up question", serverContextId, serverTaskId);
        System.out.println("Second response: " + secondResult.result());

        // The server should resolve to the same context and task
        AgenticScope secondScope = secondResult.agenticScope();
        assertThat(secondScope.readState("contextId", "")).isEqualTo(serverContextId);
        assertThat(secondScope.readState("taskId", "")).isEqualTo(serverTaskId);
        assertThat(secondResult.result()).contains("input=follow-up question");
    }

    public interface EchoAgent {

        @A2AClientAgent(a2aServerUrl = A2A_ECHO_SERVER_URL, outputKey = "response",
                description = "Echo sub-agent for multi-turn workflow")
        String echo(@V("question") String question,
                    @A2AContextId @V("contextId") String contextId,
                    @A2ATaskId @V("taskId") String taskId);
    }

    public interface MultiTurnWorkflow extends AgenticScopeAccess {

        @Agent
        ResultWithAgenticScope<String> converse(@V("question") String question);
    }

    /**
     * Tests @A2AContextId/@A2ATaskId in a complete workflow-based agentic system.
     *
     * A sequence of two echo agents where:
     *   1st agent: sends message with no contextId/taskId → server generates them → written to scope
     *   2nd agent: reads contextId/taskId from scope → sends them on the message envelope → server
     *              finds the existing task and continues the conversation
     *
     * This proves contextId/taskId flow through the AgenticScope across agents in a workflow.
     *
     * Requires: a2a-echo-server running on port 8081
     */
    @Test
    @Disabled("Requires a2a-echo-server to be running on port 8081")
    void a2a_multi_turn_sequence_should_propagate_contextId_and_taskId_through_scope() {
        EchoAgent firstTurn = AgenticServices
                .a2aBuilder(A2A_ECHO_SERVER_URL, EchoAgent.class)
                .outputKey("firstResponse")
                .build();

        EchoAgent secondTurn = AgenticServices
                .a2aBuilder(A2A_ECHO_SERVER_URL, EchoAgent.class)
                .outputKey("secondResponse")
                .build();

        MultiTurnWorkflow workflow = AgenticServices.sequenceBuilder(MultiTurnWorkflow.class)
                .subAgents(firstTurn, secondTurn)
                .outputKey("secondResponse")
                .build();

        ResultWithAgenticScope<String> result = workflow.converse("hello");
        AgenticScope scope = result.agenticScope();

        // contextId and taskId should have been written to the scope by the first agent
        // and reused by the second agent via @A2AContextId/@A2ATaskId
        String contextId = (String) scope.readState("contextId");
        String taskId = (String) scope.readState("taskId");
        assertThat(contextId).isNotNull();
        assertThat(taskId).isNotNull();

        // The second response proves the IDs were correctly propagated through the scope:
        // if they weren't, the server would have created a new task instead of continuing
        assertThat(result.result())
                .contains("contextId=" + contextId)
                .contains("taskId=" + taskId)
                .contains("input=hello");
    }
}
