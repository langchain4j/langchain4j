package dev.langchain4j.skills;

public class DefaultSkill extends AbstractSkill {

    public DefaultSkill(Builder builder) {
        super(builder);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractSkill.BaseBuilder<Builder> {
        public DefaultSkill build() {
            return new DefaultSkill(this);
        }
    }
}