// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package dev.langchain4j.store.embedding.vespa;

import dev.langchain4j.internal.Utils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * This Workaround is needed because of <a href="https://github.com/vespa-engine/vespa/issues/28026">this request</a>.
 * It will be redundant as soon as vespa-client is implemented. This class is copied from <code>vespa-feed-client</code>.
 * BouncyCastle integration for creating a {@link SSLContext} instance from PEM encoded material
 */
class VespaClient {

    static final BouncyCastleProvider bcProvider = new BouncyCastleProvider();

    public static VespaApi createInstance(
            String baseUrl, Path certificate, Path privateKey, boolean logRequests, boolean logResponses) {
        try {
            OkHttpClient.Builder builder = new OkHttpClient.Builder().addInterceptor(chain -> {
                // trick to format the query URL exactly how Vespa expects it (search/?query),
                // see https://docs.vespa.ai/en/reference/query-language-reference.html
                Request request = chain.request();
                if (request.url().url().getPath().startsWith("/search/")) {
                    HttpUrl url = request.url()
                            .newBuilder()
                            .removePathSegment(1)
                            .addPathSegment("")
                            .encodedQuery(request.url().encodedPathSegments().get(1))
                            .build();
                    request = request.newBuilder().url(url).build();
                }
                return chain.proceed(request);
            });

            addSsl(certificate, privateKey, builder);

            if (logRequests) {
                builder.addInterceptor(new VespaRequestLoggingInterceptor());
            }
            if (logResponses) {
                builder.addInterceptor(new VespaResponseLoggingInterceptor());
            }

            OkHttpClient client = builder.build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(Utils.ensureTrailingForwardSlash(baseUrl))
                    .client(client)
                    .addConverterFactory(JacksonConverterFactory.create())
                    .build();

            return retrofit.create(VespaApi.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void addSsl(Path certificate, Path privateKey, OkHttpClient.Builder builder)
            throws IOException, GeneralSecurityException {
        if (certificate != null && privateKey != null) {
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(null);
            keystore.setKeyEntry("cert", privateKey(privateKey), new char[0], certificates(certificate));
            // Protocol version must be equal to TlsContext.SSL_CONTEXT_VERSION or higher
            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(createKeyManagers(keystore), null, /*Default secure random algorithm*/ null);

            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keystore);

            builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager)
                    trustManagerFactory.getTrustManagers()[0]);
        }
    }

    private static KeyManager[] createKeyManagers(KeyStore keystore) throws GeneralSecurityException {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keystore, new char[0]);
        return kmf.getKeyManagers();
    }

    private static Certificate[] certificates(Path file) throws IOException, GeneralSecurityException {
        try (PEMParser parser = new PEMParser(Files.newBufferedReader(file))) {
            List<X509Certificate> result = new ArrayList<>();
            Object pemObject;
            while ((pemObject = parser.readObject()) != null) {
                result.add(toX509Certificate(pemObject));
            }
            if (result.isEmpty()) throw new IOException("File contains no PEM encoded certificates: " + file);
            return result.toArray(new Certificate[0]);
        }
    }

    private static PrivateKey privateKey(Path file) throws IOException, GeneralSecurityException {
        try (PEMParser parser = new PEMParser(Files.newBufferedReader(file))) {
            Object pemObject;
            while ((pemObject = parser.readObject()) != null) {
                if (pemObject instanceof PrivateKeyInfo) {
                    PrivateKeyInfo keyInfo = (PrivateKeyInfo) pemObject;
                    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyInfo.getEncoded());
                    return createKeyFactory(keyInfo).generatePrivate(keySpec);
                } else if (pemObject instanceof PEMKeyPair) {
                    PEMKeyPair pemKeypair = (PEMKeyPair) pemObject;
                    PrivateKeyInfo keyInfo = pemKeypair.getPrivateKeyInfo();
                    return createKeyFactory(keyInfo).generatePrivate(new PKCS8EncodedKeySpec(keyInfo.getEncoded()));
                }
            }
            throw new IOException("Could not find private key in PEM file");
        }
    }

    private static X509Certificate toX509Certificate(Object pemObject) throws IOException, GeneralSecurityException {
        if (pemObject instanceof X509Certificate) return (X509Certificate) pemObject;
        if (pemObject instanceof X509CertificateHolder) {
            return new JcaX509CertificateConverter().setProvider(bcProvider).getCertificate((X509CertificateHolder)
                    pemObject);
        }
        throw new IOException("Invalid type of PEM object: " + pemObject);
    }

    private static KeyFactory createKeyFactory(PrivateKeyInfo info) throws IOException, GeneralSecurityException {
        ASN1ObjectIdentifier algorithm = info.getPrivateKeyAlgorithm().getAlgorithm();
        if (X9ObjectIdentifiers.id_ecPublicKey.equals(algorithm)) {
            return KeyFactory.getInstance("EC", bcProvider);
        } else if (PKCSObjectIdentifiers.rsaEncryption.equals(algorithm)) {
            return KeyFactory.getInstance("RSA", bcProvider);
        } else {
            throw new IOException("Unknown key algorithm: " + algorithm);
        }
    }
}
