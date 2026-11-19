package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailExecutor;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailExecutor;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.guardrail.config.ToolOutputGuardrailsConfig;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolGuardrailBinding;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.service.tool.ToolServiceContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolGuardrailResolutionTest {

    private static final InvocationContext CONTEXT = InvocationContext.builder().build();
    private static final UserMessage USER_MESSAGE = UserMessage.from("hi");
    private static final AtomicReference<String> SEEN_BY_CLASS_LEVEL = new AtomicReference<>();

    @BeforeEach
    void resetSeenText() {
        SEEN_BY_CLASS_LEVEL.set(null);
    }

    // --- guardrails -----------------------------------------------------------------------------

    public static class InClass implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return success();
        }
    }

    public static class InMethod implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return success();
        }
    }

    public static class InGlobal implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return success();
        }
    }

    public static class OutClass implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            SEEN_BY_CLASS_LEVEL.set(request.text());
            return success();
        }
    }

    public static class OutMethod implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            return success();
        }
    }

    public static class OutGlobal implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            return successWith(request.text() + "[global]");
        }
    }

    public static class OutOfTool implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            return success();
        }
    }

    public static class OutRejecting implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            return failure("rejected");
        }

        @Override
        public CompletableFuture<ToolOutputGuardrailResult> validateAsync(ToolOutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    public static class AiServiceInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    // --- tools ----------------------------------------------------------------------------------

    static class Plain {
        @Tool
        String plain() {
            return "plain";
        }
    }

    @ToolInputGuardrails(InClass.class)
    @ToolOutputGuardrails(OutClass.class)
    static class ClassOnly {
        @Tool
        String classOnly() {
            return "classOnly";
        }
    }

    static class MethodOnly {
        @Tool
        @ToolInputGuardrails(InMethod.class)
        @ToolOutputGuardrails(OutMethod.class)
        String methodOnly() {
            return "methodOnly";
        }
    }

    @ToolOutputGuardrails(OutClass.class)
    static class WebTools {
        @Tool
        @ToolOutputGuardrails(OutMethod.class)
        String fetch() {
            return "fetch";
        }

        @Tool
        String other() {
            return "other";
        }
    }

    @ToolOutputGuardrails({OutClass.class, OutClass.class})
    static class Twice {
        @Tool
        @ToolOutputGuardrails(OutClass.class)
        String twice() {
            return "twice";
        }
    }

    public static class PublicTools {
        @Tool
        @ToolOutputGuardrails(OutMethod.class)
        public String publicTool() {
            return "publicTool";
        }

        @Tool
        @ToolOutputGuardrails(OutMethod.class)
        private String privateTool() {
            return "privateTool";
        }
    }

    @InputGuardrails(AiServiceInputGuardrail.class)
    @ToolInputGuardrails(InClass.class)
    static class AiServiceUsedAsTool {
        @Tool
        String proxy() {
            return "proxy";
        }
    }

    @ToolOutputGuardrails(OutClass.class)
    interface AnnotatedInterface {}

    static class InheritsClassAnnotation implements AnnotatedInterface {
        @Tool
        String inherited() {
            return "inherited";
        }
    }

    @ToolOutputGuardrails(OutOfTool.class)
    static class ParentWithAnnotation {}

    static class ChildOfAnnotatedParent extends ParentWithAnnotation {
        @Tool
        String child() {
            return "child";
        }
    }

    @ToolOutputGuardrails(OutClass.class)
    static class AnnotatedChildOfAnnotatedParent extends ParentWithAnnotation implements AnnotatedInterface {
        @Tool
        String annotatedChild() {
            return "annotatedChild";
        }
    }

    // --- helpers --------------------------------------------------------------------------------

    interface Assistant {}

    /**
     * A {@link ToolService} with tools, and the builder of the tool guardrail service it is given on first use (like
     * {@code AiServices}, which gives it the service when the AI service is built).
     */
    private static final class Setup {
        private final ToolService toolService = new ToolService();
        private final ToolGuardrailService.Builder guardrails = ToolGuardrailService.builder(Assistant.class);
        private boolean built;

        Setup tools(Object... objectsWithTools) {
            this.toolService.tools(List.of(objectsWithTools));
            return this;
        }

        ToolService toolService() {
            return this.toolService;
        }

        ToolGuardrailService.Builder guardrails() {
            return this.guardrails;
        }

        void rebuild() {
            this.built = false;
        }

        ToolServiceContext context() {
            if (!this.built) {
                this.toolService.toolGuardrailService(this.guardrails.build());
                this.built = true;
            }

            return this.toolService.createContext(CONTEXT, USER_MESSAGE, List.of(USER_MESSAGE));
        }

        ToolGuardrailBinding binding(String toolName) {
            return context().toolGuardrailBindings().get(toolName);
        }

        Optional<ToolInputGuardrailExecutor> inputExecutor(String toolName) {
            var binding = binding(toolName);
            return ((DefaultToolGuardrailService) binding.service()).inputExecutor(binding.key());
        }

        Optional<ToolOutputGuardrailExecutor> outputExecutor(String toolName) {
            var binding = binding(toolName);
            return ((DefaultToolGuardrailService) binding.service()).outputExecutor(binding.key());
        }

        List<Class<?>> inputChain(String toolName) {
            return inputExecutor(toolName)
                    .map(executor -> classesOf(executor.guardrails()))
                    .orElseGet(List::of);
        }

        List<Class<?>> outputChain(String toolName) {
            return outputExecutor(toolName)
                    .map(executor -> classesOf(executor.guardrails()))
                    .orElseGet(List::of);
        }

        private static List<Class<?>> classesOf(List<?> guardrails) {
            return guardrails.stream().<Class<?>>map(Object::getClass).toList();
        }
    }

    private static Setup setupWith(Object... tools) {
        return new Setup().tools(tools);
    }

    private static ToolOutputGuardrailRequest outputRequest(String toolName, String result) {
        return ToolOutputGuardrailRequest.builder()
                .toolExecutionRequest(ToolExecutionRequest.builder()
                        .id("1")
                        .name(toolName)
                        .arguments("{}")
                        .build())
                .requestParams(GuardrailRequestParams.builder()
                        .userMessageTemplate("t")
                        .variables(Map.of())
                        .invocationContext(CONTEXT)
                        .build())
                .resultContents(List.of(TextContent.from(result)))
                .isError(false)
                .build();
    }

    // --- resolution matrix ----------------------------------------------------------------------

    @Test
    void no_guardrails_means_no_executor_and_a_success() {
        var setup = setupWith(new Plain());
        var binding = setup.binding("plain");

        assertThat(setup.inputExecutor("plain")).isEmpty();
        assertThat(setup.outputExecutor("plain")).isEmpty();
        assertThat(binding.service().hasInputGuardrails(binding.key())).isFalse();
        assertThat(binding.service().hasOutputGuardrails(binding.key())).isFalse();
        assertThat(binding.service()
                        .executeOutputGuardrails(binding.key(), outputRequest("plain", "result"))
                        .isSuccess())
                .isTrue();
    }

    @Test
    void the_service_tells_which_guardrails_a_bound_tool_has_and_the_binding_carries_its_specification() {
        var setup = setupWith(new WebTools(), new ClassOnly());
        var fetch = setup.binding("fetch");
        var classOnly = setup.binding("classOnly");

        assertThat(fetch)
                .returns("fetch", binding -> binding.toolSpecification().name())
                .returns(false, binding -> binding.service().hasInputGuardrails(binding.key()))
                .returns(true, binding -> binding.service().hasOutputGuardrails(binding.key()));
        assertThat(classOnly)
                .returns("classOnly", binding -> binding.toolSpecification().name())
                .returns(true, binding -> binding.service().hasInputGuardrails(binding.key()))
                .returns(true, binding -> binding.service().hasOutputGuardrails(binding.key()));
    }

    @Test
    void without_a_tool_guardrail_service_no_tool_is_bound() {
        var toolService = new ToolService();
        toolService.tools(List.of(new ClassOnly()));

        assertThat(toolService.toolGuardrailService()).isEmpty();
        assertThat(toolService
                        .createContext(CONTEXT, USER_MESSAGE, List.of(USER_MESSAGE))
                        .toolGuardrailBindings())
                .isEmpty();
    }

    @Test
    void tools_registered_before_and_after_the_service_is_set_are_bound() {
        var setup = setupWith(new ClassOnly());
        setup.context();
        setup.tools(new MethodOnly());

        assertThat(setup.outputChain("classOnly")).containsExactly(OutClass.class);
        assertThat(setup.outputChain("methodOnly")).containsExactly(OutMethod.class);
    }

    @Test
    void builder_only_applies_to_every_tool() {
        var setup = setupWith(new Plain(), new MethodOnly());
        setup.guardrails().outputGuardrails(new OutGlobal());

        assertThat(setup.outputChain("plain")).containsExactly(OutGlobal.class);
        assertThat(setup.outputChain("methodOnly")).containsExactly(OutGlobal.class, OutMethod.class);
    }

    @Test
    void builder_guardrail_classes_follow_the_instances() {
        var setup = setupWith(new Plain());
        setup.guardrails().inputGuardrailClasses(InClass.class).inputGuardrails(new InGlobal());

        assertThat(setup.inputChain("plain")).containsExactly(InGlobal.class, InClass.class);
    }

    @Test
    void class_only() {
        var setup = setupWith(new ClassOnly());

        assertThat(setup.inputChain("classOnly")).containsExactly(InClass.class);
        assertThat(setup.outputChain("classOnly")).containsExactly(OutClass.class);
    }

    @Test
    void method_only() {
        var setup = setupWith(new MethodOnly());

        assertThat(setup.inputChain("methodOnly")).containsExactly(InMethod.class);
        assertThat(setup.outputChain("methodOnly")).containsExactly(OutMethod.class);
    }

    @Test
    void class_then_method_and_other_methods_only_get_the_class_one() {
        var setup = setupWith(new WebTools());

        assertThat(setup.outputChain("fetch")).containsExactly(OutClass.class, OutMethod.class);
        assertThat(setup.outputChain("other")).containsExactly(OutClass.class);
    }

    @Test
    void builder_then_class_then_method() {
        var setup = setupWith(new WebTools());
        setup.guardrails().outputGuardrails(new OutGlobal());

        assertThat(setup.outputChain("fetch")).containsExactly(OutGlobal.class, OutClass.class, OutMethod.class);
    }

    @Test
    void builder_then_method_annotation() {
        var setup = setupWith(new MethodOnly());
        setup.guardrails().inputGuardrails(new InGlobal());

        assertThat(setup.inputChain("methodOnly")).containsExactly(InGlobal.class, InMethod.class);
    }

    @Test
    void builder_then_ai_service_tool_guardrails() {
        var setup = new Setup();
        setup.toolService()
                .tools(List.<AiServiceTool>of(AiServiceTool.builder()
                        .toolSpecification(
                                ToolSpecification.builder().name("custom").build())
                        .toolExecutor((request, memoryId) -> "ok")
                        .toolOutputGuardrails(new OutMethod())
                        .toolInputGuardrails(List.of(new InMethod()))
                        .build()));
        setup.guardrails().outputGuardrails(new OutGlobal()).inputGuardrails(new InGlobal());

        assertThat(setup.outputChain("custom")).containsExactly(OutGlobal.class, OutMethod.class);
        assertThat(setup.inputChain("custom")).containsExactly(InGlobal.class, InMethod.class);
    }

    @Test
    void builder_then_class_then_method_annotation_then_ai_service_tool_guardrails() throws Exception {
        var setup = new Setup();
        setup.toolService()
                .tools(List.<AiServiceTool>of(
                        AiServiceTool.builder()
                                .toolSpecification(ToolSpecification.builder()
                                        .name("fetch")
                                        .build())
                                .toolExecutor(new DefaultToolExecutor(
                                        new WebTools(), WebTools.class.getDeclaredMethod("fetch")))
                                .toolOutputGuardrails(new OutOfTool())
                                .build(),
                        AiServiceTool.builder()
                                .toolSpecification(ToolSpecification.builder()
                                        .name("methodOnly")
                                        .build())
                                .toolExecutor(new DefaultToolExecutor(
                                        new MethodOnly(), MethodOnly.class.getDeclaredMethod("methodOnly")))
                                .toolInputGuardrails(new InClass())
                                .build()));
        setup.guardrails().outputGuardrails(new OutGlobal()).inputGuardrails(new InGlobal());

        assertThat(setup.outputChain("fetch"))
                .containsExactly(OutGlobal.class, OutClass.class, OutMethod.class, OutOfTool.class);
        assertThat(setup.inputChain("methodOnly")).containsExactly(InGlobal.class, InMethod.class, InClass.class);
    }

    @Test
    void input_and_output_are_resolved_independently() {
        var setup = setupWith(new Plain());
        setup.guardrails().outputGuardrails(new OutGlobal());

        assertThat(setup.inputChain("plain")).isEmpty();
        assertThat(setup.outputChain("plain")).containsExactly(OutGlobal.class);
    }

    @Test
    void global_guardrail_and_method_annotation_both_run_global_first() {
        // @PSchmitz-Valckenberg's scenario (discussion #7030): a format check on the fetching tool, plus a global
        // injection check on the builder
        var setup = setupWith(new MethodOnly());
        setup.guardrails().outputGuardrailClasses(OutGlobal.class);

        assertThat(setup.outputChain("methodOnly")).containsExactly(OutGlobal.class, OutMethod.class);
    }

    // --- deduplication --------------------------------------------------------------------------

    @Test
    void the_same_guardrail_class_at_several_levels_runs_once_in_its_first_position() {
        var setup = setupWith(new Twice());
        setup.guardrails().outputGuardrailClasses(OutMethod.class, OutClass.class);

        assertThat(setup.outputChain("twice")).containsExactly(OutMethod.class, OutClass.class);
    }

    @Test
    void two_distinct_instances_of_one_class_both_run() {
        var setup = setupWith(new Plain());
        var first = new OutMethod();
        var second = new OutMethod();
        setup.guardrails().outputGuardrails(first, second);

        assertThat(setup.outputExecutor("plain"))
                .get()
                .extracting(ToolOutputGuardrailExecutor::guardrails)
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .containsExactly(first, second);
    }

    @Test
    void the_same_instance_twice_runs_once() {
        var setup = setupWith(new Plain());
        var instance = new OutMethod();
        setup.guardrails().outputGuardrails(instance, instance);

        assertThat(setup.outputExecutor("plain"))
                .get()
                .extracting(ToolOutputGuardrailExecutor::guardrails)
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .containsExactly(instance);
    }

    @Test
    void a_builder_instance_and_an_annotation_class_naming_the_same_class_both_run() {
        var setup = setupWith(new ClassOnly());
        setup.guardrails().outputGuardrails(new OutClass());

        assertThat(setup.outputChain("classOnly")).containsExactly(OutClass.class, OutClass.class);
    }

    @Test
    void null_or_empty_global_lists_mean_no_global_guardrails() {
        var setup = setupWith(new ClassOnly());
        setup.guardrails()
                .outputGuardrails((List<ToolOutputGuardrail>) null)
                .inputGuardrails(List.of())
                .outputGuardrailClasses((List<Class<? extends ToolOutputGuardrail>>) null);

        assertThat(setup.outputChain("classOnly")).containsExactly(OutClass.class);
        assertThat(setup.inputChain("classOnly")).containsExactly(InClass.class);
    }

    // --- annotations ----------------------------------------------------------------------------

    @Test
    void ai_service_guardrail_annotations_are_not_tool_guardrails() {
        var setup = setupWith(new AiServiceUsedAsTool());

        assertThat(setup.inputChain("proxy")).containsExactly(InClass.class);
    }

    @Test
    void method_annotations_are_found_on_public_methods_and_on_methods_the_factory_does_not_list() {
        var setup = setupWith(new PublicTools());

        assertThat(setup.outputChain("publicTool")).containsExactly(OutMethod.class);
        assertThat(setup.outputChain("privateTool")).containsExactly(OutMethod.class);
    }

    @Test
    void class_annotation_is_found_on_interfaces_and_superclasses() {
        var setup = setupWith(new InheritsClassAnnotation(), new ChildOfAnnotatedParent());

        assertThat(setup.outputChain("inherited")).containsExactly(OutClass.class);
        assertThat(setup.outputChain("child")).containsExactly(OutOfTool.class);
    }

    @Test
    void a_class_annotation_on_a_subclass_hides_the_ones_of_its_parents() {
        var setup = setupWith(new AnnotatedChildOfAnnotatedParent());

        assertThat(setup.outputChain("annotatedChild")).containsExactly(OutClass.class);
    }

    @Test
    void map_tools_backed_by_a_default_tool_executor_keep_their_method_annotations() throws Exception {
        var method = MethodOnly.class.getDeclaredMethod("methodOnly");
        var setup = new Setup();
        setup.toolService()
                .tools(Map.of(
                        ToolSpecification.builder().name("fromMap").build(),
                        new DefaultToolExecutor(new MethodOnly(), method)));

        assertThat(setup.outputChain("fromMap")).containsExactly(OutMethod.class);
        assertThat(setup.inputChain("fromMap")).containsExactly(InMethod.class);
        assertThat(setup.binding("fromMap").toolSource()).isEqualTo(ToolSource.programmatic());
    }

    // --- executors ------------------------------------------------------------------------------

    @Test
    void executor_is_created_once_per_tool() {
        var setup = setupWith(new ClassOnly());
        var binding = setup.binding("classOnly");
        var executor = setup.outputExecutor("classOnly").orElseThrow();

        assertThat(setup.binding("classOnly")).isSameAs(binding);
        assertThat(setup.outputExecutor("classOnly")).containsSame(executor);
    }

    @Test
    void tools_are_bound_again_when_the_service_changes() {
        var setup = setupWith(new Plain());
        setup.guardrails().outputGuardrails(new OutGlobal());
        assertThat(setup.outputChain("plain")).containsExactly(OutGlobal.class);

        setup.guardrails().outputGuardrails(List.of());
        setup.rebuild();

        assertThat(setup.outputChain("plain")).isEmpty();
    }

    @Test
    void a_rewrite_of_a_level_is_what_the_next_level_sees() {
        var setup = setupWith(new ClassOnly());
        setup.guardrails().outputGuardrails(new OutGlobal());
        var binding = setup.binding("classOnly");

        var result = binding.service().executeOutputGuardrails(binding.key(), outputRequest("classOnly", "result"));

        assertThat(SEEN_BY_CLASS_LEVEL.get()).isEqualTo("result[global]");
        assertThat(result.successfulText()).isEqualTo("result[global]");
    }

    @Test
    void a_non_fatal_failure_is_returned_not_thrown_blocking_and_non_blocking() {
        var setup = setupWith(new Plain());
        setup.guardrails().outputGuardrails(new OutRejecting());
        var binding = setup.binding("plain");

        assertThat(binding.service().executeOutputGuardrails(binding.key(), outputRequest("plain", "result")))
                .extracting(ToolOutputGuardrailResult::isSuccess, ToolOutputGuardrailResult::failureMessage)
                .containsExactly(false, "rejected");
        assertThat(binding.service().executeOutputGuardrailsAsync(binding.key(), outputRequest("plain", "result")))
                .succeedsWithin(Duration.ofSeconds(5))
                .extracting(ToolOutputGuardrailResult::isSuccess)
                .isEqualTo(false);
    }

    @Test
    void the_global_configs_are_given_to_the_executors_of_every_tool() {
        var setup = setupWith(new ClassOnly());
        var inputConfig = ToolInputGuardrailsConfig.builder().build();
        var outputConfig = ToolOutputGuardrailsConfig.builder().build();
        setup.guardrails().inputGuardrailsConfig(inputConfig).outputGuardrailsConfig(outputConfig);

        assertThat(setup.inputExecutor("classOnly"))
                .get()
                .extracting(ToolInputGuardrailExecutor::config)
                .isSameAs(inputConfig);
        assertThat(setup.outputExecutor("classOnly"))
                .get()
                .extracting(ToolOutputGuardrailExecutor::config)
                .isSameAs(outputConfig);
    }

    @Test
    void the_key_names_the_tool_it_was_bound_for() {
        var setup = setupWith(new Plain(), new ClassOnly());

        assertThat(setup.binding("plain").key().toolName()).isEqualTo("plain");
        assertThat(setup.binding("classOnly").key().toolName()).isEqualTo("classOnly");
    }

    @Test
    void the_target_class_is_the_ai_service_class() {
        assertThat(ToolGuardrailService.builder(Assistant.class).build().targetClass())
                .isEqualTo(Assistant.class);
    }

    @Test
    void a_key_the_service_did_not_create_has_no_guardrails() {
        var service = ToolGuardrailService.builder(Assistant.class)
                .outputGuardrails(new OutGlobal())
                .build();

        ToolGuardrailService.ToolKey foreignKey = () -> "plain";

        assertThat(service.hasOutputGuardrails(foreignKey)).isFalse();
        assertThat(service.hasOutputGuardrails("not a key")).isFalse();
        assertThat(service.hasOutputGuardrails(null)).isFalse();
        assertThat(service.executeOutputGuardrails(foreignKey, outputRequest("plain", "result"))
                        .isSuccess())
                .isTrue();
        assertThat(service.executeOutputGuardrails("not a key", outputRequest("plain", "result"))
                        .isSuccess())
                .isTrue();
    }

    // --- tool source ----------------------------------------------------------------------------

    @Test
    void local_tools_have_a_local_source() throws Exception {
        var setup = setupWith(new Plain());

        assertThat(setup.binding("plain").toolSource())
                .isEqualTo(ToolSource.local(Plain.class.getDeclaredMethod("plain")));
    }

    @Test
    void programmatic_tools_have_a_programmatic_source() {
        var setup = new Setup();
        setup.toolService()
                .tools(Map.of(ToolSpecification.builder().name("mapped").build(), (ToolExecutor)
                        (request, memoryId) -> "ok"));

        assertThat(setup.binding("mapped").toolSource()).isEqualTo(ToolSource.programmatic());
    }

    @Test
    void ai_service_tool_source_is_used_and_defaults_to_programmatic() {
        var setup = new Setup();
        setup.toolService()
                .tools(List.<AiServiceTool>of(
                        AiServiceTool.builder()
                                .toolSpecification(ToolSpecification.builder()
                                        .name("mcpLike")
                                        .build())
                                .toolExecutor((request, memoryId) -> "ok")
                                .toolSource(ToolSource.mcp("server-1"))
                                .build(),
                        AiServiceTool.builder()
                                .toolSpecification(ToolSpecification.builder()
                                        .name("noSource")
                                        .build())
                                .toolExecutor((request, memoryId) -> "ok")
                                .build()));

        assertThat(setup.binding("mcpLike").toolSource()).isEqualTo(ToolSource.mcp("server-1"));
        assertThat(setup.binding("noSource").toolSource()).isEqualTo(ToolSource.programmatic());
    }

    @Test
    void tools_of_a_tool_provider_get_their_source_and_guardrails() {
        var setup = new Setup();
        setup.toolService()
                .toolProvider(request -> ToolProviderResult.builder()
                        .add(AiServiceTool.builder()
                                .toolSpecification(ToolSpecification.builder()
                                        .name("provided")
                                        .build())
                                .toolExecutor((toolRequest, memoryId) -> "ok")
                                .toolSource(ToolSource.mcp("server-2"))
                                .toolOutputGuardrails(new OutMethod())
                                .build())
                        .build());
        setup.guardrails().outputGuardrails(new OutGlobal());

        assertThat(setup.binding("provided").toolSource()).isEqualTo(ToolSource.mcp("server-2"));
        assertThat(setup.outputChain("provided")).containsExactly(OutGlobal.class, OutMethod.class);
    }

    @Test
    void tools_without_their_own_guardrails_share_the_executors_of_the_global_guardrails_across_invocations() {
        var setup = setupWith(new Plain());
        setup.toolService()
                .toolProvider(request -> ToolProviderResult.builder()
                        .add(providedTool("first"))
                        .add(providedTool("second"))
                        .build());
        setup.guardrails().inputGuardrails(new InGlobal()).outputGuardrails(new OutGlobal());

        // every call of binding(..) creates the context of a new invocation, which provides the tools anew
        var sharedInput = setup.inputExecutor("first").orElseThrow();
        var sharedOutput = setup.outputExecutor("first").orElseThrow();

        assertThat(List.of(setup.inputExecutor("first"), setup.inputExecutor("second"), setup.inputExecutor("plain")))
                .allSatisfy(executor -> assertThat(executor).containsSame(sharedInput));
        assertThat(List.of(
                        setup.outputExecutor("first"), setup.outputExecutor("second"), setup.outputExecutor("plain")))
                .allSatisfy(executor -> assertThat(executor).containsSame(sharedOutput));
        assertThat(setup.inputChain("first")).containsExactly(InGlobal.class);
        assertThat(setup.outputChain("first")).containsExactly(OutGlobal.class);
    }

    @Test
    void a_tool_with_its_own_guardrails_does_not_share_the_executor_of_the_global_guardrails() {
        var setup = setupWith(new Plain(), new MethodOnly());
        setup.guardrails().inputGuardrails(new InGlobal());

        assertThat(setup.inputExecutor("methodOnly"))
                .isPresent()
                .get()
                .isNotSameAs(setup.inputExecutor("plain").orElseThrow());
        assertThat(setup.inputChain("methodOnly")).containsExactly(InGlobal.class, InMethod.class);
        assertThat(setup.inputChain("plain")).containsExactly(InGlobal.class);
    }

    @Test
    void the_shared_executor_of_the_global_guardrails_has_no_duplicate_classes() {
        var setup = setupWith(new Plain());
        setup.guardrails().inputGuardrailClasses(InClass.class, InClass.class);

        assertThat(setup.inputChain("plain")).containsExactly(InClass.class);
    }

    private static AiServiceTool providedTool(String name) {
        return AiServiceTool.builder()
                .toolSpecification(ToolSpecification.builder().name(name).build())
                .toolExecutor((toolRequest, memoryId) -> "ok")
                .toolSource(ToolSource.mcp("server"))
                .build();
    }

    @Test
    void dynamic_tool_provider_tools_are_bound_when_refreshed() {
        var setup = new Setup();
        setup.toolService().toolProvider(new ToolProvider() {
            @Override
            public ToolProviderResult provideTools(ToolProviderRequest request) {
                return ToolProviderResult.builder()
                        .add(AiServiceTool.builder()
                                .toolSpecification(ToolSpecification.builder()
                                        .name("dynamic")
                                        .build())
                                .toolExecutor((toolRequest, memoryId) -> "ok")
                                .toolSource(ToolSource.mcp("server-3"))
                                .toolOutputGuardrails(new OutMethod())
                                .build())
                        .build();
            }

            @Override
            public boolean isDynamic() {
                return true;
            }
        });
        setup.guardrails().outputGuardrails(new OutGlobal());

        assertThat(setup.binding("dynamic").toolSource()).isEqualTo(ToolSource.mcp("server-3"));
        assertThat(setup.outputChain("dynamic")).containsExactly(OutGlobal.class, OutMethod.class);
    }
}
