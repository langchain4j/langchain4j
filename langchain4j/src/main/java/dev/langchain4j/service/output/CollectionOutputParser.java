package dev.langchain4j.service.output;

import java.util.Collection;

abstract class CollectionOutputParser<T extends Collection<?>> implements OutputParser<T> {

    @Override
    public String formatInstructions() {
        return "\nYou must put every item on a separate line.";
    }
}
