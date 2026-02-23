package dev.langchain4j.skills;

public class SkillsConfig {

    private final boolean allowRunScripts;

    private SkillsConfig(Builder builder) {
        this.allowRunScripts = builder.allowRunScripts;
    }

    public boolean allowRunScripts() { // TODO names
        return allowRunScripts;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean allowRunScripts;

        public Builder allowRunScripts(boolean allowRunScripts) {
            this.allowRunScripts = allowRunScripts;
            return this;
        }

        public SkillsConfig build() {
            return new SkillsConfig(this);
        }
    }
}
