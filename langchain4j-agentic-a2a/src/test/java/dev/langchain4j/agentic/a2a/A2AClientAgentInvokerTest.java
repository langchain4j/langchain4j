package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AgentInvocationArguments;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;

class A2AClientAgentInvokerTest {

    private static A2AClientAgentInvoker untypedInvoker() throws NoSuchMethodException {
        AgentCard agentCard = AgentCard.builder()
                .name("generate")
                .description("Generate content")
                .version("1.0.0")
                .url("http://localhost")
                .capabilities(new AgentCapabilities(false, false, false, List.of()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of())
                .build();

        A2AClientInstance clientInstance = mock(A2AClientInstance.class);
        when(clientInstance.agentCard()).thenReturn(agentCard);
        when(clientInstance.inputKeys()).thenReturn(new String[] {"topic"});

        Method invoke = UntypedAgent.class.getMethod("invoke", Map.class);
        return new A2AClientAgentInvoker(clientInstance, invoke);
    }

    @Test
    void untyped_invocation_arguments_are_a_snapshot_of_the_state() throws NoSuchMethodException {
        DefaultAgenticScope agenticScope = DefaultAgenticScope.ephemeralAgenticScope();
        agenticScope.writeState("topic", "dragons");

        AgentInvocationArguments args = untypedInvoker().toInvocationArguments(agenticScope);

        // The arguments must be the state as it was at invocation time, not a live view of it.
        assertThat(args.namedArgs()).isNotSameAs(agenticScope.state());
        assertThat(args.namedArgs()).isEqualTo(Map.of("topic", "dragons"));
        assertThat(args.positionalArgs()).containsExactly(args.namedArgs());

        agenticScope.writeState("story", "A story about dragons");
        assertThat(args.namedArgs()).isEqualTo(Map.of("topic", "dragons"));
    }

    @Test
    void untyped_invocation_arguments_do_not_contain_non_serializable_state_values() throws NoSuchMethodException {
        DefaultAgenticScope agenticScope = DefaultAgenticScope.ephemeralAgenticScope();
        agenticScope.writeState("topic", "dragons");
        agenticScope.writeState("pendingResult", new CompletableFuture<>());

        AgentInvocationArguments args = untypedInvoker().toInvocationArguments(agenticScope);

        assertThat(agenticScope.state()).containsKey("pendingResult");
        assertThat(args.namedArgs()).isEqualTo(Map.of("topic", "dragons"));
    }
}
