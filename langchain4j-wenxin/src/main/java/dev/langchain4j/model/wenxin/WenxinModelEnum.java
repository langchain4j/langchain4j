package dev.langchain4j.model.wenxin;

import lombok.Getter;

/**
 * Baidu ERNIE Bot big model, the most perfect model in China at present
 */
@Getter
public enum WenxinModelEnum {

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
    CodeLlama_7b_Instruct("CodeLlama-7b-Instruct", "codellama_7b_instruct"),
    AquilaCode_multi("AquilaCode-multi", "bge_large_en")
    ;

    private String modelName;

    private  String serviceName;

    WenxinModelEnum(String modelName, String serviceName) {
        this.modelName = modelName;
        this.serviceName = serviceName;
    }


    public static WenxinModelEnum fromModelName(String modelName) {
        for (WenxinModelEnum wenxinModelEnum : WenxinModelEnum.values()) {
            if (wenxinModelEnum.getModelName().equals(modelName)) {
                return wenxinModelEnum;
            }
        }
        return null;
    }

    public static String getServiceName(String modelName) {
        for (WenxinModelEnum wenxinModelEnum : WenxinModelEnum.values()) {
            if (wenxinModelEnum.getModelName().equals(modelName)) {
                return wenxinModelEnum.getServiceName();
            }
        }
        return null;
    }
}
