package dev.langchain4j.model.output;

import dev.langchain4j.internal.Json;

import java.util.Arrays;

public class EnumOutputParser implements OutputParser<Enum> {

    private final Class<? extends Enum> enumClass;

    public EnumOutputParser(Class<? extends Enum> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public Enum parse(String string) {
        return Json.fromJson(string, enumClass);
    }

    @Override
    public String formatInstructions() {
        return "one of " + Arrays.toString(enumClass.getEnumConstants());
    }
}
