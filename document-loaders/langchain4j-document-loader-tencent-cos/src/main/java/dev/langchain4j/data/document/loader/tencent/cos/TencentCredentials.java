package dev.langchain4j.data.document.loader.tencent.cos;

import com.qcloud.cos.auth.*;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

public class TencentCredentials {

    private final String secretId;
    private final String secretKey;
    private final String sessionToken;

    public TencentCredentials(String secretId, String secretKey) {
        this(secretId, secretKey, null);
    }

    public TencentCredentials(String secretId, String secretKey, String sessionToken) {
        this.secretId = ensureNotBlank(secretId, "accessKeyId");
        this.secretKey = ensureNotBlank(secretKey, "secretAccessKey");
        this.sessionToken = sessionToken;
    }

    public COSCredentialsProvider toCredentialsProvider() {
        return new COSStaticCredentialsProvider(toCredentials());
    }

    public COSCredentials toCredentials() {
        if (sessionToken == null) {
            return new BasicCOSCredentials(secretId, secretKey);
        }

        return new BasicSessionCredentials(secretId, secretKey, sessionToken);
    }
}
