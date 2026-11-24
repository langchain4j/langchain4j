package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies how the supervisor handles planning replies that parse as an {@link AgentInvocation} but
 * are not usable as they are: a reply that names no agent must fail with a readable error instead of
 * a {@link NullPointerException}, and a missing {@code arguments} map must be treated as empty.
 */
class SupervisorAgentPlanningReplyTest {

    @Test
    void should_fail_with_a_readable_error_when_the_planning_reply_has_no_agent_name() {
        ChatModel model = replyWith("{\"arguments\": {}}");

        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(model)
                .subAgents(AgenticServices.agentAction(() -> {}))
                .build();

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> supervisor.invoke("do something"))
                .withMessage("No agent name in the planning reply");
    }

    @Test
    void should_treat_a_missing_arguments_map_as_empty() {
        ChatModel model = replyWith(
                "{\"agentName\": \"run\", \"arguments\": null}",
                "{\"agentName\": \"done\", \"arguments\": {\"response\": \"ok\"}}");

        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(model)
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .subAgents(AgenticServices.agentAction(() -> {}))
                .build();

        assertThatCode(() -> supervisor.invoke("do something")).doesNotThrowAnyException();
    }

    private static ChatModel replyWith(String... replies) {
        Deque<String> queue = new ArrayDeque<>(List.of(replies));
        return new ChatModel() {

            @Override
            public ChatResponse doChat(ChatRequest request) {
                if (queue.size() > 1) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from(queue.poll()))
                            .build();
                }
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(queue.peek()))
                        .build();
            }
        };
    }
}
