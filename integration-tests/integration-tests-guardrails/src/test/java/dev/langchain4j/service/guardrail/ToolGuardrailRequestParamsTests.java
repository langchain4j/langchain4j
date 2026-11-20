package dev.langchain4j.service.guardrail;

import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.ORIGINAL_ARGUMENTS;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.callingSearchThenAnswering;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.input;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.SearchTool;
import dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.ToolKind;
import dev.langchain4j.test.mock.MockChatModel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The counterpart of {@code InputGuardrailPromptTemplateTests} for tool guardrails: the {@code GuardrailRequestParams}
 * of a tool guardrail request carry the user message template and the variables of every shape of AI service method,
 * with and without a memory ID, and with and without chat memory.
 */
class ToolGuardrailRequestParamsTests extends BaseGuardrailTests {

    /** The methods without a memory ID: such an AI service builds with or without chat memory. */
    interface Assistant {

        @UserMessage("Tell me a joke")
        String noParameters();

        @UserMessage("Say hi to my friend {{it}}!")
        String oneParameterWithoutName(String friend);

        @UserMessage("Tell me something about {{topic1}}, {{topic2}}, {{topic3}}!")
        String threeParameters(@V("topic1") String topic1, @V("topic2") String topic2, @V("topic3") String topic3);

        @UserMessage("Tell me something about {{it}}!")
        String list(List<String> topics);
    }

    /** The methods with a memory ID: such an AI service only builds with a chat memory provider. */
    interface MemoryAssistant {

        @UserMessage("Tell me another joke")
        String withMemoryId(@MemoryId String memoryId);

        @UserMessage("Say hi to my friend {{friend}}!")
        String memoryIdAndOneParameter(@MemoryId String memoryId, @V("friend") String friend);

        @UserMessage("Tell me something about {{topics}}! This is my memory id: {{memoryId}}")
        String memoryIdAndList(@V("memoryId") @MemoryId String memoryId, @V("topics") List<String> topics);
    }

    private static final List<String> ACTORS = List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone");

    /** The params of the one guardrail request of an invocation in which the model calls {@code search} once. */
    private static <T> GuardrailRequestParams paramsOf(
            Class<T> assistantClass, boolean withChatMemory, Consumer<T> call) {
        var requests = new CopyOnWriteArrayList<ToolInputGuardrailRequest>();
        var tool = SearchTool.echoing();
        var services =
                AiServices.builder(assistantClass).chatModel(MockChatModel.thatResponds(callingSearchThenAnswering()));
        if (withChatMemory) {
            services.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10));
        }
        ToolKind.LIST.register(services, tool);
        services.toolInputGuardrails(input(request -> {
            requests.add(request);
            return ToolInputGuardrailResult.success();
        }));

        call.accept(services.build());

        assertThat(tool.receivedArguments).containsExactly(ORIGINAL_ARGUMENTS);
        return requests.stream().findFirst().orElseThrow().requestParams();
    }

    private static GuardrailRequestParams paramsWithoutChatMemory(Consumer<Assistant> call) {
        return paramsOf(Assistant.class, false, call);
    }

    private static GuardrailRequestParams memoryParamsOf(Consumer<MemoryAssistant> call) {
        return paramsOf(MemoryAssistant.class, true, call);
    }

    @Test
    void no_parameters() {
        var params = paramsWithoutChatMemory(Assistant::noParameters);

        assertThat(params.userMessageTemplate()).isEqualTo("Tell me a joke");
        assertThat(params.variables()).isEmpty();
    }

    @Test
    void a_memory_id_alone() {
        var params = memoryParamsOf(assistant -> assistant.withMemoryId("memory-id-001"));

        assertThat(params.userMessageTemplate()).isEqualTo("Tell me another joke");
        assertThat(params.variables()).containsExactlyInAnyOrderEntriesOf(Map.of("arg0", "memory-id-001"));
        assertThat(params.invocationContext().chatMemoryId()).isEqualTo("memory-id-001");
    }

    @Test
    void one_parameter_without_a_name_and_no_memory_id() {
        var params = paramsWithoutChatMemory(assistant -> assistant.oneParameterWithoutName("Rambo"));

        assertThat(params.userMessageTemplate()).isEqualTo("Say hi to my friend {{it}}!");
        assertThat(params.variables()).containsExactlyInAnyOrderEntriesOf(Map.of("arg0", "Rambo", "it", "Rambo"));
    }

    @Test
    void a_memory_id_and_one_parameter() {
        var params = memoryParamsOf(assistant -> assistant.memoryIdAndOneParameter("1", "Chuck Norris"));

        assertThat(params.userMessageTemplate()).isEqualTo("Say hi to my friend {{friend}}!");
        assertThat(params.variables())
                .containsExactlyInAnyOrderEntriesOf(Map.of("friend", "Chuck Norris", "arg0", "1"));
    }

    @Test
    void three_parameters_and_no_memory_id() {
        var params = paramsWithoutChatMemory(
                assistant -> assistant.threeParameters(ACTORS.get(0), ACTORS.get(1), ACTORS.get(2)));

        assertThat(params.userMessageTemplate())
                .isEqualTo("Tell me something about {{topic1}}, {{topic2}}, {{topic3}}!");
        assertThat(params.variables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "topic1", "Chuck Norris",
                        "topic2", "Jean-Claude Van Damme",
                        "topic3", "Silvester Stallone"));
    }

    @Test
    void a_list_and_no_memory_id() {
        var params = paramsWithoutChatMemory(assistant -> assistant.list(ACTORS));

        assertThat(params.userMessageTemplate()).isEqualTo("Tell me something about {{it}}!");
        assertThat(params.variables())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("arg0", ACTORS, "it", "[" + String.join(", ", ACTORS) + "]"));
    }

    @Test
    void a_memory_id_and_a_list() {
        var params = memoryParamsOf(assistant -> assistant.memoryIdAndList("memory-id-007", ACTORS));

        assertThat(params.userMessageTemplate())
                .isEqualTo("Tell me something about {{topics}}! This is my memory id: {{memoryId}}");
        assertThat(params.variables())
                .containsExactlyInAnyOrderEntriesOf(Map.of("topics", ACTORS, "memoryId", "memory-id-007"));
    }

    // ---------- chat memory ----------

    @Test
    void the_chat_memory_of_the_invocation_is_the_one_the_guardrail_sees() {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        var seen = new CopyOnWriteArrayList<ChatMemory>();
        var model = MockChatModel.thatResponds(callingSearchThenAnswering());
        var services = AiServices.builder(Assistant.class).chatModel(model).chatMemory(memory);
        ToolKind.LIST.register(services, SearchTool.echoing());
        services.toolInputGuardrails(input(request -> {
            seen.add(request.requestParams().chatMemory());
            return ToolInputGuardrailResult.success();
        }));

        services.build().noParameters();

        assertThat(seen).singleElement().isSameAs(memory);
    }

    @Test
    void without_chat_memory_the_guardrail_still_runs_and_gets_no_memory() {
        var params = paramsWithoutChatMemory(Assistant::noParameters);

        assertThat(params.chatMemory()).isNull();
    }
}
