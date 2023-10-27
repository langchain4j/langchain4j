package dev.langchain4j.data.document;

import static dev.langchain4j.internal.Utils.areNotNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Represents an AWS credentials object, including access key ID, secret access key, and optional session token.
 */
public class AwsCredentials {

    private final String accessKeyId;
    private final String secretAccessKey;
    private String sessionToken;

    public AwsCredentials(String accessKeyId, String secretAccessKey, String sessionToken) {
        this.accessKeyId = ensureNotBlank(accessKeyId, "accessKeyId");
        this.secretAccessKey = ensureNotBlank(secretAccessKey, "secretAccessKey");
        this.sessionToken = sessionToken;
    }

    public AwsCredentials(String accessKeyId, String secretAccessKey) {
        this(accessKeyId, secretAccessKey, null);
    }


    public String accessKeyId() {
        return accessKeyId;
    }

    public String secretAccessKey() {
        return secretAccessKey;
    }

    public String sessionToken() {
        return sessionToken;
    }

    public boolean hasAccessKeyIdAndSecretKey() {
        return areNotNullOrBlank(accessKeyId, secretAccessKey);
    }

    public boolean hasAllCredentials() {
        return areNotNullOrBlank(accessKeyId, secretAccessKey, sessionToken);
    }
}
