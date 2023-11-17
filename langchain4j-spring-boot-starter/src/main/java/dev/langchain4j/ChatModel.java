package dev.langchain4j;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

class ChatModel {

    @NestedConfigurationProperty
    private ModelProvider provider;
    @NestedConfigurationProperty
    private OpenAi openAi;
    @NestedConfigurationProperty
    private HuggingFace huggingFace;
    @NestedConfigurationProperty
    private LocalAi localAi;

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

    public HuggingFace getHuggingFace() {
        return huggingFace;
    }

    public void setHuggingFace(HuggingFace huggingFace) {
        this.huggingFace = huggingFace;
    }

    public LocalAi getLocalAi() {
        return localAi;
    }

    public void setLocalAi(LocalAi localAi) {
        this.localAi = localAi;
    }
}