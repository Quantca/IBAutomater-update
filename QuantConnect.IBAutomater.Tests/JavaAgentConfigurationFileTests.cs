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
using System.IO;
using System.Linq;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Text;
using NUnit.Framework;

namespace QuantConnect.IBAutomater.Tests
{
    [TestFixture]
    public class JavaAgentConfigurationFileTests
    {
        private string _temporaryDirectory;

        [SetUp]
        public void SetUp()
        {
            _temporaryDirectory = Path.Combine(
                Path.GetTempPath(),
                $"IBAutomater-{Guid.NewGuid():N}");
            Directory.CreateDirectory(_temporaryDirectory);
        }

        [TearDown]
        public void TearDown()
        {
            Directory.Delete(_temporaryDirectory, true);
        }

        [Test]
        public void WritesExactUtf8PayloadToOwnerOnlyFile()
        {
            RequireSupportedPlatform();
            var configurationDirectory = Path.Combine(_temporaryDirectory, "configuration files ü");
            Directory.CreateDirectory(configurationDirectory);
            var fileName = Path.Combine(configurationDirectory, "IBAutomater.json");
            const string configuration = "user\npássword\npaper\n4002\nFalse\nFalse\nTrue";

            IBAutomater.WriteJavaAgentConfigurationFile(fileName, configuration);

            Assert.That(File.ReadAllBytes(fileName), Is.EqualTo(new UTF8Encoding(false).GetBytes(configuration)));
            AssertOwnerOnlyPermissions(fileName);
            Assert.That(File.ResolveLinkTarget(fileName, false), Is.Null);
            AssertNoTemporaryHandoffFiles(fileName);
        }

        [Test]
        public void AppliesOwnerOnlyModeBeforeWritingAndRemovesOwnFileAfterWriteFailure()
        {
            RequireSupportedPlatform();
            var fileName = Path.Combine(_temporaryDirectory, "IBAutomater.json");
            var observedOwnerOnlyPermissions = false;

            Assert.Throws<InvalidOperationException>(() =>
                IBAutomater.WriteJavaAgentConfigurationFile(fileName, stream =>
                {
                    AssertOwnerOnlyPermissions(fileName);
                    Assert.That(new FileInfo(fileName).Length, Is.Zero);
                    observedOwnerOnlyPermissions = true;
                    stream.WriteByte(1);
                    throw new InvalidOperationException("Injected write failure");
                }));

            Assert.That(observedOwnerOnlyPermissions, Is.True);
            Assert.That(File.Exists(fileName), Is.False);
            AssertNoTemporaryHandoffFiles(fileName);
        }

        [Test]
        public void ReplacesPreexistingFileWithOwnerOnlyPayload()
        {
            RequireSupportedPlatform();
            var fileName = Path.Combine(_temporaryDirectory, "IBAutomater.json");
            File.WriteAllText(fileName, "stale handoff");

            IBAutomater.WriteJavaAgentConfigurationFile(fileName, "credentials");

            Assert.That(File.ReadAllText(fileName), Is.EqualTo("credentials"));
            AssertOwnerOnlyPermissions(fileName);
            Assert.That(File.ResolveLinkTarget(fileName, false), Is.Null);
            AssertNoTemporaryHandoffFiles(fileName);
        }

        [Test]
        public void ReplacesPreexistingSymbolicLinkWithoutModifyingItsTarget()
        {
            RequireUnix();
            var target = Path.Combine(_temporaryDirectory, "target");
            var fileName = Path.Combine(_temporaryDirectory, "IBAutomater.json");
            const string sentinel = "not-owned";
            File.WriteAllText(target, sentinel);
            File.CreateSymbolicLink(fileName, target);

            IBAutomater.WriteJavaAgentConfigurationFile(fileName, "credentials");

            Assert.That(File.ReadAllText(target), Is.EqualTo(sentinel));
            Assert.That(File.ReadAllText(fileName), Is.EqualTo("credentials"));
            Assert.That(File.ResolveLinkTarget(fileName, false), Is.Null);
            AssertOwnerOnlyPermissions(fileName);
            AssertNoTemporaryHandoffFiles(fileName);
        }

        [Test]
        public void ReplacesDanglingSymbolicLinkWithRegularOwnerOnlyFile()
        {
            RequireUnix();
            var missingTarget = Path.Combine(_temporaryDirectory, "missing-target");
            var fileName = Path.Combine(_temporaryDirectory, "IBAutomater.json");
            File.CreateSymbolicLink(fileName, missingTarget);

            IBAutomater.WriteJavaAgentConfigurationFile(fileName, "credentials");

            Assert.That(File.Exists(missingTarget), Is.False);
            Assert.That(File.ReadAllText(fileName), Is.EqualTo("credentials"));
            Assert.That(File.ResolveLinkTarget(fileName, false), Is.Null);
            AssertOwnerOnlyPermissions(fileName);
            AssertNoTemporaryHandoffFiles(fileName);
        }

        [Test]
        public void DoesNotRecursivelyRemoveDirectoryAtHandoffPath()
        {
            RequireSupportedPlatform();
            var fileName = Path.Combine(_temporaryDirectory, "IBAutomater.json");
            Directory.CreateDirectory(fileName);
            var sentinel = Path.Combine(fileName, "sentinel");
            File.WriteAllText(sentinel, "not-owned");

            Assert.Catch(() => IBAutomater.WriteJavaAgentConfigurationFile(fileName, "credentials"));

            Assert.That(Directory.Exists(fileName), Is.True);
            Assert.That(File.ReadAllText(sentinel), Is.EqualTo("not-owned"));
            AssertNoTemporaryHandoffFiles(fileName);
        }

        private static void AssertOwnerOnlyPermissions(string fileName)
        {
            if (!OperatingSystem.IsWindows())
            {
                Assert.That(
                    File.GetUnixFileMode(fileName),
                    Is.EqualTo(UnixFileMode.UserRead | UnixFileMode.UserWrite));
                return;
            }

            if (OperatingSystem.IsWindows())
            {
                using var windowsIdentity = WindowsIdentity.GetCurrent();
                var currentUser = windowsIdentity.User;
                var security = new FileInfo(fileName).GetAccessControl();
                var rules = security.GetAccessRules(true, false, typeof(SecurityIdentifier))
                    .Cast<FileSystemAccessRule>()
                    .ToList();
                Assert.That(security.AreAccessRulesProtected, Is.True);
                Assert.That(security.GetOwner(typeof(SecurityIdentifier)), Is.EqualTo(currentUser));
                Assert.That(rules, Has.Count.EqualTo(1));
                Assert.That(rules[0].IdentityReference, Is.EqualTo(currentUser));
                Assert.That(rules[0].AccessControlType, Is.EqualTo(AccessControlType.Allow));
                Assert.That(rules[0].FileSystemRights, Is.EqualTo(FileSystemRights.FullControl));
            }
        }

        private static void AssertNoTemporaryHandoffFiles(string fileName)
        {
            Assert.That(
                Directory.GetFiles(Path.GetDirectoryName(fileName))
                    .Where(path => Path.GetFileName(path).StartsWith(
                        $"{Path.GetFileName(fileName)}.", StringComparison.Ordinal)),
                Is.Empty);
        }

        private static void RequireSupportedPlatform()
        {
            if (!OperatingSystem.IsWindows()
                && Environment.OSVersion.Platform != PlatformID.Unix
                && Environment.OSVersion.Platform != PlatformID.MacOSX)
            {
                Assert.Ignore("Secure settings-file behavior requires Windows or a POSIX platform.");
            }
        }

        private static void RequireUnix()
        {
            if (OperatingSystem.IsWindows())
            {
                Assert.Ignore("Symbolic-link behavior is exercised on POSIX platforms.");
            }
        }
    }
}
