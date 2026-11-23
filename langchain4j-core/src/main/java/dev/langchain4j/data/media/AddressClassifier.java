package dev.langchain4j.data.media;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether an IP address is a public unicast address. Everything else (loopback, private, link-local,
 * cloud metadata, multicast, reserved, documentation, ...) is non-public. IPv4 addresses embedded in IPv6 addresses
 * are checked as IPv4 addresses.
 */
final class AddressClassifier {

    private static final List<Cidr> NON_PUBLIC_IPV4 = List.of(
            Cidr.parse("0.0.0.0/8"),
            Cidr.parse("10.0.0.0/8"),
            Cidr.parse("100.64.0.0/10"),
            Cidr.parse("127.0.0.0/8"),
            Cidr.parse("169.254.0.0/16"),
            Cidr.parse("172.16.0.0/12"),
            Cidr.parse("192.0.0.0/24"),
            Cidr.parse("192.0.2.0/24"),
            Cidr.parse("192.88.99.0/24"),
            Cidr.parse("192.168.0.0/16"),
            Cidr.parse("198.18.0.0/15"),
            Cidr.parse("198.51.100.0/24"),
            Cidr.parse("203.0.113.0/24"),
            Cidr.parse("224.0.0.0/4"),
            Cidr.parse("240.0.0.0/4"),
            Cidr.parse("168.63.129.16/32"));

    private static final List<Cidr> NON_PUBLIC_IPV6 = List.of(
            Cidr.parse("::/128"),
            Cidr.parse("::1/128"),
            Cidr.parse("fe80::/10"),
            Cidr.parse("fec0::/10"),
            Cidr.parse("fc00::/7"),
            Cidr.parse("ff00::/8"),
            Cidr.parse("100::/64"),
            Cidr.parse("2001:2::/48"),
            Cidr.parse("2001:db8::/32"),
            Cidr.parse("64:ff9b:1::/48"));

    private static final Cidr IPV4_MAPPED = Cidr.parse("::ffff:0:0/96");
    private static final Cidr IPV4_COMPATIBLE = Cidr.parse("::/96");
    private static final Cidr NAT64 = Cidr.parse("64:ff9b::/96");
    private static final Cidr SIX_TO_FOUR = Cidr.parse("2002::/16");
    private static final Cidr TEREDO = Cidr.parse("2001::/32");

    private AddressClassifier() {}

    static boolean isPublic(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4 ? isPublicIpv4(bytes) : isPublicIpv6(bytes);
    }

    private static boolean isPublicIpv4(byte[] address) {
        return NON_PUBLIC_IPV4.stream().noneMatch(cidr -> cidr.contains(address));
    }

    private static boolean isPublicIpv6(byte[] address) {
        if (NON_PUBLIC_IPV6.stream().anyMatch(cidr -> cidr.contains(address))) {
            return false;
        }
        byte[] embedded = embeddedIpv4(address);
        return embedded == null || isPublicIpv4(embedded);
    }

    private static byte @Nullable [] embeddedIpv4(byte[] address) {
        if (IPV4_MAPPED.contains(address) || IPV4_COMPATIBLE.contains(address) || NAT64.contains(address)) {
            return Arrays.copyOfRange(address, 12, 16);
        }
        if (SIX_TO_FOUR.contains(address)) {
            return Arrays.copyOfRange(address, 2, 6);
        }
        if (TEREDO.contains(address)) {
            byte[] client = Arrays.copyOfRange(address, 12, 16);
            for (int i = 0; i < client.length; i++) {
                client[i] = (byte) ~client[i];
            }
            return client;
        }
        return null;
    }

    private static final class Cidr {

        private final byte[] prefix;
        private final int bits;

        private Cidr(byte[] prefix, int bits) {
            this.prefix = prefix;
            this.bits = bits;
        }

        static Cidr parse(String cidr) {
            int slash = cidr.indexOf('/');
            String literal = cidr.substring(0, slash);
            byte[] bytes = literalToBytes(literal);
            if (literal.contains(":") && bytes.length == 4) {
                // the JDK turns IPv4-mapped IPv6 literals into IPv4 addresses
                byte[] mapped = new byte[16];
                mapped[10] = (byte) 0xff;
                mapped[11] = (byte) 0xff;
                System.arraycopy(bytes, 0, mapped, 12, 4);
                bytes = mapped;
            }
            return new Cidr(bytes, Integer.parseInt(cidr.substring(slash + 1)));
        }

        private static byte[] literalToBytes(String literal) {
            try {
                return InetAddress.getByName(literal).getAddress(); // an IP literal, so there is no name lookup
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException(literal, e);
            }
        }

        boolean contains(byte[] address) {
            if (address.length != prefix.length) {
                return false;
            }
            int fullBytes = bits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != prefix[i]) {
                    return false;
                }
            }
            int remainingBits = bits % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xff << (8 - remainingBits)) & 0xff;
            return (address[fullBytes] & mask) == (prefix[fullBytes] & mask);
        }
    }
}
