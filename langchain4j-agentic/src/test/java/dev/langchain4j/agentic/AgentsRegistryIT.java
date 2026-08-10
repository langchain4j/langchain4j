package dev.langchain4j.agentic;

import static dev.langchain4j.agentic.Models.baseModel;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.Agents.CreativeWriter;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.RegistryAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgentsRegistry;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.V;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AgentsRegistryIT {

    private static final String SPI_SERVICE_FILE =
            "META-INF/services/dev.langchain4j.agentic.planner.AgentsRegistry";

    private static URLClassLoader registryClassLoader;
    private static Path tempDir;
    private static ClassLoader originalClassLoader;

    @BeforeAll
    static void setUp() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("These tests require a JDK").isNotNull();

        tempDir = Files.createTempDirectory("compiled-registry-it-");

        Path sourceFile = tempDir.resolve("dev/langchain4j/agentic/test/WriterAgentsRegistry.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, registrySource(), StandardCharsets.UTF_8);

        int result = compiler.run(
                null, null, null,
                "-classpath", System.getProperty("java.class.path"),
                "-d", tempDir.toString(),
                sourceFile.toString());
        assertThat(result).as("Registry compilation must succeed").isZero();

        Path servicesFile = tempDir.resolve(SPI_SERVICE_FILE);
        Files.createDirectories(servicesFile.getParent());
        Files.writeString(servicesFile, "dev.langchain4j.agentic.test.WriterAgentsRegistry", StandardCharsets.UTF_8);

        registryClassLoader = new URLClassLoader(
                new URL[]{tempDir.toUri().toURL()}, AgentsRegistryIT.class.getClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (name.equals(SPI_SERVICE_FILE)) {
                    return findResources(name);
                }
                return super.getResources(name);
            }
        };

        originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(registryClassLoader);
        AgentsRegistry.refresh();
    }

    @AfterAll
    static void tearDown() throws Exception {
        Thread.currentThread().setContextClassLoader(originalClassLoader);
        AgentsRegistry.refresh();
        registryClassLoader.close();
        deleteRecursively(tempDir);
    }

    @Test
    void sequence_with_local_and_registry_agents() {
        AgentsRegistry registry = AgentsRegistry.get();

        Map<String, AgentInstance> allAgents = registry.allAgents();
        assertThat(allAgents).containsKeys("audienceEditor", "styleEditor");
        assertThat(allAgents).hasSize(2);

        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        AgentInstance audienceEditor = registry.getAgent("audienceEditor");
        AgentInstance styleEditor = registry.getAgent("styleEditor");

        UntypedAgent novelCreator = AgenticServices.sequenceBuilder()
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story")
                .build();

        Map<String, Object> input = Map.of(
                "topic", "dragons and wizards",
                "style", "fantasy",
                "audience", "young adults");

        String story = (String) novelCreator.invoke(input);
        assertThat(story).isNotBlank();
    }

    public interface AudienceEditorFromRegistry {
        @RegistryAgent("audienceEditor")
        String editStory(@V("story") String story, @V("audience") String audience);
    }

    public interface StyleEditorFromRegistry {
        @RegistryAgent("styleEditor")
        String editStory(@V("story") String story, @V("style") String style);
    }

    public interface DeclarativeStoryCreator {

        @SequenceAgent(
                outputKey = "story",
                subAgents = {CreativeWriter.class, AudienceEditorFromRegistry.class, StyleEditorFromRegistry.class})
        String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    @Test
    void declarative_sequence_with_registry_agents() {
        DeclarativeStoryCreator storyCreator =
                AgenticServices.createAgenticSystem(DeclarativeStoryCreator.class);

        String story = storyCreator.write("dragons and wizards", "fantasy", "young adults");
        assertThat(story).isNotBlank();
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    private static String registrySource() {
        return """
                package dev.langchain4j.agentic.test;

                import static dev.langchain4j.agentic.Models.baseModel;

                import dev.langchain4j.agentic.AgenticServices;
                import dev.langchain4j.agentic.Agents;
                import dev.langchain4j.agentic.planner.AgentInstance;
                import dev.langchain4j.agentic.planner.AgentsRegistry;
                import dev.langchain4j.model.openai.OpenAiChatModel;
                import dev.langchain4j.model.openai.OpenAiChatModelName;
                import dev.langchain4j.model.chat.ChatModel;
                import java.util.Map;

                public class WriterAgentsRegistry implements AgentsRegistry {

                    private final Map<String, AgentInstance> agents;

                    public WriterAgentsRegistry() {

                        AgentInstance audienceEditor = (AgentInstance) AgenticServices
                                .agentBuilder(Agents.AudienceEditor.class)
                                .chatModel(baseModel())
                                .outputKey("story")
                                .build();

                        AgentInstance styleEditor = (AgentInstance) AgenticServices
                                .agentBuilder(Agents.StyleEditor.class)
                                .chatModel(baseModel())
                                .outputKey("story")
                                .build();

                        this.agents = Map.of(
                                "audienceEditor", audienceEditor,
                                "styleEditor", styleEditor);
                    }

                    @Override
                    public Map<String, AgentInstance> allAgents() {
                        return agents;
                    }

                    @Override
                    public AgentInstance getAgent(String name) {
                        AgentInstance agent = agents.get(name);
                        if (agent == null) {
                            throw new RuntimeException("No agent found with name: " + name);
                        }
                        return agent;
                    }

                }
                """;
    }
}
