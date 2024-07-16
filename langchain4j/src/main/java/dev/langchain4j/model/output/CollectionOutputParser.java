package dev.langchain4j.model.output;

import java.util.Collection;

public abstract class CollectionOutputParser<T extends Collection<?>> implements OutputParser<T> {
    @Override
    public String formatInstructions() {
        return "\nYou must put every item on a separate line.";
    }
}
