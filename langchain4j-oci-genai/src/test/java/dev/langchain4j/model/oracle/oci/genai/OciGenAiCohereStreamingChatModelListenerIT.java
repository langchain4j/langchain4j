package dev.langchain4j.model.oracle.oci.genai;

import static dev.langchain4j.model.oracle.oci.genai.TestEnvProps.NON_EMPTY;
import static dev.langchain4j.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COHERE_CHAT_MODEL_NAME;
import static dev.langchain4j.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COHERE_CHAT_MODEL_NAME_PROPERTY;
import static dev.langchain4j.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COMPARTMENT_ID;
import static dev.langchain4j.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COMPARTMENT_ID_PROPERTY;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

@EnabledIfEnvironmentVariables({
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_COMPARTMENT_ID_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_COHERE_CHAT_MODEL_NAME_PROPERTY, matches = NON_EMPTY)
})
public class OciGenAiCohereStreamingChatModelListenerIT extends AbstractStreamingChatModelListenerIT {

    static final AuthenticationDetailsProvider authProvider = TestEnvProps.createAuthProvider();
    static final List<AutoCloseable> CHAT_MODELS = new ArrayList<>();

    @AfterAll
    static void afterAll() throws Exception {
        for (AutoCloseable closeable : CHAT_MODELS) {
            closeable.close();
        }
    }

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener) {
        var model = OciGenAiCohereStreamingChatModel.builder()
                .chatModelId(OCI_GENAI_COHERE_CHAT_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .maxTokens(600)
                .temperature(0.7)
                .listeners(List.of(listener))
                .topP(1.0)
                .build();

        CHAT_MODELS.add(model);

        return model;
    }

    @Override
    protected StreamingChatModel createFailingModel(ChatModelListener listener) {
        var model = OciGenAiCohereStreamingChatModel.builder()
                .chatModelId("failing")
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .listeners(List.of(listener))
                .authProvider(authProvider)
                .build();

        CHAT_MODELS.add(model);

        return model;
    }

    @Override
    protected String modelName() {
        return OCI_GENAI_COHERE_CHAT_MODEL_NAME;
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return com.oracle.bmc.model.BmcException.class;
    }

    @Override
    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected boolean assertTokenUsage() {
        return false;
    }

    @Override
    protected Integer maxTokens() {
        return 600;
    }
}
