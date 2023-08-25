// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package dev.langchain4j.store.embedding.vespa;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;

import javax.net.ssl.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

/**
 * This Workaround is needed because of <a href="https://github.com/vespa-engine/vespa/issues/28026">this request</a>.
 * It will be redundant as soon as vespa-client is implemented. This class is copied from <code>vespa-feed-client</code>.
 * BouncyCastle integration for creating a {@link SSLContext} instance from PEM encoded material
 *
 * @author bjorncs
 */
class VespaSslContextBuilder {

    static final BouncyCastleProvider bcProvider = new BouncyCastleProvider();

    private Path certificateFile;
    private Path privateKeyFile;
    private Path caCertificatesFile;
    private Collection<X509Certificate> certificate;
    private PrivateKey privateKey;
    private Collection<X509Certificate> caCertificates;

    VespaSslContextBuilder withCertificateAndKey(Path certificate, Path privateKey) {
        this.certificateFile = certificate;
        this.privateKeyFile = privateKey;
        return this;
    }

    VespaSslContextBuilder withCertificateAndKey(Collection<X509Certificate> certificate, PrivateKey privateKey) {
        this.certificate = certificate;
        this.privateKey = privateKey;
        return this;
    }

    VespaSslContextBuilder withCaCertificates(Path caCertificates) {
        this.caCertificatesFile = caCertificates;
        return this;
    }

    VespaSslContextBuilder withCaCertificates(Collection<X509Certificate> caCertificates) {
        this.caCertificates = caCertificates;
        return this;
    }

    SSLContext build() throws IOException {
        try {
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(null);
            if (hasCertificateFile()) {
                keystore.setKeyEntry("cert", privateKey(privateKeyFile), new char[0], certificates(certificateFile));
            } else if (hasCertificateInstance()) {
                keystore.setKeyEntry("cert", privateKey, new char[0], certificate.toArray(new Certificate[0]));
            }
            if (hasCaCertificateFile()) {
                addCaCertificates(keystore, Arrays.asList(certificates(caCertificatesFile)));
            } else if (hasCaCertificateInstance()) {
                addCaCertificates(keystore, caCertificates);
            }
            // Protocol version must be equal to TlsContext.SSL_CONTEXT_VERSION or higher
            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(
                    createKeyManagers(keystore).orElse(null),
                    createTrustManagers(keystore).orElse(null),
                    /*Default secure random algorithm*/null);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private boolean hasCertificateFile() { return certificateFile != null && privateKeyFile != null; }
    private boolean hasCertificateInstance() { return certificate != null && privateKey != null; }
    private boolean hasCaCertificateFile() { return caCertificatesFile != null; }
    private boolean hasCaCertificateInstance() { return caCertificates != null; }

    private Optional<KeyManager[]> createKeyManagers(KeyStore keystore) throws GeneralSecurityException {
        if (!hasCertificateInstance() && !hasCertificateFile()) return Optional.empty();
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keystore, new char[0]);
        return Optional.of(kmf.getKeyManagers());
    }

    private Optional<TrustManager[]> createTrustManagers(KeyStore keystore) throws GeneralSecurityException {
        if (!hasCaCertificateInstance() && !hasCaCertificateFile()) return Optional.empty();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keystore);
        return Optional.of(tmf.getTrustManagers());
    }

    private static void addCaCertificates(KeyStore keystore, Collection<? extends Certificate> certificates) throws KeyStoreException {
        int i = 0;
        for (Certificate cert : certificates) {
            keystore.setCertificateEntry("ca-cert-" + ++i, cert);
        }
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
            return new JcaX509CertificateConverter()
                    .setProvider(bcProvider)
                    .getCertificate((X509CertificateHolder) pemObject);
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
