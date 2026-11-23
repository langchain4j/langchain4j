package dev.langchain4j.data.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AddressClassifierTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "0.0.0.0",
                "0.1.2.3",
                "10.0.0.1",
                "10.255.255.255",
                "100.64.0.1",
                "100.100.100.200",
                "100.127.255.255",
                "127.0.0.1",
                "127.255.255.254",
                "169.254.169.254",
                "169.254.170.2",
                "172.16.0.1",
                "172.31.255.255",
                "192.0.0.8",
                "192.0.2.1",
                "192.88.99.1",
                "192.168.1.1",
                "198.18.0.1",
                "198.19.255.255",
                "198.51.100.7",
                "203.0.113.9",
                "224.0.0.1",
                "239.255.255.250",
                "240.0.0.1",
                "255.255.255.255",
                "168.63.129.16",
                "::",
                "::1",
                "fe80::1",
                "febf::1",
                "fec0::1",
                "fc00::1",
                "fd00:ec2::254",
                "ff02::1",
                "100::1",
                "2001:2::1",
                "2001:db8::1",
                "64:ff9b:1::1",
                // IPv4 addresses embedded in IPv6 addresses
                "::ffff:127.0.0.1",
                "::ffff:169.254.169.254",
                "::127.0.0.1",
                "::10.0.0.1",
                "64:ff9b::7f00:1",
                "64:ff9b::a9fe:a9fe",
                "2002:7f00:1::1",
                "2002:a9fe:a9fe::",
                "2001:0:4136:e378:8000:63bf:80ff:fffe"
            })
    void should_classify_as_non_public(String literal) throws Exception {
        assertThat(AddressClassifier.isPublic(InetAddress.getByName(literal)))
                .as(literal)
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "1.1.1.1",
                "8.8.8.8",
                "93.184.215.14",
                "100.63.255.255",
                "100.128.0.0",
                "126.255.255.255",
                "128.0.0.1",
                "169.253.255.255",
                "169.255.0.0",
                "172.15.255.255",
                "172.32.0.0",
                "192.0.1.1",
                "192.167.255.255",
                "192.169.0.0",
                "198.17.255.255",
                "198.20.0.0",
                "223.255.255.255",
                "168.63.129.17",
                "2606:4700:10::6814:179a",
                "2a00:1450:4001:82a::200e",
                "64:ff9b::808:808",
                "2002:808:808::1",
                "2001:0:4136:e378:8000:63bf:f7f7:f7f7",
                "::ffff:8.8.8.8"
            })
    void should_classify_as_public(String literal) throws Exception {
        assertThat(AddressClassifier.isPublic(InetAddress.getByName(literal)))
                .as(literal)
                .isTrue();
    }
}
