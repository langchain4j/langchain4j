package dev.langchain4j.agentskills;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentskills.execution.DefaultScriptExecutor;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * End-to-end tests: Verify full integration of Agent Skills with real LLM
 * 
 * <p>These tests require environment variables to run:
 * <ul>
 *   <li>QWEN_API_KEY - Qwen API key</li>
 * </ul>
 * 
 * <p>Test scenarios include:
 * <ul>
 *   <li>Skills discovery and listing</li>
 *   <li>Skill content loading</li>
 *   <li>Script execution</li>
 *   <li>Resource file reading</li>
 * </ul>
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 */
@EnabledIfEnvironmentVariable(named = "QWEN_API_KEY", matches = ".+")
class AgentSkillsEndToEndTest {

    private static Path skillsDirectory;
    
    interface Assistant {
        String chat(String userMessage);
    }

    @BeforeAll
    static void setUp() throws URISyntaxException {
        skillsDirectory = Paths.get(
            AgentSkillsEndToEndTest.class
                .getClassLoader()
                .getResource("test-skills")
                .toURI()
        );
    }

    @Test
    void should_discover_available_skills() {
        // given
        DefaultAgentSkillsProvider skillsProvider = DefaultAgentSkillsProvider.builder()
            .skillDirectories(skillsDirectory)
            .build();

        OpenAiChatModel model = createModel();

        AgentSkillsConfig config = AgentSkillsConfig.builder()
            .skillsProvider(skillsProvider)
            .scriptExecutor(new DefaultScriptExecutor())
            .maxIterations(5)
            .build();

        Assistant assistant = AiServices.builder(Assistant.class)
            .chatModel(model)
            .agentSkillsConfig(config)
            .build();

        // when
        String response = assistant.chat("What skills are available? List their names.");

        // then
        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("pdf", "skill");
        System.out.println("✅ Skills Discovery Test Passed");
        System.out.println("Response: " + response);
    }

    @Test
    void should_load_skill_content() {
        // given
        DefaultAgentSkillsProvider skillsProvider = DefaultAgentSkillsProvider.builder()
            .skillDirectories(skillsDirectory)
            .build();

        OpenAiChatModel model = createModel();

        AgentSkillsConfig config = AgentSkillsConfig.builder()
            .skillsProvider(skillsProvider)
            .scriptExecutor(new DefaultScriptExecutor())
            .maxIterations(5)
            .build();

        Assistant assistant = AiServices.builder(Assistant.class)
            .chatModel(model)
            .agentSkillsConfig(config)
            .build();

        // when
        String response = assistant.chat(
            "Please load the pdf-processing skill and tell me what it can do. " +
            "Use the <use_skill>pdf-processing</use_skill> instruction to load it."
        );

        // then
        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("extract", "pdf", "document");
        System.out.println("✅ Skill Content Loading Test Passed");
        System.out.println("Response: " + response);
    }

    @Test
    void should_execute_skill_script() {
        // given
        DefaultAgentSkillsProvider skillsProvider = DefaultAgentSkillsProvider.builder()
            .skillDirectories(skillsDirectory)
            .build();

        OpenAiChatModel model = createModel();

        AgentSkillsConfig config = AgentSkillsConfig.builder()
            .skillsProvider(skillsProvider)
            .scriptExecutor(new DefaultScriptExecutor())
            .maxIterations(5)
            .build();

        Assistant assistant = AiServices.builder(Assistant.class)
            .chatModel(model)
            .agentSkillsConfig(config)
            .build();

        // when
        String response = assistant.chat(
            "Use the pdf-processing skill to extract text from 'document.pdf'. " +
            "Execute the script using: <execute_script skill=\"pdf-processing\">bash scripts/extract.sh document.pdf</execute_script>"
        );

        // then
        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("extract", "success", "complet", "document");
        System.out.println("✅ Script Execution Test Passed");
        System.out.println("Response: " + response);
    }

    @Test
    void should_read_skill_resource() {
        // given
        DefaultAgentSkillsProvider skillsProvider = DefaultAgentSkillsProvider.builder()
            .skillDirectories(skillsDirectory)
            .build();

        OpenAiChatModel model = createModel();

        AgentSkillsConfig config = AgentSkillsConfig.builder()
            .skillsProvider(skillsProvider)
            .scriptExecutor(new DefaultScriptExecutor())
            .maxIterations(5)
            .build();

        Assistant assistant = AiServices.builder(Assistant.class)
            .chatModel(model)
            .agentSkillsConfig(config)
            .build();

        // when
        String response = assistant.chat(
            "Read the configuration file from the pdf-processing skill. " +
            "Use: <read_resource skill=\"pdf-processing\">assets/config.json</read_resource>"
        );

        // then
        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("config", "extraction", "json");
        System.out.println("✅ Resource Reading Test Passed");
        System.out.println("Response: " + response);
    }

    @Test
    void should_handle_multiple_skill_operations() {
        // given
        DefaultAgentSkillsProvider skillsProvider = DefaultAgentSkillsProvider.builder()
            .skillDirectories(skillsDirectory)
            .build();

        OpenAiChatModel model = createModel();

        AgentSkillsConfig config = AgentSkillsConfig.builder()
            .skillsProvider(skillsProvider)
            .scriptExecutor(new DefaultScriptExecutor())
            .maxIterations(10)
            .build();

        Assistant assistant = AiServices.builder(Assistant.class)
            .chatModel(model)
            .agentSkillsConfig(config)
            .build();

        // when
        String response = assistant.chat(
            "Please do the following: " +
            "1) First, load the pdf-processing skill to understand its capabilities. " +
            "2) Then, read its configuration file. " +
            "3) Finally, execute the extraction script. " +
            "Report what you found at each step."
        );

        // then
        assertThat(response).isNotNull();
        assertThat(response.length()).isGreaterThan(100);
        System.out.println("✅ Multiple Operations Test Passed");
        System.out.println("Response: " + response);
    }

    @Test
    void should_handle_skill_not_found() {
        // given
        DefaultAgentSkillsProvider skillsProvider = DefaultAgentSkillsProvider.builder()
            .skillDirectories(skillsDirectory)
            .build();

        OpenAiChatModel model = createModel();

        AgentSkillsConfig config = AgentSkillsConfig.builder()
            .skillsProvider(skillsProvider)
            .scriptExecutor(new DefaultScriptExecutor())
            .maxIterations(5)
            .build();

        Assistant assistant = AiServices.builder(Assistant.class)
            .chatModel(model)
            .agentSkillsConfig(config)
            .build();

        // when
        String response = assistant.chat(
            "Try to use a skill called 'non-existent-skill' using: " +
            "<use_skill>non-existent-skill</use_skill>"
        );

        // then
        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("not found", "doesn't exist", "does not exist", "error", "available", "only");
        System.out.println("✅ Error Handling Test Passed");
        System.out.println("Response: " + response);
    }

    @Test
    void should_verify_system_prompt_enhancement() {
        // given
        DefaultAgentSkillsProvider skillsProvider = DefaultAgentSkillsProvider.builder()
            .skillDirectories(skillsDirectory)
            .build();

        OpenAiChatModel model = createModel();

        AgentSkillsConfig config = AgentSkillsConfig.builder()
            .skillsProvider(skillsProvider)
            .scriptExecutor(new DefaultScriptExecutor())
            .maxIterations(5)
            .build();

        Assistant assistant = AiServices.builder(Assistant.class)
            .chatModel(model)
            .agentSkillsConfig(config)
            .build();

        // when
        String response = assistant.chat(
            "Tell me about your capabilities. What special skills do you have access to?"
        );

        // then
        assertThat(response).isNotNull();
        // LLM should mention skills because system prompt was enhanced
        assertThat(response.toLowerCase()).containsAnyOf("skill", "pdf", "capabilit");
        System.out.println("✅ System Prompt Enhancement Test Passed");
        System.out.println("Response: " + response);
    }

    private OpenAiChatModel createModel() {
        String apiKey = System.getenv("QWEN_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("QWEN_API_KEY environment variable is required");
        }

        return OpenAiChatModel.builder()
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
            .apiKey(apiKey)
            .modelName("qwen-plus")
            .temperature(0.7)
            .timeout(Duration.ofSeconds(60))
            .maxRetries(2)
            .logRequests(false)
            .logResponses(false)
            .build();
    }
}
