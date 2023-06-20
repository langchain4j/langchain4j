package dev.langchain4j.agent;

import java.util.Optional;

public interface Tool {

    String id();

    String description();

    Optional<String> execute(String input);
}
