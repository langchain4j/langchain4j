package dev.langchain4j.data.document.loader;

import static dev.langchain4j.internal.Utils.areNotNullOrBlank;

/**
 * Represents an AWS credentials object, including access key ID, secret access key, and optional session token.
 */
public class AwsCredentials {

    private final String accessKeyId;
    private final String secretAccessKey;
    private String sessionToken;

    public AwsCredentials(String accessKeyId, String secretAccessKey, String sessionToken) {
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.sessionToken = sessionToken;
    }

    public AwsCredentials(String accessKeyId, String secretAccessKey) {
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
    }


    public String getAccessKeyId() {
        return accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public boolean hasAccessKeyIdAndSecretKey() {
        return areNotNullOrBlank(accessKeyId, secretAccessKey);
    }

    public boolean hasAllCredentials() {
        return areNotNullOrBlank(accessKeyId, secretAccessKey, sessionToken);
    }
}
