package dev.langchain4j.skills;

public class SkillsConfig {

    private final boolean allowRun;

    private SkillsConfig(Builder builder) {
        this.allowRun = builder.allowRun;
    }

    public boolean allowRun() {
        return allowRun;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean allowRun;

        public Builder allowRun(boolean allowRun) {
            this.allowRun = allowRun;
            return this;
        }

        public SkillsConfig build() {
            return new SkillsConfig(this);
        }
    }
}
