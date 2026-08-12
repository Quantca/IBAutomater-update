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

/**
 * The supported IB two-factor authentication methods.
 */
enum TwoFactorAuthenticationMethod {
    IB_KEY("ib-key"),
    MOBILE_AUTHENTICATOR("mobile-authenticator");

    private final String token;

    TwoFactorAuthenticationMethod(String token) {
        this.token = token;
    }

    /**
     * Parses a configuration token.
     *
     * @param value The token to parse
     * @return The corresponding authentication method
     */
    static TwoFactorAuthenticationMethod parse(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.length() == 0 || IB_KEY.token.equalsIgnoreCase(candidate)) {
            return IB_KEY;
        }
        if (MOBILE_AUTHENTICATOR.token.equalsIgnoreCase(candidate)) {
            return MOBILE_AUTHENTICATOR;
        }
        throw new IllegalArgumentException("Unsupported two-factor authentication method");
    }

    /**
     * Gets the canonical configuration token.
     *
     * @return The canonical configuration token
     */
    String getToken() {
        return this.token;
    }
}
