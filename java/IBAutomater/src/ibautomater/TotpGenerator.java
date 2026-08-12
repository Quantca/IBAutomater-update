/*
 * QUANTCONNECT.COM - Democratizing Finance, Empowering Individuals.
 * IBAutomater v1.0. Copyright 2019 QuantConnect Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package ibautomater;

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Generates the RFC 6238 profile used by Gateway's "Mobile Authenticator app" option.
 */
final class TotpGenerator {
    static final int PERIOD_SECONDS = 30;
    static final int SAFE_BOUNDARY_MILLISECONDS = 3000;

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String ALGORITHM = "HmacSHA1";
    private static final int MODULUS = 1000000;

    private final byte[] key;

    TotpGenerator(String base32Secret) {
        this(decodeBase32(base32Secret));
    }

    TotpGenerator(byte[] key) {
        if (key == null || key.length == 0) {
            throw invalidSecret();
        }
        this.key = Arrays.copyOf(key, key.length);
    }

    String generate(long epochSeconds) {
        if (epochSeconds < 0) {
            throw new IllegalArgumentException("TOTP time must not precede the Unix epoch");
        }

        long counter = epochSeconds / PERIOD_SECONDS;
        byte[] counterBytes = new byte[8];
        for (int index = counterBytes.length - 1; index >= 0; index--) {
            counterBytes[index] = (byte)counter;
            counter >>>= 8;
        }

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(this.key, ALGORITHM));
            byte[] digest = mac.doFinal(counterBytes);
            int offset = digest[digest.length - 1] & 0x0f;
            int binary = ((digest[offset] & 0x7f) << 24)
                | ((digest[offset + 1] & 0xff) << 16)
                | ((digest[offset + 2] & 0xff) << 8)
                | (digest[offset + 3] & 0xff);
            int code = binary % MODULUS;
            return String.format(java.util.Locale.ROOT, "%06d", code);
        }
        catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP generation is unavailable", exception);
        }
    }

    String generateCurrent() {
        return generate(System.currentTimeMillis() / 1000L);
    }

    /**
     * Returns a one-shot delay until the current period is outside its guarded
     * boundaries. The safe interval includes the lower boundary and excludes
     * the upper boundary.
     */
    static int getBoundaryDelayMilliseconds(long epochMilliseconds) {
        long periodMilliseconds = PERIOD_SECONDS * 1000L;
        long elapsed = Math.floorMod(epochMilliseconds, periodMilliseconds);
        if (elapsed >= SAFE_BOUNDARY_MILLISECONDS
            && elapsed < periodMilliseconds - SAFE_BOUNDARY_MILLISECONDS) {
            return 0;
        }
        if (elapsed < SAFE_BOUNDARY_MILLISECONDS) {
            return (int)(SAFE_BOUNDARY_MILLISECONDS - elapsed);
        }
        return (int)(periodMilliseconds - elapsed + SAFE_BOUNDARY_MILLISECONDS);
    }

    static byte[] decodeBase32(String secret) {
        if (secret == null) {
            throw invalidSecret();
        }

        StringBuilder normalizedBuilder = new StringBuilder(secret.length());
        for (int index = 0; index < secret.length(); index++) {
            char character = secret.charAt(index);
            if (character == ' ' || character == '\t') {
                continue;
            }
            if (character == '\r' || character == '\n' || character == '\0') {
                throw invalidSecret();
            }
            // RFC 4648 Base32 is ASCII. Fold only ASCII lower-case letters so
            // Unicode characters that happen to uppercase to A-Z are rejected.
            if (character >= 'a' && character <= 'z') {
                character = (char)(character - 'a' + 'A');
            }
            normalizedBuilder.append(character);
        }

        String normalized = normalizedBuilder.toString();
        if (normalized.length() == 0) {
            throw invalidSecret();
        }

        int paddingIndex = normalized.indexOf('=');
        int significantLength = paddingIndex < 0 ? normalized.length() : paddingIndex;
        int paddingLength = normalized.length() - significantLength;
        for (int index = significantLength; index < normalized.length(); index++) {
            if (normalized.charAt(index) != '=') {
                throw invalidSecret();
            }
        }

        int remainder = significantLength % 8;
        int expectedPadding;
        switch (remainder) {
            case 0:
                expectedPadding = 0;
                break;
            case 2:
                expectedPadding = 6;
                break;
            case 4:
                expectedPadding = 4;
                break;
            case 5:
                expectedPadding = 3;
                break;
            case 7:
                expectedPadding = 1;
                break;
            default:
                throw invalidSecret();
        }

        if (paddingLength > 0
            && (normalized.length() % 8 != 0 || paddingLength != expectedPadding)) {
            throw invalidSecret();
        }

        ByteArrayOutputStream decoded = new ByteArrayOutputStream(significantLength * 5 / 8);
        int buffer = 0;
        int bufferedBits = 0;
        for (int index = 0; index < significantLength; index++) {
            int value = ALPHABET.indexOf(normalized.charAt(index));
            if (value < 0) {
                throw invalidSecret();
            }

            buffer = (buffer << 5) | value;
            bufferedBits += 5;
            if (bufferedBits >= 8) {
                bufferedBits -= 8;
                decoded.write((buffer >> bufferedBits) & 0xff);
                buffer &= bufferedBits == 0 ? 0 : (1 << bufferedBits) - 1;
            }
        }

        if (buffer != 0) {
            // Reject non-zero trailing bits rather than accepting multiple encodings
            // for the same key.
            throw invalidSecret();
        }

        byte[] result = decoded.toByteArray();
        if (result.length == 0) {
            throw invalidSecret();
        }
        return result;
    }

    private static IllegalArgumentException invalidSecret() {
        return new IllegalArgumentException("Invalid Mobile Authenticator setup secret");
    }
}
