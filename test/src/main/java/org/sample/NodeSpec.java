package org.sample;

public class NodeSpec {
    private static final long[] LOOKUP = {
        0x03FF600000000000L, // 0-9, '.', '-'
        0x07FFFFFE07FFFFFEL  // A-Z, a-z
    };

    public static boolean isPortValid(int port) {
        return (port & ~0xFFFF) == 0;
    }

    public static boolean isHostValid(String host) {
        int hostLength = host.length();
        if (((hostLength - 1) | (253 - hostLength)) < 0) return false;

        int result = 0;
        for (char hostPart: host.toCharArray()) {
            int isAscii = (127 - hostPart) >> 31;
            int bit = (int) ((LOOKUP[(hostPart >> 6) & 1] >>> (hostPart & 63)) & 1);

            result |= (bit ^ 1) | isAscii;
        }

        return result == 0;
    }
}
