package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgentConfigurator;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.V;
import java.util.List;
import org.junit.jupiter.api.Test;

class A2AServerUrlResolutionTest {

    public interface A2AWithNoUrl {

        @A2AClientAgent(outputKey = "story")
        String generateStory(@V("topic") String topic);
    }

    public interface A2AWithBothUrlAndSupplier {

        @A2AClientAgent(a2aServerUrl = "http://localhost:8080", outputKey = "story")
        String generateStory(@V("topic") String topic);

        @A2AServerUrlSupplier
        static String serverUrl() {
            return "http://localhost:8080";
        }
    }

    @Test
    void should_fail_when_neither_url_nor_supplier_is_provided() {
        assertThatThrownBy(() ->
                AgenticServices.createAgenticSystem(A2AWithNoUrl.class, (ChatModel) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@A2AServerUrlSupplier");
    }

    @Test
    void should_fail_when_both_url_and_supplier_are_provided() {
        assertThatThrownBy(() ->
                AgenticServices.createAgenticSystem(A2AWithBothUrlAndSupplier.class, (ChatModel) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not both");
    }

    public interface A2AWithSupplierTakingParameters {

        @A2AClientAgent(outputKey = "story")
        String generateStory(@V("topic") String topic);

        @A2AServerUrlSupplier
        static String serverUrl(String env) {
            return "http://localhost:8080";
        }
    }

    @Test
    void should_fail_when_supplier_takes_parameters() {
        assertThatThrownBy(() ->
                AgenticServices.createAgenticSystem(A2AWithSupplierTakingParameters.class, (ChatModel) null))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("No SupplierParameterResolver is registered for parameter")
                .hasMessageContaining("@A2AServerUrlSupplier");
    }

    public interface A2AWithNonStaticSupplier {

        @A2AClientAgent(outputKey = "story")
        String generateStory(@V("topic") String topic);

        @A2AServerUrlSupplier
        default String serverUrl() {
            return "http://localhost:8080";
        }
    }

    public interface A2AWithHelperObject {

        @A2AClientAgent(outputKey = "story")
        String generateStory(@V("topic") String topic);
    }

    public interface StoryPipeline {

        @SequenceAgent(outputKey = "story", subAgents = A2AWithHelperObject.class)
        String write(@V("topic") String topic, @V("enabled") boolean enabled);
    }

    /** The helper object of the A2A agent, as a container bean would be: its helpers are instance methods. */
    public static class A2AHelpers {

        @A2AServerUrlSupplier
        String serverUrl() {
            return A2ALazyAgentCardTest.UNREACHABLE_A2A_SERVER_URL;
        }

        @ActivationCondition(description = "stories are enabled")
        boolean enabled(@V("enabled") boolean enabled) {
            return enabled;
        }
    }

    @Test
    void helper_object_supplies_the_server_url_and_the_activation_condition() {
        var configurator = new AgentConfigurator(
                ctx -> {},
                null,
                null,
                null,
                type -> type == A2AWithHelperObject.class ? List.of(new A2AHelpers()) : List.of());

        StoryPipeline pipeline =
                AgenticServices.createAgenticSystem(StoryPipeline.class, (ChatModel) null, configurator);

        AgentInstance a2aAgent = ((AgentInstance) pipeline).subagents().get(0);
        assertThat(a2aAgent.activationCondition().description()).isEqualTo("stories are enabled");
        assertThat(pipeline.write("dragons", false)).isNull();
        // enabled, the agent fetches its card from the URL of the helper object, which is unreachable
        assertThatThrownBy(() -> pipeline.write("dragons", true)).satisfies(failure -> {
            Throwable cause = failure;
            while (cause != null && !(cause instanceof A2AAgentCardException)) {
                cause = cause.getCause();
            }
            assertThat(cause).isInstanceOf(A2AAgentCardException.class);
            assertThat(((A2AAgentCardException) cause).a2aServerUrl())
                    .isEqualTo(A2ALazyAgentCardTest.UNREACHABLE_A2A_SERVER_URL);
        });
    }

    @Test
    void should_fail_when_supplier_is_not_static() {
        assertThatThrownBy(() ->
                AgenticServices.createAgenticSystem(A2AWithNonStaticSupplier.class, (ChatModel) null))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("@A2AServerUrlSupplier")
                .hasMessageContaining("is not static: make it static, or provide the agent as one of its helper objects");
    }
}
