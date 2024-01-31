package dev.langchain4j.model.qianfan;

import lombok.Getter;

@Getter
public enum QianfanChatModelNameEnum {

    ERNIE_BOT("ERNIE-Bot", "completions"),
    ERNIE_BOT_4("ERNIE-Bot 4.0", "completions_pro"),
    ERNIE_BOT_8("ERNIE-Bot-8K", "ernie_bot_8k"),
    ERNIE_BOT_TURBO("ERNIE-Bot-turbo", "eb-instant"),
    EB_TURBO_APPBUILDER("EB-turbo-AppBuilder", "ai_apaas"),
    YI_34B_CHAT("Yi-34B-Chat", "yi_34b_chat"),
    BLOOMZ_7B("BLOOMZ-7B","bloomz_7b1"),
    QIANFAN_BLOOMZ_7B_COMPRESSED("Qianfan-BLOOMZ-7B-compressed", "qianfan_bloomz_7b_compressed"),
    MIXTRAL_8X7B_INSTRUCT("Mixtral-8x7B-Instruct", "mixtral_8x7b_instruct"),
    LLAMA_2_7B_CHAT("Llama-2-7b-chat", "llama_2_7b"),
    LLAMA_2_13B_CHAT("Llama-2-13b-chat", "llama_2_13b"),
    LLAMA_2_70B_CHAT("Llama-2-70b-chat", "llama_2_70b"),
    QIANFAN_CHINESE_LLAMA_2_7B("Qianfan-Chinese-Llama-2-7B", "qianfan_chinese_llama_2_7b"),
    CHATGLM2_6B_32K("ChatGLM2-6B-32K", "chatglm2_6b_32k"),
    AQUILACHAT_7B("AquilaChat-7B", "aquilachat_7b")
    ;


    private String modelName;

    private String endpoint;

    QianfanChatModelNameEnum(String modelName, String endpoint) {
        this.modelName = modelName;
        this.endpoint = endpoint;
    }


    public static String getEndpoint(String modelName) {
        for (QianfanChatModelNameEnum qianfanChatModelNameEnum : QianfanChatModelNameEnum.values()) {
            if (qianfanChatModelNameEnum.getModelName().equals(modelName)) {
                return qianfanChatModelNameEnum.getEndpoint();
            }
        }
        return null;
    }
}
