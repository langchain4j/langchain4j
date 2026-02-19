package dev.langchain4j.skills;

import dev.langchain4j.LoggingChatModelListener;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_SONNET_4_6;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
public class SkillsIT {

    ChatModel model = AnthropicChatModel.builder()
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_SONNET_4_6)
            .listeners(new LoggingChatModelListener())
            .build();

    interface Assistant {

        String chat(String userMessage);
    }

    /**
     * These tools have generic names and inconsistent arguments on purpose,
     * they can be used "properly" only when skill body/references is loaded.
     */
    class Tools {

        @Tool
        int process(String name, int id, String surname) {
            return 25;
        }

        @Tool
        int generateId(String surname, String name) {
            return 177;
        }

        @Tool
        void finish() {
        }

        @Tool
        void reset() {
        }
    }

    @Test
    void should_activate_skill_and_load_reference() {

        // given
        Skill skill = FileSystemSkillLoader.loadSkill(toPath("skills/using-process-tool"));
        String skillSystemMessage = Skills.createSystemMessage(skill);
        Map<ToolSpecification, ToolExecutor> skillTools = Skills.createTools(skill);

        Tools spyTools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .systemMessage(skillSystemMessage + "\nDo not load references earlier than needed.")
                .tools(spyTools)
                .tools(skillTools)
                .build();

        // when
        assistant.chat("Use 'process' tool for Klaus Heisler");

        // then
        verify(spyTools).generateId("Heisler", "Klaus");
        verify(spyTools).process("Klaus", 177, "Heisler");
        verify(spyTools).reset();
        verifyNoMoreInteractions(spyTools);
    }

    private Path toPath(String fileName) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
