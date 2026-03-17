package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE;
import static dev.langchain4j.agent.tool.SearchBehavior.NOT_SEARCHABLE;
import static dev.langchain4j.agent.tool.ToolSpecification.METADATA_SEARCH_BEHAVIOR;
import static dev.langchain4j.service.tool.ToolService.EFFECTIVE_TOOLS_ATTRIBUTE;
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

    @Test
    void skill_with_tools_should_have_single_static_provider() {

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

        // then - single provider, not dynamic
        assertThat(skills.toolProvider()).isNotNull();

        // provider returns all tools: activate_skill (ALWAYS_VISIBLE) + my_tool (NOT_SEARCHABLE)
        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        assertThat(getToolNames(result)).containsExactlyInAnyOrder("activate_skill", "my_tool");

        ToolSpecification activateSkill = result.toolSpecificationByName("activate_skill");
        assertThat(activateSkill.metadata().get(METADATA_SEARCH_BEHAVIOR)).isEqualTo(ALWAYS_VISIBLE);

        ToolSpecification myTool = result.toolSpecificationByName("my_tool");
        assertThat(myTool.metadata().get(METADATA_SEARCH_BEHAVIOR)).isEqualTo(NOT_SEARCHABLE);
    }

    @Test
    void skill_without_tools_should_have_single_provider() {

        // given
        Skill skill = Skill.builder()
                .name("simple-skill")
                .description("A skill without tools")
                .content("Just instructions")
                .build();

        Skills skills = Skills.from(skill);

        // then - single provider
        assertThat(skills.toolProvider()).isNotNull();

        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        assertThat(getToolNames(result)).containsExactly("activate_skill");
    }

    @Test
    void multiple_skills_with_tools_should_have_single_provider() {

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

        // then - single provider with all tools
        assertThat(skills.toolProvider()).isNotNull();

        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        assertThat(getToolNames(result)).containsExactlyInAnyOrder("activate_skill", "tool_a", "tool_b");

        // skill-scoped tools are NOT_SEARCHABLE
        assertThat(result.toolSpecificationByName("tool_a").metadata().get(METADATA_SEARCH_BEHAVIOR))
                .isEqualTo(NOT_SEARCHABLE);
        assertThat(result.toolSpecificationByName("tool_b").metadata().get(METADATA_SEARCH_BEHAVIOR))
                .isEqualTo(NOT_SEARCHABLE);
    }

    @Test
    void activate_skill_should_emit_found_tools_attribute() {

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
        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        ToolExecutor activateExecutor = result.toolExecutorByName("activate_skill");

        // when
        ToolExecutionResult executionResult = activateExecutor.executeWithContext(
                ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"my-skill\"}")
                        .build(),
                InvocationContext.builder().build()
        );

        // then
        assertThat(executionResult.attributes().get(EFFECTIVE_TOOLS_ATTRIBUTE))
                .isEqualTo(List.of("my_tool"));
    }

    @Test
    void activate_skill_without_tools_should_not_emit_found_tools() {

        // given
        Skill skill = Skill.builder()
                .name("my-skill")
                .description("A skill without tools")
                .content("Just instructions")
                .build();

        Skills skills = Skills.from(skill);
        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        ToolExecutor activateExecutor = result.toolExecutorByName("activate_skill");

        // when
        ToolExecutionResult executionResult = activateExecutor.executeWithContext(
                ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"my-skill\"}")
                        .build(),
                InvocationContext.builder().build()
        );

        // then
        assertThat(executionResult.attributes()).doesNotContainKey(EFFECTIVE_TOOLS_ATTRIBUTE);
    }

    @Test
    void activate_skill_should_emit_only_activated_skills_tools() {

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
        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        ToolExecutor activateExecutor = result.toolExecutorByName("activate_skill");

        // when - activate only skill-1
        ToolExecutionResult executionResult = activateExecutor.executeWithContext(
                ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"skill-1\"}")
                        .build(),
                InvocationContext.builder().build()
        );

        // then - only skill-1's tools
        assertThat(executionResult.attributes().get(EFFECTIVE_TOOLS_ATTRIBUTE))
                .isEqualTo(List.of("tool_a"));
    }

    @Test
    void skill_scoped_tool_executor_should_be_functional() {

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

        // when
        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        ToolExecutor resolvedExecutor = result.toolExecutorByName("greet");

        // then
        assertThat(resolvedExecutor).isNotNull();
        assertThat(resolvedExecutor.execute(
                ToolExecutionRequest.builder().name("greet").arguments("{}").build(), null))
                .isEqualTo("Hello, World!");
    }


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

        // then - single provider with all tools
        assertThat(skills.toolProvider()).isNotNull();

        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        assertThat(getToolNames(result)).containsExactlyInAnyOrder("activate_skill", "sayHello");
        assertThat(result.toolSpecificationByName("sayHello").metadata().get(METADATA_SEARCH_BEHAVIOR))
                .isEqualTo(NOT_SEARCHABLE);
    }

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

        // then - single provider
        assertThat(skills.toolProvider()).isNotNull();

        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        assertThat(getToolNames(result)).containsExactlyInAnyOrder("activate_skill", "dynamic_tool");
        assertThat(result.toolSpecificationByName("dynamic_tool").metadata().get(METADATA_SEARCH_BEHAVIOR))
                .isEqualTo(NOT_SEARCHABLE);
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

        // Skills wraps them in a single provider
        Skills skills = Skills.from(skill);
        assertThat(skills.toolProvider()).isNotNull();

        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        assertThat(getToolNames(result)).containsExactlyInAnyOrder("activate_skill", "sayHello", "mcp_tool");
    }

    static class AnotherTools {

        @dev.langchain4j.agent.tool.Tool("Says goodbye")
        String sayGoodbye(String name) {
            return "Goodbye, " + name + "!";
        }
    }

    @Test
    void tools_map_should_not_override_tool_annotated_methods() {

        // given
        ToolSpecification manualTool = ToolSpecification.builder()
                .name("manual_tool")
                .description("A manual tool")
                .build();
        ToolExecutor manualExecutor = (request, memoryId) -> "manual";

        Skill skill = Skill.builder()
                .name("my-skill")
                .description("A skill")
                .content("Use all tools")
                .tools(new MyTools())
                .tools(Map.of(manualTool, manualExecutor))
                .build();

        Skills skills = Skills.from(skill);

        // then - both @Tool method and Map tool are present
        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        assertThat(getToolNames(result)).containsExactlyInAnyOrder("activate_skill", "sayHello", "manual_tool");
    }

    @Test
    void tool_annotated_methods_should_not_override_tools_map() {

        // given - reversed order: Map first, then @Tool
        ToolSpecification manualTool = ToolSpecification.builder()
                .name("manual_tool")
                .description("A manual tool")
                .build();
        ToolExecutor manualExecutor = (request, memoryId) -> "manual";

        Skill skill = Skill.builder()
                .name("my-skill")
                .description("A skill")
                .content("Use all tools")
                .tools(Map.of(manualTool, manualExecutor))
                .tools(new MyTools())
                .build();

        Skills skills = Skills.from(skill);

        // then
        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        assertThat(getToolNames(result)).containsExactlyInAnyOrder("activate_skill", "manual_tool", "sayHello");
    }

    @Test
    void multiple_tool_annotated_objects_should_accumulate() {

        // given
        Skill skill = Skill.builder()
                .name("my-skill")
                .description("A skill")
                .content("Use all tools")
                .tools(new MyTools())
                .tools(new AnotherTools())
                .build();

        Skills skills = Skills.from(skill);

        // then
        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        assertThat(getToolNames(result)).containsExactlyInAnyOrder("activate_skill", "sayHello", "sayGoodbye");
    }

    @Test
    void multiple_tool_maps_should_accumulate() {

        // given
        ToolSpecification tool1 = ToolSpecification.builder().name("tool_1").description("Tool 1").build();
        ToolSpecification tool2 = ToolSpecification.builder().name("tool_2").description("Tool 2").build();

        Skill skill = Skill.builder()
                .name("my-skill")
                .description("A skill")
                .content("Use all tools")
                .tools(Map.of(tool1, (ToolExecutor) (request, memoryId) -> "1"))
                .tools(Map.of(tool2, (ToolExecutor) (request, memoryId) -> "2"))
                .build();

        Skills skills = Skills.from(skill);

        // then
        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        assertThat(getToolNames(result)).containsExactlyInAnyOrder("activate_skill", "tool_1", "tool_2");
    }

    @Test
    void tool_providers_should_not_override_each_other() {

        // given
        ToolProvider provider1 = request -> ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("provider1_tool").description("P1").build(),
                        (req, memoryId) -> "p1")
                .build();
        ToolProvider provider2 = request -> ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("provider2_tool").description("P2").build(),
                        (req, memoryId) -> "p2")
                .build();

        Skill skill = Skill.builder()
                .name("my-skill")
                .description("A skill")
                .content("Use all tools")
                .toolProviders(provider1)
                .toolProviders(provider2)
                .build();

        // then - both providers are present on the skill
        assertThat(skill.toolProviders()).hasSize(2);

        Skills skills = Skills.from(skill);

        // then - tools from both providers
        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        assertThat(getToolNames(result)).containsExactlyInAnyOrder("activate_skill", "provider1_tool", "provider2_tool");
    }

    @Test
    void all_three_tool_types_should_accumulate() {

        // given
        ToolSpecification manualTool = ToolSpecification.builder()
                .name("manual_tool")
                .description("A manual tool")
                .build();
        ToolExecutor manualExecutor = (request, memoryId) -> "manual";

        ToolProvider dynamicProvider = request -> ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("dynamic_tool").description("Dynamic").build(),
                        (req, memoryId) -> "dynamic")
                .build();

        Skill skill = Skill.builder()
                .name("my-skill")
                .description("A skill")
                .content("Use all tools")
                .tools(new MyTools())
                .tools(Map.of(manualTool, manualExecutor))
                .toolProviders(dynamicProvider)
                .build();

        // then - 2 tool providers on skill: 1 static (wrapping @Tool + Map), 1 dynamic
        assertThat(skill.toolProviders()).hasSize(2);

        Skills skills = Skills.from(skill);

        // then - all three tools present
        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        assertThat(getToolNames(result)).containsExactlyInAnyOrder("activate_skill", "sayHello", "manual_tool", "dynamic_tool");
    }

    @Test
    void tool_providers_collection_should_not_override_varargs() {

        // given
        ToolProvider provider1 = request -> ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("vararg_tool").description("Vararg").build(),
                        (req, memoryId) -> "vararg")
                .build();
        ToolProvider provider2 = request -> ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("collection_tool").description("Collection").build(),
                        (req, memoryId) -> "collection")
                .build();

        Skill skill = Skill.builder()
                .name("my-skill")
                .description("A skill")
                .content("Use all tools")
                .toolProviders(provider1)
                .toolProviders(List.of(provider2))
                .build();

        // then - both providers present
        assertThat(skill.toolProviders()).hasSize(2);
    }

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
        assertThat(skills.toolProvider()).isNotNull();

        // tools present including skill-scoped
        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());
        assertThat(getToolNames(result)).containsExactlyInAnyOrder("activate_skill", "dynamic_tool");
    }

    private static ToolProviderRequest dummyRequest() {
        return ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(UserMessage.from("test"))
                .build();
    }

    private static Stream<String> getToolNames(ToolProviderResult result) {
        return result.tools().keySet().stream()
                .map(ToolSpecification::name);
    }
}
