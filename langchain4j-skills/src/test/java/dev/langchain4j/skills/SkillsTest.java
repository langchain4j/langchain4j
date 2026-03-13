package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static dev.langchain4j.skills.ActivateSkillToolExecutor.ACTIVATED_SKILL_ATTRIBUTE;

import static org.assertj.core.api.Assertions.assertThat;

class SkillsTest {

    @Test
    void should_generate_description_for_single_skill() {
        Skills skills = Skills.from(
                Skill.builder()
                        .name("docx")
                        .description("Edit Word documents")
                        .content("skill content")
                        .build()
        );

        assertThat(skills.formatAvailableSkills()).isEqualTo(
                """
                        <available_skills>
                        <skill>
                        <name>docx</name>
                        <description>Edit Word documents</description>
                        </skill>
                        </available_skills>"""
        );
    }

    @Test
    void should_generate_description_for_multiple_skills() {
        Skills skills = Skills.from(
                Skill.builder()
                        .name("docx")
                        .description("Edit Word documents")
                        .content("docx content")
                        .build(),
                Skill.builder()
                        .name("mcp-builder")
                        .description("Build MCP servers")
                        .content("mcp content")
                        .build()
        );

        assertThat(skills.formatAvailableSkills()).isEqualTo(
                """
                        <available_skills>
                        <skill>
                        <name>docx</name>
                        <description>Edit Word documents</description>
                        </skill>
                        <skill>
                        <name>mcp-builder</name>
                        <description>Build MCP servers</description>
                        </skill>
                        </available_skills>"""
        );
    }

    @Test
    void should_escape_xml_special_characters_in_name_and_description() {
        Skills skills = Skills.from(
                Skill.builder()
                        .name("skill<>&\"'")
                        .description("desc<>&\"'")
                        .content("content")
                        .build()
        );

        assertThat(skills.formatAvailableSkills()).isEqualTo(
                """
                        <available_skills>
                        <skill>
                        <name>skill&lt;&gt;&amp;&quot;&apos;</name>
                        <description>desc&lt;&gt;&amp;&quot;&apos;</description>
                        </skill>
                        </available_skills>"""
        );
    }

    // --- Dynamic skill tool providers ---

    @Test
    void skill_tools_should_be_in_separate_dynamic_provider() {

        // given
        Skill skill = Skill.builder()
                .name("my-skill")
                .description("A skill with tools")
                .content("Use my_tool to do something")
                .tools(Map.of(
                        ToolSpecification.builder().name("my_tool").description("Does something").build(),
                        (ToolExecutor) (request, memoryId) -> "result"
                ))
                .build();

        Skills skills = Skills.from(skill);

        // then - should have 2 providers: base (static) + skill (dynamic)
        assertThat(skills.toolProviders()).hasSize(2);
        assertThat(skills.toolProviders().get(0).isDynamic()).isFalse();
        assertThat(skills.toolProviders().get(1).isDynamic()).isTrue();

        // base provider tools
        ToolProviderResult baseResult = skills.toolProviders().get(0).provideTools(dummyRequest());
        assertThat(getToolNames(baseResult)).containsExactly("activate_skill");
    }

    @Test
    void skill_provider_should_return_empty_before_skill_activation() {

        // given
        Skill skill = Skill.builder()
                .name("my-skill")
                .description("A skill with tools")
                .content("Use my_tool")
                .tools(Map.of(
                        ToolSpecification.builder().name("my_tool").description("Does something").build(),
                        (ToolExecutor) (request, memoryId) -> "result"
                ))
                .build();

        Skills skills = Skills.from(skill);

        // when - no activation in messages
        ToolProviderRequest request = requestWithMessages(List.of(UserMessage.from("hello")));

        // then - skill provider returns empty
        ToolProviderResult result = skills.toolProviders().get(1).provideTools(request);
        assertThat(result.tools()).isEmpty();
    }

    @Test
    void skill_provider_should_return_tools_after_skill_activation() {

        // given
        Skill skill = Skill.builder()
                .name("my-skill")
                .description("A skill with tools")
                .content("Use my_tool")
                .tools(Map.of(
                        ToolSpecification.builder().name("my_tool").description("Does something").build(),
                        (ToolExecutor) (request, memoryId) -> "result"
                ))
                .build();

        Skills skills = Skills.from(skill);

        // when - activation in messages
        ToolProviderRequest request = requestWithMessages(List.of(
                UserMessage.from("Do something"),
                skillActivatedMessage("my-skill")
        ));

        // then - skill provider returns tools
        ToolProviderResult result = skills.toolProviders().get(1).provideTools(request);
        assertThat(getToolNames(result)).containsExactly("my_tool");
    }

    @Test
    void only_activated_skill_provider_should_return_tools() {

        // given
        Skill skill1 = Skill.builder()
                .name("skill-1")
                .description("First skill")
                .content("Use tool_a")
                .tools(Map.of(
                        ToolSpecification.builder().name("tool_a").description("Tool A").build(),
                        (ToolExecutor) (request, memoryId) -> "a"
                ))
                .build();

        Skill skill2 = Skill.builder()
                .name("skill-2")
                .description("Second skill")
                .content("Use tool_b")
                .tools(Map.of(
                        ToolSpecification.builder().name("tool_b").description("Tool B").build(),
                        (ToolExecutor) (request, memoryId) -> "b"
                ))
                .build();

        Skills skills = Skills.from(skill1, skill2);

        // when - only skill-1 activated
        ToolProviderRequest request = requestWithMessages(List.of(
                UserMessage.from("Do something"),
                skillActivatedMessage("skill-1")
        ));

        // then - 3 providers: base + skill-1 + skill-2
        assertThat(skills.toolProviders()).hasSize(3);

        assertThat(getToolNames(skills.toolProviders().get(1).provideTools(request))).containsExactly("tool_a");
        assertThat(skills.toolProviders().get(2).provideTools(request).tools()).isEmpty();
    }

    @Test
    void multiple_activated_skills_should_return_tools() {

        // given
        Skill skill1 = Skill.builder()
                .name("skill-1")
                .description("First skill")
                .content("Use tool_a")
                .tools(Map.of(
                        ToolSpecification.builder().name("tool_a").description("Tool A").build(),
                        (ToolExecutor) (request, memoryId) -> "a"
                ))
                .build();

        Skill skill2 = Skill.builder()
                .name("skill-2")
                .description("Second skill")
                .content("Use tool_b")
                .tools(Map.of(
                        ToolSpecification.builder().name("tool_b").description("Tool B").build(),
                        (ToolExecutor) (request, memoryId) -> "b"
                ))
                .build();

        Skills skills = Skills.from(skill1, skill2);

        // when - both skills activated
        ToolProviderRequest request = requestWithMessages(List.of(
                UserMessage.from("Do something"),
                skillActivatedMessage("skill-1"),
                skillActivatedMessage("skill-2")
        ));

        // then
        assertThat(getToolNames(skills.toolProviders().get(1).provideTools(request))).containsExactly("tool_a");
        assertThat(getToolNames(skills.toolProviders().get(2).provideTools(request))).containsExactly("tool_b");
    }

    @Test
    void skill_without_tools_should_have_only_base_provider() {

        // given
        Skill skill = Skill.builder()
                .name("simple-skill")
                .description("A skill without tools")
                .content("Just instructions")
                .build();

        Skills skills = Skills.from(skill);

        // then - only base provider
        assertThat(skills.toolProviders()).hasSize(1);
        assertThat(skills.toolProviders().get(0).isDynamic()).isFalse();

        ToolProviderResult result = skills.toolProviders().get(0).provideTools(dummyRequest());
        assertThat(getToolNames(result)).containsExactly("activate_skill");
    }

    @Test
    void conditional_tool_executor_should_be_functional() {

        // given
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("greet")
                .description("Greets someone")
                .build();
        ToolExecutor toolExecutor = (request, memoryId) -> "Hello, World!";

        Skill skill = Skill.builder()
                .name("greeting-skill")
                .description("A greeting skill")
                .content("Use greet tool")
                .tools(Map.of(toolSpec, toolExecutor))
                .build();

        Skills skills = Skills.from(skill);

        // when - call with activation
        ToolProviderRequest request = requestWithMessages(List.of(
                UserMessage.from("greet"),
                skillActivatedMessage("greeting-skill")
        ));
        ToolProviderResult skillResult = skills.toolProviders().get(1).provideTools(request);
        ToolExecutor resolvedExecutor = skillResult.toolExecutorByName("greet");

        // then
        assertThat(resolvedExecutor).isNotNull();
        assertThat(resolvedExecutor.execute(
                ToolExecutionRequest.builder().name("greet").arguments("{}").build(), null))
                .isEqualTo("Hello, World!");
    }

    // --- @Tool-annotated objects ---

    static class MyTools {

        @dev.langchain4j.agent.tool.Tool("Says hello")
        String sayHello(String name) {
            return "Hello, " + name + "!";
        }
    }

    @Test
    void skill_should_support_tool_annotated_objects() {

        // given
        Skill skill = Skill.builder()
                .name("greeting-skill")
                .description("A greeting skill")
                .content("Use sayHello tool")
                .tools(new MyTools())
                .build();

        // then
        assertThat(skill.toolProviders()).hasSize(1);
    }

    @Test
    void skill_with_tool_annotated_objects_should_work_with_skills() {

        // given
        Skill skill = Skill.builder()
                .name("greeting-skill")
                .description("A greeting skill")
                .content("Use sayHello tool")
                .tools(new MyTools())
                .build();

        Skills skills = Skills.from(skill);

        // then - 2 providers: base + skill (dynamic)
        assertThat(skills.toolProviders()).hasSize(2);
        assertThat(skills.toolProviders().get(1).isDynamic()).isTrue();

        // before activation: empty
        assertThat(skills.toolProviders().get(1).provideTools(dummyRequest()).tools()).isEmpty();

        // after activation: tools returned
        ToolProviderRequest activatedRequest = requestWithMessages(List.of(
                UserMessage.from("greet"),
                skillActivatedMessage("greeting-skill")
        ));
        assertThat(getToolNames(skills.toolProviders().get(1).provideTools(activatedRequest)))
                .containsExactly("sayHello");
    }

    // --- ToolProvider on Skill ---

    @Test
    void skill_should_support_tool_providers() {

        // given
        ToolProvider myProvider = request -> ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("dynamic_tool").description("A dynamic tool").build(),
                        (req, memoryId) -> "dynamic result")
                .build();

        Skill skill = Skill.builder()
                .name("dynamic-skill")
                .description("A dynamic skill")
                .content("Use dynamic_tool")
                .toolProviders(myProvider)
                .build();

        // then
        assertThat(skill.toolProviders()).hasSize(1);
    }

    @Test
    void skill_with_tool_provider_should_work_with_skills() {

        // given
        ToolProvider myProvider = request -> ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("dynamic_tool").description("A dynamic tool").build(),
                        (req, memoryId) -> "dynamic result")
                .build();

        Skill skill = Skill.builder()
                .name("dynamic-skill")
                .description("A dynamic skill")
                .content("Use dynamic_tool")
                .toolProviders(myProvider)
                .build();

        Skills skills = Skills.from(skill);

        // then - 2 providers: base + skill (dynamic)
        assertThat(skills.toolProviders()).hasSize(2);
        assertThat(skills.toolProviders().get(1).isDynamic()).isTrue();

        // before activation: empty
        assertThat(skills.toolProviders().get(1).provideTools(dummyRequest()).tools()).isEmpty();
    }

    @Test
    void skill_should_combine_static_tools_and_tool_providers() {

        // given
        ToolProvider mcpProvider = request -> ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("mcp_tool").description("An MCP tool").build(),
                        (req, memoryId) -> "mcp result")
                .build();

        Skill skill = Skill.builder()
                .name("combo-skill")
                .description("A skill with both")
                .content("Use sayHello and mcp_tool")
                .tools(new MyTools())
                .toolProviders(mcpProvider)
                .build();

        // then - both static and dynamic providers on Skill
        assertThat(skill.toolProviders()).hasSize(2);

        // Skills wraps them in a single SkillToolProvider (dynamic)
        Skills skills = Skills.from(skill);
        assertThat(skills.toolProviders()).hasSize(2); // base + skill

        // after activation: both tools returned
        ToolProviderRequest activatedRequest = requestWithMessages(List.of(
                UserMessage.from("do stuff"),
                skillActivatedMessage("combo-skill")
        ));
        ToolProviderResult skillResult = skills.toolProviders().get(1).provideTools(activatedRequest);
        assertThat(getToolNames(skillResult)).containsExactlyInAnyOrder("sayHello", "mcp_tool");
    }

    // --- toBuilder ---

    @Test
    void toBuilder_should_copy_all_fields() {

        // given
        DefaultSkill original = (DefaultSkill) Skill.builder()
                .name("my-skill")
                .description("My skill")
                .content("Some content")
                .build();

        // when
        DefaultSkill copy = original.toBuilder().build();

        // then
        assertThat(copy.name()).isEqualTo("my-skill");
        assertThat(copy.description()).isEqualTo("My skill");
        assertThat(copy.content()).isEqualTo("Some content");
    }

    @Test
    void toBuilder_should_allow_adding_tools_to_filesystem_loaded_skill() {

        // given - simulate a filesystem-loaded skill (no tools)
        DefaultSkill original = (DefaultSkill) Skill.builder()
                .name("my-skill")
                .description("My skill")
                .content("Use sayHello")
                .build();

        assertThat(original.toolProviders()).isEmpty();

        // when - add tools via toBuilder
        DefaultSkill withTools = original.toBuilder()
                .tools(new MyTools())
                .build();

        // then
        assertThat(withTools.name()).isEqualTo("my-skill");
        assertThat(withTools.toolProviders()).hasSize(1);
    }

    @Test
    void toBuilder_should_allow_adding_tool_provider_to_filesystem_loaded_skill() {

        // given - simulate a filesystem-loaded skill (no tools)
        DefaultSkill original = (DefaultSkill) Skill.builder()
                .name("my-skill")
                .description("My skill")
                .content("Use dynamic_tool")
                .build();

        ToolProvider mcpProvider = request -> ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("dynamic_tool").description("dynamic").build(),
                        (req, memoryId) -> "result")
                .build();

        // when
        DefaultSkill withProvider = original.toBuilder()
                .toolProviders(mcpProvider)
                .build();

        // then
        assertThat(withProvider.toolProviders()).hasSize(1);

        Skills skills = Skills.from(withProvider);
        assertThat(skills.toolProviders()).hasSize(2); // base + skill

        // after activation
        ToolProviderRequest activatedRequest = requestWithMessages(List.of(
                UserMessage.from("do stuff"),
                skillActivatedMessage("my-skill")
        ));
        ToolProviderResult skillResult = skills.toolProviders().get(1).provideTools(activatedRequest);
        assertThat(getToolNames(skillResult)).containsExactly("dynamic_tool");
    }

    private static ToolProviderRequest dummyRequest() {
        return ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(UserMessage.from("test"))
                .build();
    }

    private static ToolProviderRequest requestWithMessages(List<ChatMessage> messages) {
        return ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(UserMessage.from("test"))
                .messages(messages)
                .build();
    }

    private static ToolExecutionResultMessage skillActivatedMessage(String skillName) {
        return ToolExecutionResultMessage.builder()
                .toolName("activate_skill")
                .text("skill activated")
                .attributes(Map.of(ACTIVATED_SKILL_ATTRIBUTE, skillName))
                .build();
    }

    private static Stream<String> getToolNames(ToolProviderResult result) {
        return result.tools().keySet().stream()
                .map(ToolSpecification::name);
    }
}
