package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.IllegalConfigurationException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Validates {@link ToolService#createContext(InvocationContext, UserMessage, List, List)} — the
 * overload that merges a caller-supplied list of method-scoped <i>base tools</i> with the
 * service's configured static tools and providers.
 *
 * <p>This overload is the integration point used by downstream framework integrators
 * (e.g. {@code quarkus-langchain4j}) that resolve additional method-scoped tools per AI service
 * method invocation and need them to participate in the same provider semantics as
 * AI-service-level tools.
 */
class CreateContextWithBaseToolsTest {

    private static final InvocationContext INVOCATION_CONTEXT = InvocationContext.builder()
            .interfaceName("TestService")
            .methodName("chat")
            .userMessage(UserMessage.from("hi"))
            .chatMemoryId("default")
            .timestampNow()
            .build();

    private static final UserMessage USER_MESSAGE = UserMessage.from("hi");
    private static final List<ChatMessage> MESSAGES = List.of(USER_MESSAGE);

    private static AiServiceTool aiServiceTool(String name, String description) {
        return AiServiceTool.builder()
                .toolSpecification(ToolSpecification.builder()
                        .name(name)
                        .description(description)
                        .build())
                .toolExecutor((ToolExecutionRequest req, Object memId) -> "result-" + name)
                .build();
    }

    @Test
    void merges_baseTools_with_static_tools() {
        ToolService toolService = new ToolService();
        AiServiceTool staticTool = aiServiceTool("static", "static tool");
        List<AiServiceTool> staticTools = List.of(staticTool);
        toolService.tools(staticTools);

        AiServiceTool baseTool = aiServiceTool("scoped", "method-scoped tool");

        ToolServiceContext context =
                toolService.createContext(INVOCATION_CONTEXT, USER_MESSAGE, new ArrayList<>(MESSAGES), List.of(baseTool));

        assertThat(context.effectiveTools())
                .extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("static", "scoped");
        assertThat(context.availableTools())
                .extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("static", "scoped");
        assertThat(context.toolExecutors()).containsKeys("static", "scoped");
        assertThat(context.toolExecutors().get("scoped")).isSameAs(baseTool.toolExecutor());
        assertThat(context.toolExecutors().get("static")).isSameAs(staticTool.toolExecutor());
    }

    @Test
    void merges_baseTools_with_dynamic_provider_tools() {
        ToolService toolService = new ToolService();
        AiServiceTool providerTool = aiServiceTool("from-provider", "provider tool");
        toolService.toolProvider(request -> ToolProviderResult.builder()
                .add(providerTool)
                .build());

        AiServiceTool baseTool = aiServiceTool("scoped", "method-scoped tool");

        ToolServiceContext context =
                toolService.createContext(INVOCATION_CONTEXT, USER_MESSAGE, new ArrayList<>(MESSAGES), List.of(baseTool));

        assertThat(context.effectiveTools())
                .extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("from-provider", "scoped");
        assertThat(context.toolExecutors()).containsKeys("from-provider", "scoped");
        assertThat(context.toolExecutors().get("scoped")).isSameAs(baseTool.toolExecutor());
        assertThat(context.toolExecutors().get("from-provider")).isSameAs(providerTool.toolExecutor());
    }

    @Test
    void collision_with_static_tool_throws_illegal_configuration() {
        ToolService toolService = new ToolService();
        List<AiServiceTool> firstTools = List.of(aiServiceTool("dup", "first"));
        toolService.tools(firstTools);

        AiServiceTool conflicting = aiServiceTool("dup", "second");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> toolService.createContext(
                        INVOCATION_CONTEXT, USER_MESSAGE, new ArrayList<>(MESSAGES), List.of(conflicting)))
                .withMessageContaining("Duplicated definition for tool: dup");
    }

    @Test
    void collision_with_provider_supplied_tool_throws_illegal_configuration() {
        ToolService toolService = new ToolService();
        // baseTools are merged BEFORE the provider runs. The provider returns a tool whose name
        // collides with one of the baseTools — this collision must be detected.
        toolService.toolProvider(request -> ToolProviderResult.builder()
                .add(aiServiceTool("dup", "from provider"))
                .build());

        AiServiceTool baseTool = aiServiceTool("dup", "from base");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> toolService.createContext(
                        INVOCATION_CONTEXT, USER_MESSAGE, new ArrayList<>(MESSAGES), List.of(baseTool)))
                .withMessageContaining("Duplicated definition for tool: dup");
    }

    @Test
    void empty_baseTools_behaves_identically_to_no_baseTools_overload() {
        ToolService toolService = new ToolService();
        AiServiceTool staticTool = aiServiceTool("static", "static tool");
        List<AiServiceTool> staticTools = List.of(staticTool);
        toolService.tools(staticTools);

        ToolServiceContext withEmpty = toolService.createContext(
                INVOCATION_CONTEXT, USER_MESSAGE, new ArrayList<>(MESSAGES), List.of());
        ToolServiceContext withoutBase =
                toolService.createContext(INVOCATION_CONTEXT, USER_MESSAGE, new ArrayList<>(MESSAGES));

        // Same set of tools, executors, return behaviors — the empty-list path must not allocate
        // a divergent merged collection.
        assertThat(withEmpty.effectiveTools())
                .extracting(ToolSpecification::name)
                .containsExactlyElementsOf(
                        withoutBase.effectiveTools().stream().map(ToolSpecification::name).toList());
        assertThat(withEmpty.toolExecutors().keySet()).isEqualTo(withoutBase.toolExecutors().keySet());
        assertThat(withEmpty.toolExecutors().get("static")).isSameAs(withoutBase.toolExecutors().get("static"));
    }

    @Test
    void null_baseTools_behaves_identically_to_no_baseTools_overload() {
        ToolService toolService = new ToolService();
        AiServiceTool staticTool = aiServiceTool("static", "static tool");
        List<AiServiceTool> staticTools = List.of(staticTool);
        toolService.tools(staticTools);

        ToolServiceContext withNull =
                toolService.createContext(INVOCATION_CONTEXT, USER_MESSAGE, new ArrayList<>(MESSAGES), null);
        ToolServiceContext withoutBase =
                toolService.createContext(INVOCATION_CONTEXT, USER_MESSAGE, new ArrayList<>(MESSAGES));

        assertThat(withNull.effectiveTools())
                .extracting(ToolSpecification::name)
                .containsExactlyElementsOf(
                        withoutBase.effectiveTools().stream().map(ToolSpecification::name).toList());
        assertThat(withNull.toolExecutors().keySet()).isEqualTo(withoutBase.toolExecutors().keySet());
        assertThat(withNull.toolExecutors().get("static")).isSameAs(withoutBase.toolExecutors().get("static"));
    }

    @Test
    void no_static_no_providers_with_baseTools_only_returns_merged_context() {
        // Ensures the "no providers, no static tools, but baseTools present" branch in
        // createContextFromStaticToolsAndProviders builds a non-Empty context with only the
        // baseTools merged in. This is the framework-integration happy path when the service
        // has no AI-service-level tools and method-scoped tools are the only ones.
        ToolService toolService = new ToolService();
        AiServiceTool baseTool = aiServiceTool("scoped", "method-scoped tool");

        ToolServiceContext context =
                toolService.createContext(INVOCATION_CONTEXT, USER_MESSAGE, new ArrayList<>(MESSAGES), List.of(baseTool));

        assertThat(context).isNotSameAs(ToolServiceContext.Empty.INSTANCE);
        assertThat(context.effectiveTools()).extracting(ToolSpecification::name).containsExactly("scoped");
        assertThat(context.toolExecutors()).containsOnlyKeys("scoped");
        assertThat(context.toolExecutors().get("scoped")).isSameAs(baseTool.toolExecutor());
    }

    @Test
    void no_static_no_providers_no_baseTools_returns_empty_context() {
        // Parity guard: when nothing is configured AND no baseTools are supplied, we should
        // still get back the singleton Empty context (not a freshly allocated empty one).
        ToolService toolService = new ToolService();

        ToolServiceContext withNull =
                toolService.createContext(INVOCATION_CONTEXT, USER_MESSAGE, new ArrayList<>(MESSAGES), null);
        ToolServiceContext withEmpty = toolService.createContext(
                INVOCATION_CONTEXT, USER_MESSAGE, new ArrayList<>(MESSAGES), List.of());

        assertThat(withNull).isSameAs(ToolServiceContext.Empty.INSTANCE);
        assertThat(withEmpty).isSameAs(ToolServiceContext.Empty.INSTANCE);
    }
}
