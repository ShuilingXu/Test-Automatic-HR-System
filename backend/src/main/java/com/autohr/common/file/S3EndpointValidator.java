package com.autohr.common.file;

import java.net.InetAddress;
import java.net.URI;

public final class S3EndpointValidator {

    private S3EndpointValidator() {
    }

    public static boolean isAllowed(String value, boolean allowHttp, boolean allowPrivateAddresses) {
        URI endpoint;
        try {
            endpoint = URI.create(value == null ? "" : value.trim());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        String scheme = endpoint.getScheme();
        if (endpoint.getHost() == null
                || !("https".equalsIgnoreCase(scheme) || (allowHttp && "http".equalsIgnoreCase(scheme)))
                || endpoint.getRawUserInfo() != null
                || endpoint.getRawFragment() != null) {
            return false;
        }
        if (allowPrivateAddresses) {
            return true;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(endpoint.getHost())) {
                if (isPrivateOrLocal(address)) {
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean isPrivateOrLocal(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first == 100 && second >= 64 && second <= 127;
        }
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }
}
