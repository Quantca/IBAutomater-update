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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * Dependency-free tests for settings parsing, authentication policy, and
 * Gateway configuration helpers. The Ant test target fails on the first error.
 */
public final class IBAutomaterSelfTest {
    private static int assertions;

    public static void main(String[] args) {
        TestTotpVectors();
        TestBase32Validation();
        TestBoundaryDelay();
        TestSettingsParsing();
        TestAuthenticationMethodSelection();
        TestMobileAuthenticatorControls();
        TestWeeklyReauthenticationPolicyAndClassification();
        TestFinancialAdvisorAllocationGroupsCheckBox();

        if (assertions == 0) {
            throw new AssertionError("No self-test assertions executed");
        }
        System.out.println("IBAutomater Java self-tests passed: " + assertions + " assertions");
    }

    private static void TestTotpVectors() {
        byte[] key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        TotpGenerator generator = new TotpGenerator(key);

        AssertEquals("287082", generator.generate(59L));
        AssertEquals("081804", generator.generate(1111111109L));
        AssertEquals("050471", generator.generate(1111111111L));
        AssertEquals("005924", generator.generate(1234567890L));
        AssertEquals("279037", generator.generate(2000000000L));
        AssertEquals("353130", generator.generate(20000000000L));
    }

    private static void TestBase32Validation() {
        byte[] expected = "foobar".getBytes(StandardCharsets.US_ASCII);
        AssertTrue(Arrays.equals(expected, TotpGenerator.decodeBase32("MZXW6YTBOI======")));
        AssertTrue(Arrays.equals(expected, TotpGenerator.decodeBase32("mzxw 6ytb\toi")));
        AssertTrue(Arrays.equals(
            "f".getBytes(StandardCharsets.US_ASCII),
            TotpGenerator.decodeBase32("MY======")));

        ExpectInvalidSecret("");
        ExpectInvalidSecret("A");
        ExpectInvalidSecret("MZX");
        ExpectInvalidSecret("MZ======");
        ExpectInvalidSecret("MY=====");
        ExpectInvalidSecret("MY======A");
        ExpectInvalidSecret("MY-=====");
        ExpectInvalidSecret("MY======\n");
        ExpectInvalidSecret("M\u0131======");
        ExpectInvalidSecret("MZXW6YT\u017f");
    }

    private static void TestBoundaryDelay() {
        AssertEquals(3000, TotpGenerator.getBoundaryDelayMilliseconds(0L));
        AssertEquals(1, TotpGenerator.getBoundaryDelayMilliseconds(2999L));
        AssertEquals(0, TotpGenerator.getBoundaryDelayMilliseconds(3000L));
        AssertEquals(0, TotpGenerator.getBoundaryDelayMilliseconds(15000L));
        AssertEquals(0, TotpGenerator.getBoundaryDelayMilliseconds(26999L));
        AssertEquals(6000, TotpGenerator.getBoundaryDelayMilliseconds(27000L));
        AssertEquals(3001, TotpGenerator.getBoundaryDelayMilliseconds(29999L));
        AssertEquals(3000, TotpGenerator.getBoundaryDelayMilliseconds(30000L));
        AssertEquals(3000, TotpGenerator.getBoundaryDelayMilliseconds(60000L));
        AssertEquals(3001, TotpGenerator.getBoundaryDelayMilliseconds(89999L));
    }

    private static void TestSettingsParsing() {
        Settings legacy = IBAutomater.parseSettings(
            "user\npassword\npaper\n4001\nfalse\nfalse");
        AssertEquals(TwoFactorAuthenticationMethod.IB_KEY,
            legacy.getTwoFactorAuthenticationMethod());
        AssertTrue(!legacy.getUseAccountGroupsWithAllocationMethods());

        Settings financialAdvisor = IBAutomater.parseSettings(
            "user\r\npassword\r\npaper\r\n4001\r\nfalse\r\nfalse\r\ntrue");
        AssertTrue(financialAdvisor.getUseAccountGroupsWithAllocationMethods());
        Settings terminatedFinancialAdvisor = IBAutomater.parseSettings(
            "user\npassword\npaper\n4001\nfalse\nfalse\ntrue\n");
        AssertTrue(terminatedFinancialAdvisor.getUseAccountGroupsWithAllocationMethods());

        Settings explicitIbKey = IBAutomater.parseSettings(
            "user\npassword\npaper\n4001\nfalse\nfalse\ntrue\nib-key\n");
        AssertEquals(TwoFactorAuthenticationMethod.IB_KEY,
            explicitIbKey.getTwoFactorAuthenticationMethod());

        Settings mobile = IBAutomater.parseSettings(
            "user\npassword\npaper\n4001\nfalse\nfalse\ntrue\nmobile-authenticator\n"
                + "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        AssertEquals(TwoFactorAuthenticationMethod.MOBILE_AUTHENTICATOR,
            mobile.getTwoFactorAuthenticationMethod());
        AssertEquals("287082", mobile.getTotpGenerator().generate(59L));
        Settings terminatedMobile = IBAutomater.parseSettings(
            "user\npassword\npaper\n4001\nfalse\nfalse\ntrue\nmobile-authenticator\n"
                + "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ\n");
        AssertEquals(TwoFactorAuthenticationMethod.MOBILE_AUTHENTICATOR,
            terminatedMobile.getTwoFactorAuthenticationMethod());

        ExpectFailure(() -> IBAutomater.parseSettings(
            "user\npassword\npaper\n4001\nfalse\nfalse\ntrue\nmobile-authenticator\n"));
        ExpectFailure(() -> IBAutomater.parseSettings(
            "user\npassword\npaper\n4001\nfalse\nfalse\ntrue\nib-key\nMY======"));
        ExpectFailure(() -> IBAutomater.parseSettings(
            "user\npassword\npaper\n4001\nfalse\nfalse\ntrue\nmobile-authenticator\nSECRET!"));
        ExpectFailure(() -> IBAutomater.parseSettings(
            "user\npassword\npaper\n4001\nfalse\nfalse\ntrue\nmobile-authenticator\n"
                + "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ\n\n"));
    }

    private static void TestAuthenticationMethodSelection() {
        AssertEquals(TwoFactorAuthenticationMethod.IB_KEY,
            TwoFactorAuthenticationMethod.parse("  IB-KEY "));
        AssertEquals(TwoFactorAuthenticationMethod.MOBILE_AUTHENTICATOR,
            TwoFactorAuthenticationMethod.parse(" MOBILE-AUTHENTICATOR "));

        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("One Time Passcode");
        model.addElement(" Mobile Authenticator app ");
        model.addElement("IB Key");
        AssertEquals(1, WindowEventListener.FindUniqueMethodIndex(
            model, "Mobile Authenticator app"));

        model.addElement("mobile authenticator APP");
        AssertEquals(-1, WindowEventListener.FindUniqueMethodIndex(
            model, "Mobile Authenticator app"));

        AssertTrue(WindowEventListener.IsSecurityCodeCardWindow(
            "security code card authentication"));
        AssertTrue(!WindowEventListener.IsSecurityCodeCardWindow("Enter Security Code"));
        AssertTrue(WindowEventListener.ShouldIgnoreWindowEventsAfterMobileAuthenticatorFailure(
            TwoFactorAuthenticationMethod.MOBILE_AUTHENTICATOR, true));
        AssertTrue(!WindowEventListener.ShouldIgnoreWindowEventsAfterMobileAuthenticatorFailure(
            TwoFactorAuthenticationMethod.MOBILE_AUTHENTICATOR, false));
        AssertTrue(!WindowEventListener.ShouldIgnoreWindowEventsAfterMobileAuthenticatorFailure(
            TwoFactorAuthenticationMethod.IB_KEY, true));
    }

    private static void TestMobileAuthenticatorControls() {
        AssertTrue(WindowEventListener.HasMobileAuthenticatorControls(
            CreateMobileAuthenticatorPanel(
                "Enter Mobile Authenticator app code", "", 1, 1, true)));
        AssertTrue(WindowEventListener.HasMobileAuthenticatorControls(
            CreateMobileAuthenticatorPanel(
                "<html>Mobile Authenticator app code</html>", "", 1, 1, true)));
        AssertTrue(!WindowEventListener.HasMobileAuthenticatorControls(
            CreateMobileAuthenticatorPanel("Enter security code", "", 1, 1, true)));
        AssertTrue(!WindowEventListener.HasMobileAuthenticatorControls(
            CreateMobileAuthenticatorPanel(
                "Enter Mobile Authenticator app code", "123456", 1, 1, true)));
        AssertTrue(!WindowEventListener.HasMobileAuthenticatorControls(
            CreateMobileAuthenticatorPanel(
                "Enter Mobile Authenticator app code", "", 2, 1, true)));
        AssertTrue(!WindowEventListener.HasMobileAuthenticatorControls(
            CreateMobileAuthenticatorPanel(
                "Enter Mobile Authenticator app code", "", 1, 2, true)));
        AssertTrue(!WindowEventListener.HasMobileAuthenticatorControls(
            CreateMobileAuthenticatorPanel(
                "Enter Mobile Authenticator app code", "", 1, 1, false)));

        JPanel hiddenContainer = new JPanel();
        JPanel hiddenControls = CreateMobileAuthenticatorPanel(
            "Enter Mobile Authenticator app code", "", 1, 1, true);
        hiddenControls.setVisible(false);
        hiddenContainer.add(hiddenControls);
        AssertTrue(!WindowEventListener.HasMobileAuthenticatorControls(hiddenContainer));

        JPanel selector = new JPanel();
        selector.add(new JTextArea("Select second factor device"));
        DefaultListModel<String> selectorModel = new DefaultListModel<>();
        selectorModel.addElement("Mobile Authenticator app");
        selector.add(new JList<>(selectorModel));
        selector.add(new JButton("OK"));
        AssertTrue(!WindowEventListener.HasMobileAuthenticatorControls(selector));
    }

    private static void TestWeeklyReauthenticationPolicyAndClassification() {
        AssertTrue(!WindowEventListener.ShouldRejectMobileAuthenticatorRelogin(
            TwoFactorAuthenticationMethod.MOBILE_AUTHENTICATOR, false));
        AssertTrue(WindowEventListener.ShouldRejectMobileAuthenticatorRelogin(
            TwoFactorAuthenticationMethod.MOBILE_AUTHENTICATOR, true));
        AssertTrue(!WindowEventListener.ShouldRejectMobileAuthenticatorRelogin(
            TwoFactorAuthenticationMethod.IB_KEY, true));
        AssertTrue(WindowEventListener.ShouldFailMobileAuthenticatorForTooManyLoginAttempts(
            TwoFactorAuthenticationMethod.MOBILE_AUTHENTICATOR, false));
        AssertTrue(WindowEventListener.ShouldFailMobileAuthenticatorForTooManyLoginAttempts(
            TwoFactorAuthenticationMethod.MOBILE_AUTHENTICATOR, true));
        AssertTrue(!WindowEventListener.ShouldFailMobileAuthenticatorForTooManyLoginAttempts(
            TwoFactorAuthenticationMethod.IB_KEY, false));
        AssertTrue(!WindowEventListener.ShouldFailMobileAuthenticatorForTooManyLoginAttempts(
            TwoFactorAuthenticationMethod.IB_KEY, true));

        JPanel tokenExpired = new JPanel();
        tokenExpired.add(new JLabel(
            "Login failed = Soft token=0 received instead of expected permanent for example:4001 (SSL)"));
        AssertTrue(WindowEventListener.IsAutoRestartTokenExpired(tokenExpired));
        AssertTrue(!WindowEventListener.IsGenericLoginFailureWindow("Login failed", tokenExpired));

        JPanel loginFailed = new JPanel();
        loginFailed.add(new JLabel("Invalid user name or password"));
        AssertTrue(!WindowEventListener.IsAutoRestartTokenExpired(loginFailed));
        AssertTrue(WindowEventListener.IsGenericLoginFailureWindow("Login failed", loginFailed));
        AssertTrue(WindowEventListener.IsGenericLoginFailureWindow(
            "Unrecognized Username or Password", loginFailed));
        AssertTrue(!WindowEventListener.IsGenericLoginFailureWindow("Other error", loginFailed));

        JPanel nearMatch = new JPanel();
        nearMatch.add(new JLabel("Soft token=1 received instead of expected permanent"));
        AssertTrue(!WindowEventListener.IsAutoRestartTokenExpired(nearMatch));
    }

    private static void TestFinancialAdvisorAllocationGroupsCheckBox() {
        String checkBoxText = "Use Account Groups with Allocation Methods";
        List<String> messages = new ArrayList<>();

        JPanel panel = new JPanel();
        JCheckBox checkBox = new JCheckBox(checkBoxText);
        panel.add(checkBox);
        AssertEquals(null, WindowEventListener.ConfigureFinancialAdvisorAllocationGroupsCheckBox(
            panel, true, messages::add));
        AssertTrue(checkBox.isSelected());
        AssertEquals("Checkbox: [" + checkBoxText + "] - Selected: [true]",
            messages.get(messages.size() - 1));

        panel = new JPanel();
        checkBox = new JCheckBox("use account groups with allocation methods (recommended)", true);
        panel.add(checkBox);
        AssertEquals(null, WindowEventListener.ConfigureFinancialAdvisorAllocationGroupsCheckBox(
            panel, true, messages::add));
        AssertTrue(checkBox.isSelected());

        AssertEquals("check box not found",
            WindowEventListener.ConfigureFinancialAdvisorAllocationGroupsCheckBox(
                new JPanel(), true, messages::add));
        AssertEquals(null, WindowEventListener.ConfigureFinancialAdvisorAllocationGroupsCheckBox(
            new JPanel(), false, messages::add));

        panel = new JPanel();
        JCheckBox first = new JCheckBox(checkBoxText);
        JCheckBox second = new JCheckBox(checkBoxText + ".");
        panel.add(first);
        panel.add(second);
        AssertEquals("multiple matching check boxes found",
            WindowEventListener.ConfigureFinancialAdvisorAllocationGroupsCheckBox(
                panel, true, messages::add));
        AssertTrue(!first.isSelected() && !second.isSelected());
        first.setSelected(true);
        second.setSelected(true);
        AssertEquals(null, WindowEventListener.ConfigureFinancialAdvisorAllocationGroupsCheckBox(
            panel, false, messages::add));
        AssertTrue(!first.isSelected() && !second.isSelected());

        panel = new JPanel();
        checkBox = new JCheckBox(checkBoxText);
        checkBox.setEnabled(false);
        panel.add(checkBox);
        AssertEquals("check box is disabled and unchecked",
            WindowEventListener.ConfigureFinancialAdvisorAllocationGroupsCheckBox(
                panel, true, messages::add));
        checkBox.setSelected(true);
        AssertEquals(null, WindowEventListener.ConfigureFinancialAdvisorAllocationGroupsCheckBox(
            panel, true, messages::add));
        AssertTrue(checkBox.isSelected());

        panel = new JPanel();
        checkBox = new NonSelectableCheckBox(checkBoxText, false);
        panel.add(checkBox);
        AssertEquals("check box did not retain the requested state",
            WindowEventListener.ConfigureFinancialAdvisorAllocationGroupsCheckBox(
                panel, true, messages::add));

        panel = new JPanel();
        checkBox = new NonSelectableCheckBox(checkBoxText, true);
        panel.add(checkBox);
        AssertEquals("check box did not retain the requested state",
            WindowEventListener.ConfigureFinancialAdvisorAllocationGroupsCheckBox(
                panel, false, messages::add));
    }

    private static JPanel CreateMobileAuthenticatorPanel(
        String instruction, String fieldText, int fieldCount, int okButtonCount,
        boolean okButtonEnabled) {
        JPanel panel = new JPanel();
        panel.add(new JLabel(instruction));
        for (int index = 0; index < fieldCount; index++) {
            panel.add(new JTextField(fieldText));
        }
        for (int index = 0; index < okButtonCount; index++) {
            JButton okButton = new JButton("OK");
            okButton.setEnabled(okButtonEnabled);
            panel.add(okButton);
        }
        panel.add(new JButton("Cancel"));
        return panel;
    }

    private static void ExpectInvalidSecret(String secret) {
        try {
            TotpGenerator.decodeBase32(secret);
            throw new AssertionError("Expected setup secret validation to fail");
        }
        catch (IllegalArgumentException exception) {
            AssertEquals("Invalid Mobile Authenticator setup secret", exception.getMessage());
        }
    }

    private static void ExpectFailure(TestAction action) {
        try {
            action.run();
            throw new AssertionError("Expected operation to fail");
        }
        catch (IllegalArgumentException exception) {
            assertions++;
        }
    }

    private static void AssertTrue(boolean condition) {
        assertions++;
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    private static void AssertEquals(Object expected, Object actual) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected [" + expected + "] but found [" + actual + "]");
        }
    }

    private static final class NonSelectableCheckBox extends JCheckBox {
        private boolean blockSelection;

        private NonSelectableCheckBox(String text, boolean selected) {
            super(text, selected);
            blockSelection = true;
        }

        @Override
        public void setSelected(boolean selected) {
            if (!blockSelection) {
                super.setSelected(selected);
            }
        }
    }

    private interface TestAction {
        void run();
    }
}
