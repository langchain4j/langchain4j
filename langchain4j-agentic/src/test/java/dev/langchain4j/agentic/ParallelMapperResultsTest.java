package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * A parallel mapper returns the result of each item, also when its subagent has no output key and when several
 * mappers whose subagents share an output key, or the same subagent, run in parallel.
 */
class ParallelMapperResultsTest {

    static final ChatModel ECHO_MODEL = ChatModelMock.thatResponds(chatRequest -> {
        dev.langchain4j.data.message.UserMessage userMessage = (dev.langchain4j.data.message.UserMessage)
                chatRequest.messages().get(chatRequest.messages().size() - 1);
        return AiMessage.from("echo " + userMessage.singleText());
    });

    public interface EchoAgent {

        @UserMessage("{{text}}")
        @Agent(description = "Echoes a text")
        String echo(@V("text") String text);
    }

    public interface KeyedEchoAgent {

        @UserMessage("{{text}}")
        @Agent(description = "Echoes a text", outputKey = "echo")
        String echo(@V("text") String text);
    }

    public interface ListMapper {

        @Agent(outputKey = "echoes")
        List<String> mapAll(@V("texts") List<String> texts);
    }

    public interface ArrayMapper {

        @Agent(outputKey = "echoes")
        String[] mapAll(@V("texts") List<String> texts);
    }

    @Test
    void parallel_mapper_returns_the_result_of_each_item_when_its_subagent_has_no_output_key() {
        ListMapper mapper = AgenticServices.parallelMapperBuilder(ListMapper.class)
                .subAgents(echoAgent(false))
                .build();

        assertThat(mapper.mapAll(List.of("a", "b", "c"))).containsExactly("echo a", "echo b", "echo c");
    }

    @Test
    void parallel_mapper_returns_an_array_of_results_when_its_subagent_has_no_output_key() {
        ArrayMapper mapper = AgenticServices.parallelMapperBuilder(ArrayMapper.class)
                .subAgents(echoAgent(false))
                .build();

        assertThat(mapper.mapAll(List.of("a", "b"))).containsExactly("echo a", "echo b");
    }

    @Test
    void parallel_mapper_waits_for_the_results_of_an_asynchronous_subagent_without_output_key() {
        ListMapper mapper = AgenticServices.parallelMapperBuilder(ListMapper.class)
                .subAgents(echoAgent(true))
                .build();

        assertThat(mapper.mapAll(List.of("a", "b", "c"))).containsExactly("echo a", "echo b", "echo c");
    }

    @Test
    void skipped_item_still_maps_to_null_when_the_subagent_has_no_output_key() {
        AtomicInteger evaluations = new AtomicInteger();
        EchoAgent echo = AgenticServices.agentBuilder(EchoAgent.class)
                .chatModel(ECHO_MODEL)
                .activationCondition("not the second item", agenticScope -> evaluations.getAndIncrement() != 1)
                .build();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ListMapper mapper = AgenticServices.parallelMapperBuilder(ListMapper.class)
                    .subAgents(echo)
                    .executor(executor)
                    .build();

            assertThat(mapper.mapAll(List.of("a", "b", "c"))).containsExactly("echo a", null, "echo c");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void parallel_mapper_leaves_no_result_of_its_items_in_the_agentic_scope() {
        AtomicReference<Set<String>> stateKeys = new AtomicReference<>();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(
                        AgenticServices.parallelMapperBuilder()
                                .subAgents(echoAgent(false))
                                .itemsProvider("texts")
                                .outputKey("echoes")
                                .build(),
                        AgenticServices.parallelMapperBuilder()
                                .subAgents(AgenticServices.agentBuilder(KeyedEchoAgent.class)
                                        .chatModel(ECHO_MODEL)
                                        .build())
                                .itemsProvider("texts")
                                .outputKey("keyedEchoes")
                                .build(),
                        AgenticServices.nonAiAgentBuilder((AgenticScope agenticScope) -> {
                                    stateKeys.set(
                                            Set.copyOf(agenticScope.state().keySet()));
                                    return "done";
                                })
                                .name("inspector")
                                .outputKey("inspected")
                                .build())
                .outputKey("echoes")
                .build();

        assertThat(sequence.invoke(Map.of("texts", List.of("a", "b")))).isEqualTo(List.of("echo a", "echo b"));
        assertThat(stateKeys.get()).contains("texts", "echoes", "keyedEchoes").noneMatch(key -> key.matches(".*_\\d+"));
    }

    @Test
    void parallel_mappers_with_subagents_without_output_key_do_not_mix_their_results() {
        UntypedAgent parallel = AgenticServices.parallelBuilder()
                .subAgents(
                        AgenticServices.parallelMapperBuilder()
                                .subAgents(echoAgent(false))
                                .itemsProvider("letters")
                                .outputKey("echoedLetters")
                                .build(),
                        AgenticServices.parallelMapperBuilder()
                                .subAgents(echoAgent(false))
                                .itemsProvider("digits")
                                .outputKey("echoedDigits")
                                .build())
                .output(agenticScope ->
                        List.of(agenticScope.readState("echoedLetters"), agenticScope.readState("echoedDigits")))
                .build();

        assertThat(parallel.invoke(Map.of("letters", List.of("a", "b"), "digits", List.of("1", "2"))))
                .isEqualTo(List.of(List.of("echo a", "echo b"), List.of("echo 1", "echo 2")));
    }

    @Test
    void parallel_mappers_whose_subagents_share_an_output_key_do_not_mix_their_results() {
        List<String> instanceOutputKeys = new CopyOnWriteArrayList<>();
        UntypedAgent parallel = AgenticServices.parallelBuilder()
                .subAgents(
                        AgenticServices.parallelMapperBuilder()
                                .subAgents(keyedEchoAgent())
                                .itemsProvider("letters")
                                .outputKey("echoedLetters")
                                .build(),
                        AgenticServices.parallelMapperBuilder()
                                .subAgents(keyedEchoAgent())
                                .itemsProvider("digits")
                                .outputKey("echoedDigits")
                                .build())
                .output(agenticScope ->
                        List.of(agenticScope.readState("echoedLetters"), agenticScope.readState("echoedDigits")))
                .listener(new AgentListener() {
                    @Override
                    public void beforeAgentInvocation(AgentRequest agentRequest) {
                        if (agentRequest.agentName().startsWith("echo")) {
                            instanceOutputKeys.add(agentRequest.agent().outputKey());
                        }
                    }

                    @Override
                    public boolean inheritedBySubagents() {
                        return true;
                    }
                })
                .build();

        assertThat(parallel.invoke(Map.of("letters", List.of("a", "b"), "digits", List.of("1", "2"))))
                .isEqualTo(List.of(List.of("echo a", "echo b"), List.of("echo 1", "echo 2")));
        assertThat(instanceOutputKeys).hasSize(4).doesNotHaveDuplicates();
    }

    @Test
    void parallel_mappers_sharing_the_same_subagent_do_not_mix_their_results() {
        EchoAgent echo = echoAgent(false);
        List<String> instanceOutputKeys = new CopyOnWriteArrayList<>();
        UntypedAgent parallel = AgenticServices.parallelBuilder()
                .subAgents(
                        AgenticServices.parallelMapperBuilder()
                                .subAgents(echo)
                                .itemsProvider("letters")
                                .outputKey("echoedLetters")
                                .build(),
                        AgenticServices.parallelMapperBuilder()
                                .subAgents(echo)
                                .itemsProvider("digits")
                                .outputKey("echoedDigits")
                                .build())
                .output(agenticScope ->
                        List.of(agenticScope.readState("echoedLetters"), agenticScope.readState("echoedDigits")))
                .listener(new AgentListener() {
                    @Override
                    public void beforeAgentInvocation(AgentRequest agentRequest) {
                        if (agentRequest.agentName().startsWith("echo")) {
                            instanceOutputKeys.add(agentRequest.agent().outputKey());
                        }
                    }

                    @Override
                    public boolean inheritedBySubagents() {
                        return true;
                    }
                })
                .build();

        assertThat(parallel.invoke(Map.of("letters", List.of("a", "b"), "digits", List.of("1", "2"))))
                .isEqualTo(List.of(List.of("echo a", "echo b"), List.of("echo 1", "echo 2")));
        assertThat(instanceOutputKeys).hasSize(4).doesNotHaveDuplicates();
    }

    private static KeyedEchoAgent keyedEchoAgent() {
        return AgenticServices.agentBuilder(KeyedEchoAgent.class)
                .chatModel(ECHO_MODEL)
                .build();
    }

    private static EchoAgent echoAgent(boolean async) {
        return AgenticServices.agentBuilder(EchoAgent.class)
                .chatModel(ECHO_MODEL)
                .async(async)
                .build();
    }
}
