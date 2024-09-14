package dev.langchain4j.model.sparkdesk.client.message;

import dev.langchain4j.model.sparkdesk.client.Role;

public interface Message {
    Role getRole();
}
