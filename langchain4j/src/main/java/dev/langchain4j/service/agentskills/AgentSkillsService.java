package dev.langchain4j.service.agentskills;

import dev.langchain4j.Experimental;
import dev.langchain4j.Internal;
import dev.langchain4j.agentskills.AgentSkillsProvider;
import dev.langchain4j.agentskills.AgentSkillsProviderRequest;
import dev.langchain4j.agentskills.AgentSkillsProviderResult;
import dev.langchain4j.agentskills.Skill;
import dev.langchain4j.agentskills.execution.DefaultScriptExecutor;
import dev.langchain4j.agentskills.execution.ScriptExecutionResult;
import dev.langchain4j.agentskills.execution.ScriptExecutor;
import dev.langchain4j.agentskills.instruction.AgentSkillsInstruction;
import dev.langchain4j.agentskills.instruction.ExecuteScriptInstruction;
import dev.langchain4j.agentskills.instruction.ReadResourceInstruction;
import dev.langchain4j.agentskills.instruction.UseSkillInstruction;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service class that manages Agent Skills integration with AI Services.
 * <p>
 * This service handles:
 * <ul>
 *   <li>Generating system prompt additions with available skills</li>
 *   <li>Parsing LLM responses for skill instructions</li>
 *   <li>Executing skill-related operations (load skill, execute script, read resource)</li>
 * </ul>
 * <p>
 * The service recognizes the following instructions in LLM responses:
 * <ul>
 *   <li>{@code <use_skill>skill-name</use_skill>} - Load a skill's full content</li>
 *   <li>{@code <execute_script skill="skill-name">command</execute_script>} - Execute a script</li>
 *   <li>{@code <read_resource skill="skill-name">path</read_resource>} - Read a resource file</li>
 * </ul>
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Internal
@Experimental
public class AgentSkillsService {

    private static final Logger log = LoggerFactory.getLogger(AgentSkillsService.class);

    // Instruction patterns
    private static final Pattern USE_SKILL_PATTERN =
            Pattern.compile("<use_skill>([a-z0-9-]+)</use_skill>");
    private static final Pattern EXECUTE_SCRIPT_PATTERN =
            Pattern.compile("<execute_script\\s+skill=\"([a-z0-9-]+)\">(.*?)</execute_script>", Pattern.DOTALL);
    private static final Pattern READ_RESOURCE_PATTERN =
            Pattern.compile("<read_resource\\s+skill=\"([a-z0-9-]+)\">(.*?)</read_resource>", Pattern.DOTALL);

    private AgentSkillsProvider agentSkillsProvider;
    private volatile ScriptExecutor scriptExecutor;
    private int maxIterations = 10;
    private final Object scriptExecutorLock = new Object();

    /**
     * Sets the agent skills provider.
     *
     * @param agentSkillsProvider the provider to set
     */
    public void agentSkillsProvider(AgentSkillsProvider agentSkillsProvider) {
        this.agentSkillsProvider = agentSkillsProvider;
    }

    /**
     * Returns the configured agent skills provider.
     *
     * @return the provider, or null if not configured
     */
    public AgentSkillsProvider agentSkillsProvider() {
        return agentSkillsProvider;
    }

    /**
     * Sets the script executor for running skill scripts.
     *
     * @param scriptExecutor the executor to set
     */
    public void scriptExecutor(ScriptExecutor scriptExecutor) {
        this.scriptExecutor = scriptExecutor;
    }

    /**
     * Returns the script executor, creating a default one if not configured.
     * <p>
     * This method is thread-safe.
     *
     * @return the script executor
     */
    public ScriptExecutor scriptExecutor() {
        ScriptExecutor executor = scriptExecutor;
        if (executor == null) {
            synchronized (scriptExecutorLock) {
                executor = scriptExecutor;
                if (executor == null) {
                    executor = new DefaultScriptExecutor();
                    scriptExecutor = executor;
                }
            }
        }
        return executor;
    }

    /**
     * Sets the maximum number of skill instruction iterations.
     *
     * @param maxIterations the maximum iterations (must be at least 1)
     * @throws IllegalArgumentException if maxIterations is less than 1
     */
    public void maxIterations(int maxIterations) {
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations must be at least 1, but was: " + maxIterations);
        }
        this.maxIterations = maxIterations;
    }

    /**
     * Returns the maximum number of skill instruction iterations.
     *
     * @return the maximum iterations
     */
    public int maxIterations() {
        return maxIterations;
    }

    /**
     * Returns true if an agent skills provider is configured.
     *
     * @return true if skills are available
     */
    public boolean hasSkills() {
        return agentSkillsProvider != null;
    }

    /**
     * Generates the system prompt addition containing available skills.
     *
     * @param invocationContext the invocation context
     * @param userMessage       the user message
     * @return the system prompt addition, or empty string if no skills
     */
    public String generateSystemPromptAddition(InvocationContext invocationContext, UserMessage userMessage) {
        if (agentSkillsProvider == null) {
            return "";
        }

        AgentSkillsProviderRequest request = AgentSkillsProviderRequest.builder()
                .invocationContext(invocationContext)
                .userMessage(userMessage)
                .build();

        AgentSkillsProviderResult result = agentSkillsProvider.provideSkills(request);

        if (result == null || !result.hasSkills()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n<available_skills>\n");

        for (Skill skill : result.skills()) {
            sb.append("  <skill>\n");
            sb.append("    <name>").append(escapeXml(skill.name())).append("</name>\n");
            sb.append("    <description>").append(escapeXml(skill.description())).append("</description>\n");
            sb.append("  </skill>\n");
        }

        sb.append("</available_skills>\n\n");
        sb.append("When you need to use a skill to complete a task, please use the following instructions:\n");
        sb.append("- Load skill content: <use_skill>skill-name</use_skill>\n");
        sb.append("- Execute skill script: <execute_script skill=\"skill-name\">script-command</execute_script>\n");
        sb.append("- Read skill resource: <read_resource skill=\"skill-name\">resource-path</read_resource>\n");
        sb.append("The system will automatically execute these instructions and return the results to you.\n");

        return sb.toString();
    }

    /**
     * Parses the LLM response for skill instructions.
     *
     * @param llmResponse the LLM response text
     * @return the parsed instruction, or null if no instruction found
     */
    public AgentSkillsInstruction parseInstruction(String llmResponse) {
        if (llmResponse == null || llmResponse.isEmpty()) {
            return null;
        }

        // Check for use_skill instruction first
        Matcher useMatcher = USE_SKILL_PATTERN.matcher(llmResponse);
        if (useMatcher.find()) {
            String skillName = useMatcher.group(1);
            if (skillName != null && !skillName.isBlank()) {
                return new UseSkillInstruction(skillName);
            }
        }

        // Check for execute_script instruction
        Matcher execMatcher = EXECUTE_SCRIPT_PATTERN.matcher(llmResponse);
        if (execMatcher.find()) {
            String skillName = execMatcher.group(1);
            String command = execMatcher.group(2).trim();
            if (skillName != null && !skillName.isBlank() && !command.isBlank()) {
                return new ExecuteScriptInstruction(skillName, command);
            }
        }

        // Check for read_resource instruction
        Matcher readMatcher = READ_RESOURCE_PATTERN.matcher(llmResponse);
        if (readMatcher.find()) {
            String skillName = readMatcher.group(1);
            String resourcePath = readMatcher.group(2).trim();
            if (skillName != null && !skillName.isBlank() && !resourcePath.isBlank()) {
                return new ReadResourceInstruction(skillName, resourcePath);
            }
        }

        return null;
    }

    /**
     * Handles a use_skill instruction.
     *
     * @param skillName the skill name to load
     * @return the skill content wrapped in XML tags
     */
    public String handleUseSkill(String skillName) {
        log.debug("Loading skill: {}", skillName);

        Skill skill = agentSkillsProvider.skillByName(skillName);
        if (skill == null) {
            log.warn("Skill not found: {}", skillName);
            return "<skill_error>Skill '" + escapeXml(skillName) + "' not found</skill_error>";
        }

        String instructions = skill.instructions();
        if (instructions == null || instructions.isBlank()) {
            log.warn("Skill '{}' has no instructions", skillName);
            return "<skill_error>Skill '" + escapeXml(skillName) + "' has no instructions</skill_error>";
        }

        return "<skill_content name=\"" + escapeXml(skillName) + "\">\n"
                + instructions + "\n"
                + "</skill_content>\n\n"
                + "Please use the above skill content to help the user complete the task.";
    }

    /**
     * Handles an execute_script instruction.
     *
     * @param skillName the skill name
     * @param command   the script command to execute
     * @return the execution result wrapped in XML tags
     */
    public String handleExecuteScript(String skillName, String command) {
        log.debug("Executing script for skill {}: {}", skillName, command);

        if (command == null || command.isBlank()) {
            log.warn("Empty command for skill: {}", skillName);
            return "<script_error>Command cannot be empty</script_error>";
        }

        Skill skill = agentSkillsProvider.skillByName(skillName);
        if (skill == null) {
            log.warn("Skill not found for script execution: {}", skillName);
            return "<script_error>Skill '" + escapeXml(skillName) + "' not found</script_error>";
        }

        // Security check: verify command is in allowed-tools (if configured)
        if (skill.allowedTools() != null) {
            if (!isCommandAllowed(command, skill.allowedTools())) {
                log.warn("Command not in allowed-tools for skill {}: {}", skillName, command);
                return "<script_error>Command not in allowed-tools list</script_error>";
            }
        }

        try {
            // workingDirectory is skill root, command should include relative path like "scripts/extract.py"
            ScriptExecutionResult result = scriptExecutor().execute(skill.path(), command);

            StringBuilder response = new StringBuilder();
            response.append("<script_result exit_code=\"").append(result.exitCode()).append("\">\n");

            if (!result.output().isEmpty()) {
                response.append(result.output()).append("\n");
            }

            if (!result.error().isEmpty()) {
                response.append("STDERR:\n").append(result.error()).append("\n");
            }

            response.append("</script_result>");
            return response.toString();

        } catch (Exception e) {
            log.error("Script execution failed for skill {}: {}", skillName, command, e);
            return "<script_error>Script execution failed: " + escapeXml(e.getMessage()) + "</script_error>";
        }
    }

    /**
     * Handles a read_resource instruction.
     *
     * @param skillName    the skill name
     * @param resourcePath the resource path relative to skill directory
     * @return the resource content wrapped in XML tags
     */
    public String handleReadResource(String skillName, String resourcePath) {
        log.debug("Reading resource for skill {}: {}", skillName, resourcePath);

        if (resourcePath == null || resourcePath.isBlank()) {
            log.warn("Empty resource path for skill: {}", skillName);
            return "<resource_error>Resource path cannot be empty</resource_error>";
        }

        Skill skill = agentSkillsProvider.skillByName(skillName);
        if (skill == null) {
            log.warn("Skill not found for resource reading: {}", skillName);
            return "<resource_error>Skill '" + escapeXml(skillName) + "' not found</resource_error>";
        }

        try {
            Path fullPath = skill.path().resolve(resourcePath).normalize();

            // Security check: ensure path is within skill directory (prevent path traversal)
            // Using normalize() first for basic check
            if (!fullPath.startsWith(skill.path().normalize())) {
                log.warn("Path traversal attempt detected: {}", resourcePath);
                return "<resource_error>Illegal path</resource_error>";
            }

            if (!Files.exists(fullPath)) {
                log.warn("Resource not found: {}", fullPath);
                return "<resource_error>Resource file not found: " + escapeXml(resourcePath) + "</resource_error>";
            }

            // Resolve symlinks and check again to prevent symlink attacks
            Path realPath = fullPath.toRealPath();
            Path realSkillPath = skill.path().toRealPath();
            if (!realPath.startsWith(realSkillPath)) {
                log.warn("Symlink escape attempt detected: {} -> {}", resourcePath, realPath);
                return "<resource_error>Illegal path</resource_error>";
            }

            if (!Files.isRegularFile(realPath)) {
                return "<resource_error>Path is not a file: " + escapeXml(resourcePath) + "</resource_error>";
            }

            String content = Files.readString(realPath);
            return "<resource_content path=\"" + escapeXml(resourcePath) + "\">\n"
                    + content + "\n"
                    + "</resource_content>";

        } catch (IOException e) {
            log.error("Failed to read resource for skill {}: {}", skillName, resourcePath, e);
            return "<resource_error>Failed to read resource: " + escapeXml(e.getMessage()) + "</resource_error>";
        }
    }

    /**
     * Handles any skill instruction.
     *
     * @param instruction the instruction to handle
     * @return the result of handling the instruction
     */
    public String handleInstruction(AgentSkillsInstruction instruction) {
        if (instruction instanceof UseSkillInstruction use) {
            return handleUseSkill(use.skillName());
        } else if (instruction instanceof ExecuteScriptInstruction exec) {
            return handleExecuteScript(exec.skillName(), exec.command());
        } else if (instruction instanceof ReadResourceInstruction read) {
            return handleReadResource(read.skillName(), read.resourcePath());
        }
        return "<error>Unknown instruction type</error>";
    }

    private boolean isCommandAllowed(String command, List<String> allowedTools) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return true;
        }

        for (String allowed : allowedTools) {
            // Handle wildcard patterns
            if (allowed.equals("*")) {
                return true;
            }
            if (allowed.endsWith("*")) {
                String prefix = allowed.substring(0, allowed.length() - 1);
                if (command.startsWith(prefix)) {
                    return true;
                }
            } else if (command.equals(allowed) || command.startsWith(allowed + " ")) {
                return true;
            }
        }
        return false;
    }

    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
