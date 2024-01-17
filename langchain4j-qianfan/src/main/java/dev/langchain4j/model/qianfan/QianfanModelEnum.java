package dev.langchain4j.model.qianfan;

import lombok.Getter;

/**
 * Baidu ERNIE Bot big model, the most perfect model in China at present
 */
@Getter
public enum QianfanModelEnum {

    ERNIE_BOT("ERNIE-Bot", "completions"),
    ERNIE_BOT_4("ERNIE-Bot 4.0", "completions_pro"),
    ERNIE_BOT_8("ERNIE-Bot-8K", "ernie_bot_8k"),
    ERNIE_BOT_TURBO("ERNIE-Bot-turbo", "eb-instant"),
    EB_TURBO_APPBUILDER("EB-turbo-AppBuilder", "ai_apaas"),
    EMBEDDING_V1("Embedding-V1", "embedding-v1"),
    BGE_LARGE_ZH("bge-large-zh", "bge_large_zh"),
    BGE_LARGE_EN("bge-large-en", "bge_large_en"),
    TAO_8K("tao-8k", "tao_8k"),
    SQLCODER_7B("SQLCoder-7B", "sqlcoder_7b"),
    CODELLAMA_7B_INSTRUCT("CodeLlama-7b-Instruct", "codellama_7b_instruct"),
    AQUILACODE_MULTI("AquilaCode-multi", "bge_large_en"),
    BLOOMZ_7B("BLOOMZ-7B","bloomz_7b1"),
    LLAMA_2_7B_CHAT("Llama-2-7b-chat", "llama_2_7b"),
    LLAMA_2_13B_CHAT("Llama-2-13b-chat", "llama_2_13b"),
    LLAMA_2_70B_CHAT("Llama-2-70b-chat", "llama_2_70b"),
    QIANFAN_BLOOMZ_7B_COMPRESSED("Qianfan-BLOOMZ-7B-compressed", "qianfan_bloomz_7b_compressed"),
    QIANFAN_CHINESE_LLAMA_2_7B("Qianfan-Chinese-Llama-2-7B", "qianfan_chinese_llama_2_7b"),
    CHATGLM2_6B_32K("ChatGLM2-6B-32K", "chatglm2_6b_32k"),
    AQUILACHAT_7B("AquilaChat-7B", "aquilachat_7b")
        ;

    private String modelName;

    private String endpoint;

    QianfanModelEnum(String modelName, String endpoint) {
        this.modelName = modelName;
        this.endpoint = endpoint;
    }


    public static QianfanModelEnum fromModelName(String modelName) {
        for (QianfanModelEnum qianfanModelEnum : QianfanModelEnum.values()) {
            if (qianfanModelEnum.getModelName().equals(modelName)) {
                return qianfanModelEnum;
            }
        }
        return null;
    }

    public static String getEndpoint(String modelName) {
        for (QianfanModelEnum qianfanModelEnum : QianfanModelEnum.values()) {
            if (qianfanModelEnum.getModelName().equals(modelName)) {
                return qianfanModelEnum.getEndpoint();
            }
        }
        return null;
    }
}
