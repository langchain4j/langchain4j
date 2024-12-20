package dev.langchain4j.model.chat.common;

import java.util.Set;

public record StreamingMetadata(String concatenatedPartialResponses,
                                int timesOnPartialResponseWasCalled,
                                int timesOnCompleteResponseWasCalled,
                                Set<Thread> threads
) {
}
