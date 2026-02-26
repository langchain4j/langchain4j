package dev.langchain4j.skills;

import dev.langchain4j.service.tool.ToolProvider;

import java.util.Collection;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.skills.SkillUtils.createSystemMessage;
import static dev.langchain4j.skills.SkillUtils.createToolProvider;
import static java.util.Arrays.asList;

public class SkillService { // TODO name: SkillRepository?

    private final ToolProvider toolProvider;
    private final String systemMessage;

    public SkillService(Builder builder) {
        this.toolProvider = createToolProvider(builder.skills, getOrDefault(builder.allowRunningShellCommands, false));
        this.systemMessage = createSystemMessage(builder.skills);
    }

    public ToolProvider toolProvider() {
        return toolProvider;
    }

    public String systemMessage() {
        return systemMessage;
    }

    public static SkillService from(Collection<? extends Skill> skills) {
        return builder().skills(skills).build();
    }

    public static SkillService from(Skill... skills) {
        return builder().skills(skills).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Collection<? extends Skill> skills;
        private Boolean allowRunningShellCommands;

        public Builder skills(Collection<? extends Skill> skills) {
            this.skills = skills;
            return this;
        }

        public Builder skills(Skill... skills) {
            return skills(asList(skills));
        }

        public Builder allowRunningShellCommands(Boolean allowRunningShellCommands) { // TODO name
            this.allowRunningShellCommands = allowRunningShellCommands;
            return this;
        }

        public SkillService build() {
            return new SkillService(this);
        }
    }
}
