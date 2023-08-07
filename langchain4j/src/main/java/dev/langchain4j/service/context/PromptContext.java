package dev.langchain4j.service.context;

import dev.langchain4j.model.input.PromptTemplate;

public interface PromptContext extends BaseAiServiceContext {

    PromptTemplate getPromptTemplate();

}
