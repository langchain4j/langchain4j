package dev.langchain4j.agentic.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.Test;

class AgenticScopeOutputTest {

    @Test
    void readDoesNotReturnPreviousStreamingOutputAfterOutputKeyIsCleared() {
        DefaultAgenticScope agenticScope = DefaultAgenticScope.ephemeralAgenticScope();
        TokenStream previousStream = mock(TokenStream.class);

        AgenticScopeOutput.write(agenticScope, "result", previousStream);
        AgenticScopeOutput.write(agenticScope, "result", null);

        assertThat(AgenticScopeOutput.read(agenticScope, "result", "default")).isEqualTo("default");
    }
}
