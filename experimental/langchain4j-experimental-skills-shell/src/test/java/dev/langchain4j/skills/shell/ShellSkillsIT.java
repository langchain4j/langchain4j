package dev.langchain4j.skills.shell;

import dev.langchain4j.LoggingChatModelListener;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.skills.FileSystemSkill;
import dev.langchain4j.skills.FileSystemSkillLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_SONNET_4_6;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ShellSkillsIT {

    ChatModel model = AnthropicChatModel.builder()
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_SONNET_4_6)
            .listeners(new LoggingChatModelListener())
            .build();

    interface Assistant {

        Result<String> chat(String userMessage);
    }

    @Test
    void should_activate_skill_and_run_script() {

        // given
        FileSystemSkill skill = FileSystemSkillLoader.loadSkill(toPath("skills/greeting-user"));

        // when
        ShellSkills skills = ShellSkills.from(skill);

        // then
        assertThat(skills.formatAvailableSkills()).contains("greeting-user");
        assertThat(getToolNames(skills.toolProvider())).containsExactly("run_shell_command");

        // given
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .systemMessage("You have access to the following skills:\n" + skills.formatAvailableSkills()
                        + "\nWhen the user's request relates to one of these skills, read its SKILL.md before proceeding.")
                .toolProvider(skills.toolProvider())
                .build();

        // when
        Result<String> result = assistant.chat("Greet the user");

        // then
        assertThat(result.content()).containsIgnoringCase("python from hello");
    }

    private Path toPath(String fileName) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static Stream<String> getToolNames(ToolProvider toolProvider) {
        return toolProvider.provideTools(null).tools().keySet().stream().map(ToolSpecification::name);
    }
}
