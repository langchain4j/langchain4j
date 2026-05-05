package dev.langchain4j.invocation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvocationContextTest {

    @Test
    void managedParameters_returns_empty_map_when_not_set() {
        // When LangChain4jManaged.current() returns null, managedParameters is built with null.
        // managedParameters() must return an empty map (not null) to honour the interface contract
        // and prevent NPE in callers such as AgentInvocationHandler.
        InvocationContext ctx = InvocationContext.builder()
                .chatMemoryId("test")
                .managedParameters(null)
                .build();

        assertThat(ctx.managedParameters()).isNotNull().isEmpty();
    }

    @Test
    void managedParameters_returns_supplied_map_when_set() {
        var params = new java.util.HashMap<Class<? extends LangChain4jManaged>, LangChain4jManaged>();
        InvocationContext ctx = InvocationContext.builder()
                .chatMemoryId("test")
                .managedParameters(params)
                .build();

        assertThat(ctx.managedParameters()).isSameAs(params);
    }
}
