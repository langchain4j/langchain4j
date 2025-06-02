package dev.langchain4j.model.vertexai.gemini;

import com.google.cloud.vertexai.api.HarmCategory;
import com.google.cloud.vertexai.api.SafetySetting.HarmBlockThreshold;
import com.google.cloud.vertexai.api.SafetySetting;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps between Vertex AI <code>SafetSetting</code> and LangChain4j
 * <code>HarmCategoty</code> and <code>SafetyThreshold</code>
 */
class SafetySettingsMapper {
    static List<SafetySetting> mapSafetySettings(Map<dev.langchain4j.model.vertexai.gemini.HarmCategory, SafetyThreshold> safetySettingsMap) {
        return safetySettingsMap.entrySet().stream()
            .map(entry -> {
                SafetySetting.Builder safetySettingBuilder = SafetySetting.newBuilder();
                safetySettingBuilder.setCategory(map(entry.getKey()));
                safetySettingBuilder.setThreshold(map(entry.getValue()));
                return safetySettingBuilder.build();
            })
            .collect(Collectors.toList());
    }

    private static HarmCategory map(dev.langchain4j.model.vertexai.gemini.HarmCategory harmCategory) {
        return HarmCategory.valueOf(harmCategory.name());
    }

    private static HarmBlockThreshold map(SafetyThreshold safetyThreshold) {
        return HarmBlockThreshold.valueOf(safetyThreshold.name());
    }
}
