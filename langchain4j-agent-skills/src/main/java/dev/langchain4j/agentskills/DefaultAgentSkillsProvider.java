package dev.langchain4j.agentskills;

import dev.langchain4j.Experimental;
import dev.langchain4j.agentskills.loader.FileSystemSkillLoader;
import dev.langchain4j.agentskills.loader.SkillLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

/**
 * Default implementation of {@link AgentSkillsProvider} that loads skills from
 * the file system.
 * <p>
 * Skills are loaded lazily on first access and cached for subsequent requests.
 * <p>
 * Example usage:
 * <pre>{@code
 * AgentSkillsProvider provider = DefaultAgentSkillsProvider.builder()
 *     .skillDirectories(Path.of("/path/to/skills"))
 *     .build();
 * }</pre>
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Experimental
public class DefaultAgentSkillsProvider implements AgentSkillsProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentSkillsProvider.class);

    private final List<Path> skillDirectories;
    private final SkillLoader skillLoader;
    private final Map<String, Skill> skillCache;
    private volatile boolean initialized = false;

    private DefaultAgentSkillsProvider(Builder builder) {
        this.skillDirectories = Collections.unmodifiableList(
                new ArrayList<>(ensureNotEmpty(builder.skillDirectories, "skillDirectories")));
        this.skillLoader = builder.skillLoader != null
                ? builder.skillLoader
                : new FileSystemSkillLoader();
        this.skillCache = new ConcurrentHashMap<>();
    }

    @Override
    public AgentSkillsProviderResult provideSkills(AgentSkillsProviderRequest request) {
        ensureInitialized();
        return AgentSkillsProviderResult.builder()
                .skills(new ArrayList<>(skillCache.values()))
                .build();
    }

    @Override
    public List<Skill> allSkills() {
        ensureInitialized();
        return new ArrayList<>(skillCache.values());
    }

    @Override
    public Skill skillByName(String name) {
        if (name == null) {
            return null;
        }
        ensureInitialized();
        return skillCache.get(name);
    }

    /**
     * Reloads all skills from the configured directories.
     * <p>
     * This clears the cache and reloads all skills.
     * The operation is atomic - other threads will see either the old or the new skill set,
     * never an empty or partial set.
     */
    public synchronized void reload() {
        // Load into a temporary map first
        Map<String, Skill> newCache = loadAllSkills();
        
        // Atomic swap
        skillCache.clear();
        skillCache.putAll(newCache);
        initialized = true;
    }

    private synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }

        Map<String, Skill> newCache = loadAllSkills();
        skillCache.putAll(newCache);
        initialized = true;
    }

    private Map<String, Skill> loadAllSkills() {
        log.info("Loading skills from {} directories", skillDirectories.size());
        Map<String, Skill> result = new HashMap<>();

        for (Path directory : skillDirectories) {
            try {
                List<Skill> skills = skillLoader.loadSkillsFromDirectory(directory);
                for (Skill skill : skills) {
                    if (result.containsKey(skill.name())) {
                        log.warn("Duplicate skill name '{}', skipping from {}", skill.name(), skill.path());
                    } else {
                        result.put(skill.name(), skill);
                        log.info("Loaded skill: {} from {}", skill.name(), skill.path());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to load skills from directory: {}", directory, e);
            }
        }

        log.info("Loaded {} skills total", result.size());
        return result;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<Path> skillDirectories = new ArrayList<>();
        private SkillLoader skillLoader;

        /**
         * Sets the directories to scan for skills.
         *
         * @param skillDirectories list of directories
         * @return this builder
         */
        public Builder skillDirectories(List<Path> skillDirectories) {
            this.skillDirectories = new ArrayList<>(skillDirectories);
            return this;
        }

        /**
         * Sets the directories to scan for skills.
         *
         * @param skillDirectories directories
         * @return this builder
         */
        public Builder skillDirectories(Path... skillDirectories) {
            this.skillDirectories = new ArrayList<>(Arrays.asList(skillDirectories));
            return this;
        }

        /**
         * Adds a directory to scan for skills.
         *
         * @param directory the directory to add (must not be null)
         * @return this builder
         * @throws IllegalArgumentException if directory is null
         */
        public Builder addSkillDirectory(Path directory) {
            if (directory == null) {
                throw new IllegalArgumentException("directory cannot be null");
            }
            this.skillDirectories.add(directory);
            return this;
        }

        /**
         * Sets a custom skill loader.
         *
         * @param skillLoader the loader to use
         * @return this builder
         */
        public Builder skillLoader(SkillLoader skillLoader) {
            this.skillLoader = skillLoader;
            return this;
        }

        public DefaultAgentSkillsProvider build() {
            return new DefaultAgentSkillsProvider(this);
        }
    }
}
