package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgentsRegistry;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.ServiceLoader;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class AgentsRegistryTest {

    private static final String SPI_SERVICE_FILE =
            "META-INF/services/dev.langchain4j.agentic.planner.AgentsRegistry";

    @Test
    void default_registry_returns_empty_map() {
        AgentsRegistry registry = AgentsRegistry.get();
        assertThat(registry.allAgents()).isEmpty();
    }

    @Test
    void default_registry_throws_on_get_by_name() {
        AgentsRegistry registry = AgentsRegistry.get();
        assertThatThrownBy(() -> registry.getAgent("unknown"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void default_registry_throws_on_get_by_type() {
        AgentsRegistry registry = AgentsRegistry.get();
        assertThatThrownBy(() -> registry.getAgent(Runnable.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Runnable");
    }

    @Test
    void spi_loaded_registry_provides_agents() throws Exception {
        try (CompiledRegistry compiled = compileTestRegistry()) {
            AgentsRegistry registry = ServiceLoader.load(AgentsRegistry.class, compiled.classLoader())
                    .findFirst().orElseThrow();

            Map<String, AgentInstance> agents = registry.allAgents();
            assertThat(agents).containsKey("testAgent");
            assertThat(agents).hasSize(1);

            AgentInstance agent = registry.getAgent("testAgent");
            assertThat(agent.name()).isEqualTo("testAgent");
            assertThat(agent.description()).isEqualTo("A test agent from SPI");

            assertThatThrownBy(() -> registry.getAgent("nonexistent"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nonexistent");
        }
    }

    @Test
    void composite_registry_merges_multiple_providers() throws Exception {
        try (CompiledRegistry compiled = compileRegistries(
                registrySource("RegistryA", "agentA", "Agent A from registry A"),
                registrySource("RegistryB", "agentB", "Agent B from registry B"))) {

            ClassLoader originalCL = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(compiled.classLoader());
            AgentsRegistry.refresh();
            try {
                AgentsRegistry composite = AgentsRegistry.get();

                Map<String, AgentInstance> agents = composite.allAgents();
                assertThat(agents).hasSize(2);
                assertThat(agents).containsKeys("agentA", "agentB");

                assertThat(composite.getAgent("agentA").description()).isEqualTo("Agent A from registry A");
                assertThat(composite.getAgent("agentB").description()).isEqualTo("Agent B from registry B");

                assertThatThrownBy(() -> composite.getAgent("nonexistent"))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("nonexistent");
            } finally {
                Thread.currentThread().setContextClassLoader(originalCL);
                AgentsRegistry.refresh();
            }
        }
    }

    @Test
    void composite_registry_throws_on_duplicate_agent_names() throws Exception {
        try (CompiledRegistry compiled = compileRegistries(
                registrySource("RegistryA", "duplicateName", "First"),
                registrySource("RegistryB", "duplicateName", "Second"))) {

            ClassLoader originalCL = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(compiled.classLoader());
            try {
                assertThatThrownBy(AgentsRegistry::refresh)
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("duplicateName");
            } finally {
                Thread.currentThread().setContextClassLoader(originalCL);
                AgentsRegistry.refresh();
            }
        }
    }

    private static String registrySource(String className, String agentName, String agentDescription) {
        return """
                package dev.langchain4j.agentic.test;

                import dev.langchain4j.agentic.planner.AgentArgument;
                import dev.langchain4j.agentic.planner.AgentInstance;
                import dev.langchain4j.agentic.planner.AgentsRegistry;
                import dev.langchain4j.agentic.planner.AgenticSystemTopology;
                import dev.langchain4j.agentic.planner.Planner;
                import java.lang.reflect.Type;
                import java.util.List;
                import java.util.Map;

                public class %s implements AgentsRegistry {

                    private static final AgentInstance AGENT = new AgentInstance() {
                        @Override public Class<?> type() { return Object.class; }
                        @Override public Class<? extends Planner> plannerType() { return null; }
                        @Override public String name() { return "%s"; }
                        @Override public String agentId() { return "%s"; }
                        @Override public String description() { return "%s"; }
                        @Override public Type outputType() { return String.class; }
                        @Override public String outputKey() { return "output"; }
                        @Override public boolean async() { return false; }
                        @Override public List<AgentArgument> arguments() { return List.of(); }
                        @Override public AgentInstance parent() { return null; }
                        @Override public List<AgentInstance> subagents() { return List.of(); }
                        @Override public AgenticSystemTopology topology() { return AgenticSystemTopology.NON_AI_AGENT; }
                    };

                    @Override
                    public Map<String, AgentInstance> allAgents() { return Map.of("%s", AGENT); }

                    @Override
                    public AgentInstance getAgent(String name) {
                        if ("%s".equals(name)) return AGENT;
                        throw new RuntimeException("No agent found with name: " + name);
                    }

                    @Override
                    public <T> T getAgent(Class<T> agentType) {
                        throw new RuntimeException("No agent found with type: " + agentType.getName());
                    }
                }
                """.formatted(className, agentName, agentName, agentDescription, agentName, agentName);
    }

    private static CompiledRegistry compileRegistries(String... registrySources) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("These tests require a JDK").isNotNull();

        Path tempDir = Files.createTempDirectory("compiled-registry-");
        StringBuilder spiEntries = new StringBuilder();

        for (int i = 0; i < registrySources.length; i++) {
            String source = registrySources[i];
            String className = source.lines()
                    .filter(l -> l.contains("public class "))
                    .map(l -> l.replaceAll(".*public class (\\w+).*", "$1"))
                    .findFirst().orElseThrow();

            Path sourceFile = tempDir.resolve("dev/langchain4j/agentic/test/" + className + ".java");
            Files.createDirectories(sourceFile.getParent());
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

            int result = compiler.run(
                    null, null, null,
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", tempDir.toString(),
                    sourceFile.toString());
            assertThat(result).as("Compilation of %s must succeed", className).isZero();

            if (i > 0) spiEntries.append('\n');
            spiEntries.append("dev.langchain4j.agentic.test.").append(className);
        }

        Path servicesFile = tempDir.resolve(SPI_SERVICE_FILE);
        Files.createDirectories(servicesFile.getParent());
        Files.writeString(servicesFile, spiEntries.toString(), StandardCharsets.UTF_8);

        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{tempDir.toUri().toURL()}, AgentsRegistryTest.class.getClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (name.equals(SPI_SERVICE_FILE)) {
                    return findResources(name);
                }
                return super.getResources(name);
            }
        };
        return new CompiledRegistry(classLoader, tempDir);
    }

    private static CompiledRegistry compileTestRegistry() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("These tests require a JDK").isNotNull();

        Path tempDir = Files.createTempDirectory("compiled-registry-");

        Path sourceFile = tempDir.resolve("dev/langchain4j/agentic/test/TestAgentsRegistry.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, testRegistrySource(), StandardCharsets.UTF_8);

        int result = compiler.run(
                null, null, null,
                "-classpath", System.getProperty("java.class.path"),
                "-d", tempDir.toString(),
                sourceFile.toString());
        assertThat(result).as("Registry compilation must succeed").isZero();

        Path servicesFile = tempDir.resolve(SPI_SERVICE_FILE);
        Files.createDirectories(servicesFile.getParent());
        Files.writeString(servicesFile, "dev.langchain4j.agentic.test.TestAgentsRegistry", StandardCharsets.UTF_8);

        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{tempDir.toUri().toURL()}, AgentsRegistryTest.class.getClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (name.equals(SPI_SERVICE_FILE)) {
                    return findResources(name);
                }
                return super.getResources(name);
            }
        };
        return new CompiledRegistry(classLoader, tempDir);
    }

    private static String testRegistrySource() {
        return """
                package dev.langchain4j.agentic.test;

                import dev.langchain4j.agentic.planner.AgentArgument;
                import dev.langchain4j.agentic.planner.AgentInstance;
                import dev.langchain4j.agentic.planner.AgentsRegistry;
                import dev.langchain4j.agentic.planner.AgenticSystemTopology;
                import dev.langchain4j.agentic.planner.Planner;
                import java.lang.reflect.Type;
                import java.util.List;
                import java.util.Map;

                public class TestAgentsRegistry implements AgentsRegistry {

                    private static final AgentInstance TEST_AGENT = new AgentInstance() {
                        @Override public Class<?> type() { return Object.class; }
                        @Override public Class<? extends Planner> plannerType() { return null; }
                        @Override public String name() { return "testAgent"; }
                        @Override public String agentId() { return "testAgent"; }
                        @Override public String description() { return "A test agent from SPI"; }
                        @Override public Type outputType() { return String.class; }
                        @Override public String outputKey() { return "testOutput"; }
                        @Override public boolean async() { return false; }
                        @Override public List<AgentArgument> arguments() { return List.of(); }
                        @Override public AgentInstance parent() { return null; }
                        @Override public List<AgentInstance> subagents() { return List.of(); }
                        @Override public AgenticSystemTopology topology() { return AgenticSystemTopology.NON_AI_AGENT; }
                    };

                    private final Map<String, AgentInstance> agents = Map.of("testAgent", TEST_AGENT);

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

                    @Override
                    public <T> T getAgent(Class<T> agentType) {
                        throw new RuntimeException("No agent found with type: " + agentType.getName());
                    }
                }
                """;
    }

    private record CompiledRegistry(URLClassLoader classLoader, Path tempDir) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            classLoader.close();
            deleteRecursively(tempDir);
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
    }
}
