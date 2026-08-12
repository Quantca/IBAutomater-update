/*
 * QUANTCONNECT.COM - Democratizing Finance, Empowering Individuals.
 * IBAutomater v1.0. Copyright 2019 QuantConnect Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

namespace QuantConnect.IBAutomater
{
    /// <summary>
    /// The Interactive Brokers two-factor authentication method
    /// </summary>
    public enum TwoFactorAuthenticationMethod
    {
        /// <summary>
        /// IB Key seamless authentication through the IBKR mobile application
        /// </summary>
        IbKey = 0,

        /// <summary>
        /// A time-based one-time password generated from an authenticator setup key
        /// </summary>
        MobileAuthenticator = 1
    }
}
