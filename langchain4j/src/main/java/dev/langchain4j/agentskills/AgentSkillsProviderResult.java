package dev.langchain4j.agentskills;

import dev.langchain4j.Experimental;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

/**
 * Result object from {@link AgentSkillsProvider#provideSkills(AgentSkillsProviderRequest)}.
 * <p>
 * Contains the list of skills that are available for the current request context.
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Experimental
public class AgentSkillsProviderResult {

    private final List<Skill> skills;

    private AgentSkillsProviderResult(Builder builder) {
        this.skills = builder.skills != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.skills))
                : Collections.emptyList();
    }

    /**
     * Returns the list of available skills.
     *
     * @return unmodifiable list of skills
     */
    public List<Skill> skills() {
        return skills;
    }

    /**
     * Returns true if there are any skills available.
     *
     * @return true if skills are available
     */
    public boolean hasSkills() {
        return !isNullOrEmpty(skills);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<Skill> skills;

        public Builder skills(List<Skill> skills) {
            this.skills = skills;
            return this;
        }

        public Builder addSkill(Skill skill) {
            if (this.skills == null) {
                this.skills = new ArrayList<>();
            }
            this.skills.add(skill);
            return this;
        }

        public AgentSkillsProviderResult build() {
            return new AgentSkillsProviderResult(this);
        }
    }
}
