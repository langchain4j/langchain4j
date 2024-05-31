package dev.langchain4j.model.azure;

/**
 * You can get the latest model names from the Azure OpenAI documentation or by executing the Azure CLI command:
 * az cognitiveservices account list-models --resource-group "$RESOURCE_GROUP" --name "$AI_SERVICE" -o table
 */
public enum AzureOpenAiChatModelName {

    GPT_3_5_TURBO("gpt-35-turbo"), // alias for the latest gpt-3.5-turbo model
    GPT_3_5_TURBO_0301("gpt-35-turbo-0301"), // 4k context, functions
    GPT_3_5_TURBO_0613("gpt-35-turbo-0613"), // 4k context, functions
    GPT_3_5_TURBO_1106("gpt-35-turbo-1106"), // 16k context, functions

    GPT_3_5_TURBO_16K("gpt-35-turbo-16k"), // alias for the latest gpt-3.5-turbo-16k model
    GPT_3_5_TURBO_16K_0613("gpt-35-turbo-16k-0613"), // 16k context, functions

    GPT_4("gpt-4"), // alias for the latest gpt-4
    GPT_4_0613("gpt-4-0613"), // 8k context, functions
    GPT_4_0125_PREVIEW("gpt-4-0125-preview"), // 8k context
    GPT_4_1106_PREVIEW("gpt-4-1106-preview"), // 8k context

    GPT_4_TURBO("gpt-4-turbo"), // alias for the latest gpt-4-turbo model
    GPT_4_TURBO_2024_04_09("gpt-4-turbo-2024-04-09"), // alias for the latest gpt-4-turbo model

    GPT_4_32K("gpt-4-32k"), // alias for the latest gpt-32k model
    GPT_4_32K_0613("gpt-4-32k-0613"), // 32k context, functions

    GPT_4_VISION_PREVIEW("gpt-4-vision-preview"),

    GPT_4_O("gpt-4o");  // alias for the latest gpt-4o model

    private final String stringValue;

    AzureOpenAiChatModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
