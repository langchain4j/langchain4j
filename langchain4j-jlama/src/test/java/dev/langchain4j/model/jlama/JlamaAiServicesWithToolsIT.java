package dev.langchain4j.model.jlama;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;
import org.junit.jupiter.api.BeforeAll;

import java.io.File;
import java.util.Collections;
import java.util.List;

class JlamaAiServicesWithToolsIT extends AiServicesWithNewToolsIT {

    static File tmpDir;

    @BeforeAll
    static void setup() {
        tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "jlama_tests");
        tmpDir.mkdirs();
    }

    @Override
    protected List<ChatLanguageModel> models() {
        return Collections.singletonList(
                JlamaChatModel.builder()
                        .modelName("tjake/Meta-Llama-3.1-8B-Instruct-Jlama-Q4")
                        .modelCachePath(tmpDir.toPath())
                        .temperature(0.0f)
                        .maxTokens(1024)
                        .build()
        );
    }
}
