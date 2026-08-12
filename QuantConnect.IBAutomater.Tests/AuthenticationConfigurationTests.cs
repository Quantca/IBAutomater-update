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

using System;
using System.Collections.Generic;
using System.Reflection;
using NUnit.Framework;

namespace QuantConnect.IBAutomater.Tests
{
    [TestFixture]
    public class AuthenticationConfigurationTests
    {
        [Test]
        public void PreservesPublicAuthenticationEnumValues()
        {
            Assert.Multiple(() =>
            {
                Assert.That((int)TwoFactorAuthenticationMethod.IbKey, Is.EqualTo(0));
                Assert.That((int)TwoFactorAuthenticationMethod.MobileAuthenticator, Is.EqualTo(1));
                Assert.That((int)ErrorCode.MobileAuthenticatorAuthenticationFailed, Is.EqualTo(17));
            });
        }

        [Test]
        public void DescribesAllMobileAuthenticatorFailureStages()
        {
            var result = new StartResult(ErrorCode.MobileAuthenticatorAuthenticationFailed);

            Assert.Multiple(() =>
            {
                Assert.That(result.ErrorMessage, Does.Contain("configuration, automation, or authentication"));
                Assert.That(result.ErrorMessage, Does.Not.Contain("clock"));
            });
        }

        [TestCase(null)]
        [TestCase("")]
        [TestCase("Error: unrelated failure")]
        public void DoesNotParseUnrelatedMobileAuthenticatorOutput(string text)
        {
            Assert.That(
                IBAutomater.TryParseMobileAuthenticatorAuthenticationFailure(text, out _),
                Is.False);
        }

        [Test]
        public void ParsesCaseInsensitiveMobileAuthenticatorFailureReason()
        {
            var parsed = IBAutomater.TryParseMobileAuthenticatorAuthenticationFailure(
                "error: mobile authenticator authentication FAILED:   Too many failed login attempts.  ",
                out var reason);

            Assert.Multiple(() =>
            {
                Assert.That(parsed, Is.True);
                Assert.That(reason, Is.EqualTo("Too many failed login attempts."));
            });
        }

        [Test]
        public void PreservesAndRedactsMobileAuthenticatorFailureReason()
        {
            const string secret = "JBSWY3DPEHPK3PXP";
            var automater = CreateAutomater(TwoFactorAuthenticationMethod.MobileAuthenticator, secret);
            var output = new List<string>();
            automater.OutputDataReceived += (_, eventArgs) => output.Add(eventArgs.Data);

            var outputHandler = typeof(IBAutomater).GetMethod(
                "OnProcessOutputDataReceived",
                BindingFlags.Instance | BindingFlags.NonPublic);
            Assert.That(outputHandler, Is.Not.Null);
            outputHandler.Invoke(automater, new object[]
                {
                    $"Error: Mobile Authenticator authentication failed: Selector failed for {secret}."
                });

            var result = automater.GetLastStartResult();
            Assert.Multiple(() =>
            {
                Assert.That(result.ErrorCode, Is.EqualTo(ErrorCode.MobileAuthenticatorAuthenticationFailed));
                Assert.That(result.ErrorMessage, Does.Contain("Selector failed for ***."));
                Assert.That(result.ErrorMessage, Does.Not.Contain(secret));
                Assert.That(output, Has.Count.EqualTo(1));
                Assert.That(output[0], Does.Contain("Selector failed for ***."));
                Assert.That(output, Has.All.Matches<string>(message => !message.Contains(secret)));
            });
        }

        [Test]
        public void RejectsUnknownAuthenticationMethod()
        {
            Assert.That(
                () => CreateAutomater((TwoFactorAuthenticationMethod)2, null),
                Throws.ArgumentException.With.Property("ParamName").EqualTo("twoFactorAuthenticationMethod"));
        }

        [TestCase(null)]
        [TestCase("")]
        [TestCase("   ")]
        public void RequiresSetupKeyForMobileAuthenticator(string secret)
        {
            Assert.That(
                () => CreateAutomater(TwoFactorAuthenticationMethod.MobileAuthenticator, secret),
                Throws.ArgumentException.With.Property("ParamName").EqualTo("mobileAuthenticatorSecret"));
        }

        [Test]
        public void RejectsSetupKeyForIbKey()
        {
            Assert.That(
                () => CreateAutomater(TwoFactorAuthenticationMethod.IbKey, "JBSWY3DPEHPK3PXP"),
                Throws.ArgumentException.With.Property("ParamName").EqualTo("mobileAuthenticatorSecret"));
        }

        [TestCase("key\rvalue")]
        [TestCase("key\nvalue")]
        [TestCase("key\0value")]
        public void RejectsControlCharactersInSetupKey(string secret)
        {
            Assert.That(
                () => CreateAutomater(TwoFactorAuthenticationMethod.MobileAuthenticator, secret),
                Throws.ArgumentException.With.Property("ParamName").EqualTo("mobileAuthenticatorSecret"));
        }

        private static IBAutomater CreateAutomater(
            TwoFactorAuthenticationMethod twoFactorAuthenticationMethod,
            string mobileAuthenticatorSecret)
        {
            return new IBAutomater(
                string.Empty,
                string.Empty,
                string.Empty,
                string.Empty,
                "paper",
                4002,
                false,
                false,
                twoFactorAuthenticationMethod,
                mobileAuthenticatorSecret);
        }
    }
}
