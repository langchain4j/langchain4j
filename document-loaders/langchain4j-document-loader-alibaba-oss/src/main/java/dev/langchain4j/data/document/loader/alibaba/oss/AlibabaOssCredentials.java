package dev.langchain4j.data.document.loader.alibaba.oss;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.aliyun.oss.common.auth.DefaultCredentialProvider;

public class AlibabaOssCredentials {

    private final String accessKeyId;
    private final String secretAccessKey;
    private final String securityToken;

    public AlibabaOssCredentials(final String accessKeyId, final String secretAccessKey, final String securityToken) {
        this.accessKeyId = ensureNotBlank(accessKeyId, "accessKeyId");
        this.secretAccessKey = ensureNotBlank(secretAccessKey, "secretAccessKey");
        this.securityToken = securityToken;
    }

    public DefaultCredentialProvider toCredentialsProvider() {
        if (securityToken == null) {
            return new DefaultCredentialProvider(accessKeyId, secretAccessKey);
        }
        return new DefaultCredentialProvider(accessKeyId, secretAccessKey, securityToken);
    }
}
