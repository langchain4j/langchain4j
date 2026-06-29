package dev.langchain4j.model.anthropic;

import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.addSkillsBeta;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.createAnthropicRequest;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType.NO_CACHE;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicContainer.AnthropicContainerSkill;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTool;
import dev.langchain4j.model.anthropic.internal.client.Json;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnthropicSkillsTest {

    private static ChatRequest chatRequest() {
        return ChatRequest.builder()
                .messages(UserMessage.from("Create a 5-slide deck about renewable energy"))
                .parameters(ChatRequestParameters.builder()
                        .modelName("claude-opus-4-8")
                        .maxOutputTokens(4096)
                        .build())
                .build();
    }

    private static AnthropicCreateMessageRequest requestWithSkills(List<AnthropicSkill> skills) {
        return createAnthropicRequest(
                chatRequest(), null, true, false, NO_CACHE, NO_CACHE, false, null, null, null, null, null, skills, null, null);
    }

    @Test
    void skills_shouldAddContainerWithAnthropicManagedSkills() {
        AnthropicCreateMessageRequest request = requestWithSkills(List.of(AnthropicSkill.XLSX, AnthropicSkill.PPTX));

        assertThat(request.container).isNotNull();
        assertThat(request.container.skills)
                .containsExactly(
                        new AnthropicContainerSkill("anthropic", "xlsx", "latest"),
                        new AnthropicContainerSkill("anthropic", "pptx", "latest"));
    }

    @Test
    void skills_shouldAutoAddCodeExecutionServerTool() {
        AnthropicCreateMessageRequest request = requestWithSkills(List.of(AnthropicSkill.DOCX));

        assertThat(request.tools).hasSize(1);
        AnthropicTool tool = request.tools.get(0);
        assertThat(tool.name).isEqualTo("code_execution");
        assertThat(tool.customParameters()).containsEntry("type", "code_execution_20250825");
    }

    @Test
    void skills_shouldNotDuplicateExplicitlyConfiguredCodeExecutionTool() {
        AnthropicServerTool codeExecution = AnthropicServerTool.builder()
                .type("code_execution_20250825")
                .name("code_execution")
                .build();

        AnthropicCreateMessageRequest request = createAnthropicRequest(
                chatRequest(),
                null,
                true,
                false,
                NO_CACHE,
                NO_CACHE,
                false,
                null,
                null,
                List.of(codeExecution),
                null,
                null,
                List.of(AnthropicSkill.PDF),
                null,
                null);

        assertThat(request.tools).extracting(tool -> tool.name).containsExactly("code_execution");
    }

    @Test
    void skills_shouldStillAddServerToolWhenRegularToolIsNamedCodeExecution() {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Create a spreadsheet"))
                .parameters(ChatRequestParameters.builder()
                        .modelName("claude-opus-4-8")
                        .maxOutputTokens(4096)
                        .toolSpecifications(ToolSpecification.builder()
                                .name("code_execution")
                                .description("a user-defined tool that merely shares the name")
                                .build())
                        .build())
                .build();

        AnthropicCreateMessageRequest request = createAnthropicRequest(
                chatRequest,
                null,
                true,
                false,
                NO_CACHE,
                NO_CACHE,
                false,
                null,
                null,
                null,
                emptySet(),
                null,
                List.of(AnthropicSkill.XLSX),
                null,
                null);

        // the regular tool name must not suppress the required code_execution server tool
        assertThat(request.tools)
                .anyMatch(tool -> "code_execution".equals(tool.name)
                        && tool.customParameters() != null
                        && "code_execution_20250825"
                                .equals(tool.customParameters().get("type")));
    }

    @Test
    void skills_shouldDeduplicate() {
        AnthropicCreateMessageRequest request =
                requestWithSkills(List.of(AnthropicSkill.PDF, AnthropicSkill.PDF, AnthropicSkill.DOCX));

        assertThat(request.container.skills)
                .containsExactly(
                        new AnthropicContainerSkill("anthropic", "pdf", "latest"),
                        new AnthropicContainerSkill("anthropic", "docx", "latest"));
    }

    @Test
    void skills_shouldIgnoreNullEntries() {
        AnthropicCreateMessageRequest request = requestWithSkills(Arrays.asList(AnthropicSkill.XLSX, null));

        assertThat(request.container.skills)
                .containsExactly(new AnthropicContainerSkill("anthropic", "xlsx", "latest"));
    }

    @Test
    void noSkills_shouldNotAddContainerOrCodeExecutionTool() {
        AnthropicCreateMessageRequest request = requestWithSkills(emptyList());

        assertThat(request.container).isNull();
        assertThat(request.tools).isNullOrEmpty();
    }

    @Test
    void skills_shouldSerializeReferenceRequestShape() {
        String json = Json.toJson(requestWithSkills(List.of(AnthropicSkill.PPTX)));

        assertThat(json)
                .contains("\"container\"")
                .contains("\"skills\"")
                .contains("\"type\" : \"anthropic\"")
                .contains("\"skill_id\" : \"pptx\"")
                .contains("\"version\" : \"latest\"")
                .contains("\"type\" : \"code_execution_20250825\"")
                .contains("\"name\" : \"code_execution\"");
    }

    @Test
    void addSkillsBeta_shouldReturnBetaUnchangedWhenNoSkills() {
        assertThat(addSkillsBeta(null, null)).isNull();
        assertThat(addSkillsBeta("my-beta", emptyList())).isEqualTo("my-beta");
    }

    @Test
    void addSkillsBeta_shouldAddRequiredBetaFeaturesWhenSkillsPresent() {
        assertThat(addSkillsBeta(null, List.of(AnthropicSkill.XLSX)))
                .isEqualTo("code-execution-2025-08-25,skills-2025-10-02,files-api-2025-04-14");
    }

    @Test
    void addSkillsBeta_shouldPreserveUserBetaAndAvoidDuplicates() {
        String beta = addSkillsBeta("skills-2025-10-02 , my-custom-beta", List.of(AnthropicSkill.PDF));

        assertThat(beta).isEqualTo("skills-2025-10-02,my-custom-beta,code-execution-2025-08-25,files-api-2025-04-14");
    }
}
