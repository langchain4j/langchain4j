package dev.langchain4j;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

class ModerationModel {

    @NestedConfigurationProperty
    private ModelProvider provider;
    @NestedConfigurationProperty
    private OpenAi openAi;

    public ModelProvider getProvider() {
        return provider;
    }

    public void setProvider(ModelProvider provider) {
        this.provider = provider;
    }

    public OpenAi getOpenAi() {
        return openAi;
    }

    public void setOpenAi(OpenAi openAi) {
        this.openAi = openAi;
    }
}