package dev.langchain4j.model.openaiofficial;

import com.azure.identity.AuthenticationUtil;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.openai.credential.BearerTokenCredential;
import com.openai.credential.Credential;

class AzureInternalOpenAiOfficialHelper {

    static Credential getAzureCredential() {
        return BearerTokenCredential.create(AuthenticationUtil.getBearerTokenSupplier(
                new DefaultAzureCredentialBuilder().build(), "https://cognitiveservices.azure.com/.default"));
    }
}
