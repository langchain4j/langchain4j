package dev.langchain4j.agentic.patterns.p2p;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.Collection;
import java.util.Map;

public interface VariablesExtractorAgent {

    @UserMessage(
            """
            Extract the values of the given list of variables from the provided text,
            returning a map where the keys are the variable names and the values are the corresponding extracted values.
            If a variable is not found in the text, it should not be included in the map.
            Be conservative in your extraction, only include values that are clearly present in the text.
            The text is: {{text}}
            The names of the variables to extract are: {{variableNames}}
            """)
    Map<String, String> extractVariables(@V("text") String text, @V("variableNames") Collection<String> variableNames);
}
