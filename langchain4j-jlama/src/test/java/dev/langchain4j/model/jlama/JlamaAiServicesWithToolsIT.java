package dev.langchain4j.model.jlama;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static java.util.Collections.singletonList;

class JlamaAiServicesWithToolsIT extends AiServicesWithNewToolsIT {
    static File tmpDir;

    @BeforeAll
    static void setup() {
        tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "jlama_tests");
        tmpDir.mkdirs();
    }

    @Override
    protected List<ChatLanguageModel> models() {
        return singletonList(
                JlamaChatModel.builder()
                        .modelName("Qwen/Qwen2.5-1.5B-Instruct")
                        .modelCachePath(tmpDir.toPath())
                        .temperature(0.0f)
                        .maxTokens(1024)
                        .build()
        );
    }

    @Test
    @Disabled("qwen2.5 struggles with this test scenario")
    @Override
    protected void should_execute_tool_with_list_of_POJOs_parameter() {
    }

    @Override
    protected boolean supportsMapParameters() {
        return false;
    }
}
