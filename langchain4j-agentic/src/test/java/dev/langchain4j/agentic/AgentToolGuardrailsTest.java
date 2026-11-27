package dev.langchain4j.agentic;

import static dev.langchain4j.test.mock.MockChatModel.thatResponds;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailExecutor;
import dev.langchain4j.guardrail.ToolOutputGuardrailExecutor.ToolOutputGuardrailExecutorBuilder;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.guardrail.config.ToolOutputGuardrailsConfig;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.ToolGuardrailService;
import dev.langchain4j.service.guardrail.ToolOutputGuardrails;
import dev.langchain4j.spi.guardrail.ToolOutputGuardrailExecutorBuilderFactory;
import dev.langchain4j.test.mock.MockChatModel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AgentToolGuardrailsTest {

    private static final List<Class<?>> RAN = new CopyOnWriteArrayList<>();

    public interface Assistant {
        @dev.langchain4j.service.UserMessage("{{request}}")
        @Agent(name = "assistant", outputKey = "answer")
        String answer(@V("request") String request);
    }

    public static class GlobalInput implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            RAN.add(getClass());
            return success();
        }
    }

    public static class GlobalOutput implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            RAN.add(getClass());
            return success();
        }
    }

    public static class AnnotatedOutput implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            RAN.add(getClass());
            return success();
        }
    }

    static class LookupTools {
        @Tool
        @ToolOutputGuardrails(AnnotatedOutput.class)
        String lookup() {
            return "found";
        }
    }

    private static AiServiceContext buildKeepingContext(
            AgentBuilder<Assistant, ?> builder, AtomicReference<AiServiceContext> context) {
        builder.chatModel(thatResponds("hello")).tools(new LookupTools()).build();
        return context.get();
    }

    private static AgentBuilder<Assistant, ?> capturing(AtomicReference<AiServiceContext> context) {
        return new AgentBuilder<Assistant, AgentBuilder<Assistant, ?>>(Assistant.class) {
            @Override
            protected void build(
                    DefaultAgenticScope agenticScope,
                    AiServiceContext aiServiceContext,
                    AiServices<Assistant> aiServices) {
                context.set(aiServiceContext);
            }
        };
    }

    /** The lookup tool bound to the tool guardrail service of the agent. */
    private record Binding(ToolGuardrailService service, ToolGuardrailService.ToolKey key) {}

    private static Binding bindingOfLookup(AiServiceContext context) {
        var message = UserMessage.from("hi");
        var toolServiceContext =
                context.toolService.createContext(InvocationContext.builder().build(), message, List.of(message));
        return new Binding(
                toolServiceContext.toolGuardrailService().orElseThrow(),
                toolServiceContext.toolGuardrailKeys().get("lookup"));
    }

    private static GuardrailRequestParams requestParams() {
        return GuardrailRequestParams.builder()
                .userMessageTemplate("t")
                .variables(Map.of())
                .invocationContext(InvocationContext.builder().build())
                .build();
    }

    private static ToolExecutionRequest lookupRequest() {
        return ToolExecutionRequest.builder()
                .id("1")
                .name("lookup")
                .arguments("{}")
                .build();
    }

    /**
     * Runs the input guardrails of the tool, and returns the classes of the guardrails that ran, in order.
     */
    private static List<Class<?>> inputChainOf(AiServiceContext context) {
        var binding = bindingOfLookup(context);
        RAN.clear();
        binding.service()
                .executeInputGuardrails(
                        binding.key(),
                        ToolInputGuardrailRequest.builder()
                                .toolExecutionRequest(lookupRequest())
                                .toolSpecification(binding.key().toolSpecification())
                                .toolSource(binding.key().toolSource())
                                .requestParams(requestParams())
                                .build());
        return List.copyOf(RAN);
    }

    /**
     * Runs the output guardrails of the tool, and returns the classes of the guardrails that ran, in order.
     */
    private static List<Class<?>> outputChainOf(AiServiceContext context) {
        var binding = bindingOfLookup(context);
        RAN.clear();
        binding.service()
                .executeOutputGuardrails(
                        binding.key(),
                        ToolOutputGuardrailRequest.builder()
                                .toolExecutionRequest(lookupRequest())
                                .toolSpecification(binding.key().toolSpecification())
                                .toolSource(binding.key().toolSource())
                                .requestParams(requestParams())
                                .resultContents(List.of(TextContent.from("found")))
                                .isError(false)
                                .build());
        return List.copyOf(RAN);
    }

    @Test
    void tool_guardrail_instances_set_on_the_agent_builder_reach_the_tools_and_run_first() {
        var context = new AtomicReference<AiServiceContext>();
        var builder =
                capturing(context).toolInputGuardrails(new GlobalInput()).toolOutputGuardrails(new GlobalOutput());

        var aiServiceContext = buildKeepingContext(builder, context);

        assertThat(inputChainOf(aiServiceContext)).containsExactly(GlobalInput.class);
        assertThat(outputChainOf(aiServiceContext)).containsExactly(GlobalOutput.class, AnnotatedOutput.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tool_guardrail_classes_set_on_the_agent_builder_reach_the_tools() {
        var context = new AtomicReference<AiServiceContext>();
        var builder = capturing(context)
                .toolInputGuardrailClasses(GlobalInput.class)
                .toolOutputGuardrailClasses(GlobalOutput.class);

        var aiServiceContext = buildKeepingContext(builder, context);

        assertThat(inputChainOf(aiServiceContext)).containsExactly(GlobalInput.class);
        assertThat(outputChainOf(aiServiceContext)).containsExactly(GlobalOutput.class, AnnotatedOutput.class);
    }

    static class TransferTools {

        final List<Integer> amounts = new CopyOnWriteArrayList<>();

        @Tool
        String transfer(@P(name = "amount") int amount) {
            amounts.add(amount);
            return "Transfer done";
        }
    }

    @Test
    void the_tool_input_guardrails_config_set_on_the_agent_builder_applies_to_the_results_the_llm_gets() {
        var model = MockChatModel.thatResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name("transfer")
                        .arguments("{\"amount\":10000}")
                        .build()),
                AiMessage.from("done"));
        var tools = new TransferTools();
        ToolInputGuardrail capping = request -> ToolInputGuardrailResult.successWithArguments(Map.of("amount", 1000));
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .toolInputGuardrails(capping)
                // without this config, the LLM would get a note that the arguments were changed
                .toolInputGuardrailsConfig(ToolInputGuardrailsConfig.builder()
                        .argumentsRewriteNoteEnabled(false)
                        .build())
                .build();

        assertThat(assistant.answer("send 10000")).isEqualTo("done");

        assertThat(tools.amounts).containsExactly(1000);
        assertThat(model.requests().get(1).messages())
                .filteredOn(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .singleElement()
                .returns("Transfer done", ToolExecutionResultMessage::text);
    }

    @Test
    void the_tool_output_guardrails_config_set_on_the_agent_builder_reaches_the_output_guardrail_executors() {
        var context = new AtomicReference<AiServiceContext>();
        var config = ToolOutputGuardrailsConfig.builder().build();
        var builder =
                capturing(context).toolOutputGuardrailsConfig(config).toolOutputGuardrails(new GlobalOutput());

        var aiServiceContext = buildKeepingContext(builder, context);

        assertThat(outputChainOf(aiServiceContext)).containsExactly(GlobalOutput.class, AnnotatedOutput.class);
        assertThat(RecordingToolOutputGuardrailExecutorBuilderFactory.CONFIGS)
                .as("the configs the output guardrail executors were built with")
                .anyMatch(builtWith -> builtWith == config);
    }

    /**
     * Registered in {@code META-INF/services}, so every tool output guardrail executor of this module's tests is built
     * by the default builder, which records the config it was given.
     */
    public static class RecordingToolOutputGuardrailExecutorBuilderFactory
            implements ToolOutputGuardrailExecutorBuilderFactory {

        static final List<ToolOutputGuardrailsConfig> CONFIGS = new CopyOnWriteArrayList<>();

        @Override
        public ToolOutputGuardrailExecutorBuilder getBuilder() {
            return new ToolOutputGuardrailExecutorBuilder() {
                @Override
                public ToolOutputGuardrailExecutor build() {
                    CONFIGS.add(config());
                    return super.build();
                }
            };
        }
    }

    @Test
    void without_tool_guardrails_on_the_builder_only_the_annotations_apply() {
        var context = new AtomicReference<AiServiceContext>();

        var aiServiceContext = buildKeepingContext(capturing(context), context);

        assertThat(inputChainOf(aiServiceContext)).isEmpty();
        assertThat(outputChainOf(aiServiceContext)).containsExactly(AnnotatedOutput.class);
    }
}
