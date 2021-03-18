package org.jenkinsci.deprecatedusage;

import org.apache.commons.codec.binary.Hex;

import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.security.MessageDigest;
import java.util.function.UnaryOperator;

@FunctionalInterface
public interface Checksum {

    void check(byte[] data, String url) throws DigestException;

    static Checksum fromDigest(String kind, byte[] expectedDigest, UnaryOperator<byte[]> digester) {
        return (data, url) -> {
            byte[] actualDigest = digester.apply(data);
            if (!MessageDigest.isEqual(expectedDigest, actualDigest)) {
                StringBuilder message = new StringBuilder(new StringBuilder().append("Wrong ").append(kind).append(" digest for ").append(url).append(" of length ").append(data.length).append(": expected ").append(Hex.encodeHexString(expectedDigest)).append(" but got ").append(Hex.encodeHexString(actualDigest)));
                if (data.length < 1000) {
                    message.append(" body: `").append(new String(data, StandardCharsets.ISO_8859_1)).append('`');
                }
                throw new DigestException(message.toString());
            }
        };
    }

}
