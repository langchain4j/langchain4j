package dev.langchain4j.model.bedrock;

import java.util.Objects;

public class GuardrailAssessment {
    private final Action action;
    private final Policy policy;
    private final String name;

    public GuardrailAssessment(Builder<?> builder) {
        this.action = builder.action;
        this.name = builder.name;
        this.policy = builder.policy;
    }

    public Action action() {
        return action;
    }

    public Policy policy() {
        return policy;
    }

    public String name() {
        return name;
    }

    public static Builder<?> builder() {
        return new Builder<>();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GuardrailAssessment that = (GuardrailAssessment) o;
        return action == that.action && policy == that.policy && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, policy, name);
    }

    @Override
    public String toString() {
        return "GuardrailAssessment{" + "action=" + action + ", policy=" + policy + ", name='" + name + '\'' + '}';
    }

    public enum Policy {
        TOPIC,
        CONTENT,
        WORD,
        SENSITIVE,
        CONTEXT
    }

    public enum Action {
        ANONYMIZED,
        BLOCKED,
        NONE,
        UNKNOWN
    }

    public static class Builder<T extends Builder<T>> {
        private Policy policy;
        private String name;
        private Action action = Action.UNKNOWN;

        public T policy(Policy policy) {
            this.policy = policy;
            return (T) this;
        }

        public T name(String name) {
            this.name = name;
            return (T) this;
        }

        public T action(Action action) {
            this.action = action;
            return (T) this;
        }

        public T action(String action) {
            if (action != null) {
                try {
                    this.action = Enum.valueOf(Action.class, action);
                } catch (IllegalArgumentException ignored) {
                    this.action = Action.UNKNOWN;
                }
            }
            return (T) this;
        }

        public GuardrailAssessment build() {
            return new GuardrailAssessment(this);
        }
    }
}
