package dev.langchain4j.spi.services;

import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;

public interface AiServicesFactory {

    <T> AiServices<T> create(AiServiceContext context);
}
