package dev.langchain4j.model.openaiofficial.setup;

import com.azure.identity.AuthenticationUtil;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.openai.credential.BearerTokenCredential;
import com.openai.credential.Credential;

/**
 * Specific configuration for authenticating on Azure.
 *  * This is in a separate class to avoid needing the Azure SDK dependencies
 *  * when not using Azure as a platform.
 */
class AzureInternalOpenAiOfficialHelper {

    static Credential getAzureCredential() {
        return BearerTokenCredential.create(AuthenticationUtil.getBearerTokenSupplier(
                new DefaultAzureCredentialBuilder().build(), "https://cognitiveservices.azure.com/.default"));
    }
}
