package dev.langchain4j.data.document.loader.amazon.s3;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Represents an AWS credentials object, including access key ID, secret access key, and optional session token.
 */
public class AwsCredentials {

    private final String accessKeyId;
    private final String secretAccessKey;
    private final String sessionToken;

    public AwsCredentials(String accessKeyId, String secretAccessKey) {
        this(accessKeyId, secretAccessKey, null);
    }

    public AwsCredentials(String accessKeyId, String secretAccessKey, String sessionToken) {
        this.accessKeyId = ensureNotBlank(accessKeyId, "accessKeyId");
        this.secretAccessKey = ensureNotBlank(secretAccessKey, "secretAccessKey");
        this.sessionToken = sessionToken;
    }

    public AwsCredentialsProvider toCredentialsProvider() {
        return StaticCredentialsProvider.create(toCredentials());
    }

    private software.amazon.awssdk.auth.credentials.AwsCredentials toCredentials() {
        if (sessionToken != null) {
            return AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
        }
        return AwsBasicCredentials.create(accessKeyId, secretAccessKey);
    }
}
