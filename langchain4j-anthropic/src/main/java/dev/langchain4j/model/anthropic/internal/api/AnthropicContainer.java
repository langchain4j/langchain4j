package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Objects;

/**
 * Represents the {@code container} block of an Anthropic create-message request, used to enable
 * <a href="https://docs.anthropic.com/en/docs/agents-and-tools/agent-skills/overview">Agent Skills</a>.
 */
@JsonInclude(NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicContainer {

    public List<AnthropicContainerSkill> skills;

    public AnthropicContainer() {}

    public AnthropicContainer(List<AnthropicContainerSkill> skills) {
        this.skills = skills;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AnthropicContainer that = (AnthropicContainer) o;
        return Objects.equals(skills, that.skills);
    }

    @Override
    public int hashCode() {
        return Objects.hash(skills);
    }

    @Override
    public String toString() {
        return "AnthropicContainer{skills=" + skills + '}';
    }

    /**
     * A single entry of the {@code container.skills} array, e.g.
     * {@code {"type":"anthropic","skill_id":"xlsx","version":"latest"}}.
     */
    @JsonInclude(NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class AnthropicContainerSkill {

        public String type;
        public String skillId;
        public String version;

        public AnthropicContainerSkill() {}

        public AnthropicContainerSkill(String type, String skillId, String version) {
            this.type = type;
            this.skillId = skillId;
            this.version = version;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            AnthropicContainerSkill that = (AnthropicContainerSkill) o;
            return Objects.equals(type, that.type)
                    && Objects.equals(skillId, that.skillId)
                    && Objects.equals(version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, skillId, version);
        }

        @Override
        public String toString() {
            return "AnthropicContainerSkill{type='" + type + "', skillId='" + skillId + "', version='" + version + "'}";
        }
    }
}
