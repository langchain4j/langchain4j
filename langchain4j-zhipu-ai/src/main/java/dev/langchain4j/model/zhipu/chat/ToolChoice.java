package dev.langchain4j.model.zhipu.chat;

import java.util.Objects;

import static dev.langchain4j.model.zhipu.chat.ToolType.FUNCTION;

public class ToolChoice {

    private final ToolType type = FUNCTION;
    private final Function function;

    public ToolChoice(String functionName) {
        this.function = Function.builder().name(functionName).build();
    }

    public static ToolChoice from(String functionName) {
        return new ToolChoice(functionName);
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ToolChoice
                && equalTo((ToolChoice) another);
    }

    private boolean equalTo(ToolChoice another) {
        return Objects.equals(type, another.type)
                && Objects.equals(function, another.function);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(type);
        h += (h << 5) + Objects.hashCode(function);
        return h;
    }

    @Override
    public String toString() {
        return "ToolChoice{" +
                "type=" + type +
                ", function=" + function +
                "}";
    }
}