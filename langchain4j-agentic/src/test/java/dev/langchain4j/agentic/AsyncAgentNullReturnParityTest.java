package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AsyncAgentNullReturnParityTest {

    public static class NullReturningSyncAgent {
        @Agent(outputKey = "unread")
        public String run() {
            return null;
        }
    }

    public static class NullReturningAsyncAgent {
        @Agent(async = true, outputKey = "unread")
        public String run() {
            return null;
        }
    }

    public static class ResultAgent {
        @Agent(outputKey = "result")
        public String run() {
            return "done";
        }
    }

    @Test
    void sync_agent_returning_null_completes() {
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(new NullReturningSyncAgent(), new ResultAgent())
                .outputKey("result")
                .build();

        assertThatCode(() -> workflow.invoke(Map.of())).doesNotThrowAnyException();
    }

    @Test
    void async_agent_returning_null_completes_like_sync() {
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(new NullReturningAsyncAgent(), new ResultAgent())
                .outputKey("result")
                .build();

        assertThatCode(() -> workflow.invoke(Map.of())).doesNotThrowAnyException();
    }
}
