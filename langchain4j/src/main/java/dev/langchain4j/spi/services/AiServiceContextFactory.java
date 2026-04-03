package dev.langchain4j.spi.services;

import dev.langchain4j.Internal;
import dev.langchain4j.service.AiServiceContext;

@Internal
public interface AiServiceContextFactory {

    AiServiceContext create(Class<?> aiServiceClass);
}
