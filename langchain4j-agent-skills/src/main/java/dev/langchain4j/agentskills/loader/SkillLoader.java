package dev.langchain4j.agentskills.loader;

import dev.langchain4j.Experimental;
import dev.langchain4j.agentskills.Skill;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for loading skills from various sources.
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Experimental
public interface SkillLoader {

    /**
     * Loads all skills from the specified directory.
     * <p>
     * The implementation should recursively scan for directories containing
     * a SKILL.md file.
     *
     * @param directory the directory to scan for skills
     * @return list of loaded skills
     */
    List<Skill> loadSkillsFromDirectory(Path directory);

    /**
     * Loads a single skill from the specified skill directory.
     *
     * @param skillDirectory the skill directory containing SKILL.md
     * @return the loaded skill, or null if loading failed
     */
    Skill loadSkill(Path skillDirectory);
}
