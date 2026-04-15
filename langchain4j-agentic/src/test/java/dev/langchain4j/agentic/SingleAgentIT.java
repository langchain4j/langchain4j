package dev.langchain4j.agentic;

import static dev.langchain4j.agentic.Models.baseModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
public class SingleAgentIT {

    @Test
    public void invoke_standalone_agent_with_tools_and_listeners() {
        ConsoleAgentListener listener = new ConsoleAgentListener("[agent-02-standalone]");
        String topic = "dragons and wizards";

        ToolProvider demoTools = buildDemoTools();

        TextCreativeWriter writer = AgenticServices.agentBuilder(TextCreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .toolProviders(demoTools)
                .listener(listener)
                .build();

        String story = writer.generateStoryText(topic);
        System.out.println("story:\n" + story);
    }

    private static ToolProvider buildDemoTools() {
        ObjectMapper mapper = new ObjectMapper();

        ToolSpecification spec = ToolSpecification.builder()
                .name("demo_hint")
                .description("Give a short story opening hint based on topic")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("topic", "Optional user topic")
                        .build())
                .build();

        ToolExecutor executor = (ToolExecutionRequest request, Object memoryId) -> {
            String topic = null;
            try {
                Map<?, ?> args = mapper.readValue(request.arguments(), Map.class);
                Object v = args.get("topic");
                topic = v == null ? null : String.valueOf(v);
            } catch (Exception ignored) {
                // demo only
            }
            String t = (topic == null || topic.isBlank()) ? "your topic" : topic.trim();
            return "Opening hint: when you stare at \"" + t + "\", the story ignites.";
        };

        return ignored -> ToolProviderResult.builder().add(spec, executor).build();
    }

    private static final class ConsoleAgentListener implements AgentListener {

        private final String tag;

        private ConsoleAgentListener(String tag) {
            this.tag = tag;
        }

        @Override
        public void beforeAgentInvocation(AgentRequest request) {
            System.out.println(tag + " beforeAgentInvocation agent=" + request.agent().name()
                    + " inputs=" + request.inputs());
        }

        @Override
        public void afterAgentInvocation(AgentResponse response) {
            System.out.println(tag + " afterAgentInvocation agent=" + response.agent().name()
                    + " output=" + response.output());
        }

        @Override
        public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
            System.err.println(tag + " onAgentInvocationError " + agentInvocationError);
        }

        @Override
        public void afterAgenticScopeCreated(AgenticScope agenticScope) {
            System.out.println(tag + " afterAgenticScopeCreated scope=" + agenticScope);
        }

        @Override
        public void beforeAgenticScopeDestroyed(AgenticScope agenticScope) {
            System.out.println(tag + " beforeAgenticScopeDestroyed scope=" + agenticScope);
        }

        @Override
        public void beforeAgentToolExecution(BeforeAgentToolExecution beforeAgentToolExecution) {
            System.out.println(tag + " beforeAgentToolExecution " + beforeAgentToolExecution);
        }

        @Override
        public void afterAgentToolExecution(AfterAgentToolExecution afterAgentToolExecution) {
            System.out.println(tag + " afterAgentToolExecution " + afterAgentToolExecution);
        }

        @Override
        public boolean inheritedBySubagents() {
            return false;
        }
    }

    public interface TextCreativeWriter {

        @UserMessage("""
            You are a creative fiction writer.
            Blend the inspiration naturally into the story and output only the final story content.
            Write a short story draft around the given topic in no more than 3 sentences.
            Return only the story text, without any explanation, title, or extra prefix/suffix.
            The topic is: {{topic}}.
            """)
        @Agent(outputKey = "story", description = "Generates a story based on topic (non-streaming)")
        String generateStoryText(@V("topic") String topic);
    }
}
