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

package ibautomater;

import java.awt.Toolkit;
import java.awt.Window;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * IBAutomater is the component responsible for the interaction with the IBGateway user interface.
 *
 * @author QuantConnect Corporation
 */
public final class IBAutomater {
    static final String MOBILE_AUTHENTICATOR_FAILURE_MARKER =
        "Error: Mobile Authenticator authentication failed:";

    private final Settings settings;
    private PrintWriter printWriter = null;
    private Window mainWindow;

    /**
     * The Java agent premain method is called before the IBGateway main method.
     *
     * @param args The name of a text file containing the values of the IBAutomater settings
     */
    public static void premain(String args) throws Exception {
        Path settingsPath = Paths.get(args);
        boolean mobileAuthenticator = false;
        try {
            String fileContent;
            try {
                fileContent = new String(Files.readAllBytes(settingsPath), StandardCharsets.UTF_8);
                mobileAuthenticator = isMobileAuthenticatorConfiguration(fileContent);
            }
            finally {
                // The settings file contains credentials and is only needed during premain.
                Files.deleteIfExists(settingsPath);
            }

            new IBAutomater(parseSettings(fileContent));
        }
        catch (Exception exception) {
            if (mobileAuthenticator) {
                System.out.println(MOBILE_AUTHENTICATOR_FAILURE_MARKER + " invalid configuration");
            }
            throw exception;
        }
    }

    static Settings parseSettings(String fileContent) {
        // Accept legacy six-row, Financial Advisor seven-row, and authentication nine-row payloads.
        String[] values = splitSettings(fileContent);
        if (values.length != 6 && values.length != 7 && values.length != 9) {
            throw new IllegalArgumentException("Invalid IBAutomater settings format");
        }

        TwoFactorAuthenticationMethod authenticationMethod = TwoFactorAuthenticationMethod.IB_KEY;
        String mobileAuthenticatorSecret = null;
        if (values.length == 9) {
            authenticationMethod = TwoFactorAuthenticationMethod.parse(values[7]);
            mobileAuthenticatorSecret = values[8];
        }

        return new Settings(
            values[0],
            values[1],
            values[2],
            Integer.parseInt(values[3]),
            Boolean.parseBoolean(values[4]),
            Boolean.parseBoolean(values[5]),
            values.length > 6 && Boolean.parseBoolean(values[6]),
            authenticationMethod,
            mobileAuthenticatorSecret);
    }

    private static boolean isMobileAuthenticatorConfiguration(String fileContent) {
        try {
            String[] values = splitSettings(fileContent);
            return values.length == 9
                && TwoFactorAuthenticationMethod.MOBILE_AUTHENTICATOR.getToken()
                    .equalsIgnoreCase(values[7].trim());
        }
        catch (Exception exception) {
            return false;
        }
    }

    private static String[] splitSettings(String fileContent) {
        String[] values = fileContent.split("\\r?\\n", -1);
        // A line terminator is not a settings row. Preserve the ninth empty
        // IB Key secret, while tolerating one conventional terminator after a
        // legacy seven-row or new nine-row payload.
        if ((values.length == 8 || values.length == 10)
            && values[values.length - 1].length() == 0) {
            return Arrays.copyOf(values, values.length - 1);
        }
        return values;
    }

    /**
     * Creates a new instance of the {@link IBAutomater} class.
     * This overload retains the established behavior of deselecting the
     * "Use Account Groups with Allocation Methods" check box.
     *
     * @param userName The IB user name
     * @param password The IB password
     * @param tradingMode The trading mode (allowed values are "live" and "paper")
     * @param portNumber The socket port number to be used for API connections
     * @param exportIbGatewayLogs If true, IBGateway logs will be exported at predefined times
     * (currently at startup and when unknown windows are detected)
     * @param restarting If true, the automater will assume the gateway is starting after a 
     * soft daily restart and won't try to log in
     */
    public IBAutomater(String userName, String password, String tradingMode, int portNumber, boolean exportIbGatewayLogs, boolean restarting) {
        this(userName, password, tradingMode, portNumber, exportIbGatewayLogs, restarting, false);
    }

    /**
     * Creates a new instance of the {@link IBAutomater} class.
     *
     * @param userName The IB user name
     * @param password The IB password
     * @param tradingMode The trading mode (allowed values are "live" and "paper")
     * @param portNumber The socket port number to be used for API connections
     * @param exportIbGatewayLogs If true, IBGateway logs will be exported at predefined times
     * (currently at startup and when unknown windows are detected)
     * @param restarting If true, the automater will assume the gateway is starting after a
     * soft daily restart and won't try to log in
     * @param useAccountGroupsWithAllocationMethods The desired state of the
     * "Use Account Groups with Allocation Methods" check box: true selects it; false deselects it
     */
    public IBAutomater(String userName, String password, String tradingMode, int portNumber,
        boolean exportIbGatewayLogs, boolean restarting, boolean useAccountGroupsWithAllocationMethods) {
        this(userName, password, tradingMode, portNumber, exportIbGatewayLogs, restarting,
            useAccountGroupsWithAllocationMethods, TwoFactorAuthenticationMethod.IB_KEY, null);
    }

    /**
     * Creates a new instance of the {@link IBAutomater} class.
     *
     * @param userName The IB user name
     * @param password The IB password
     * @param tradingMode The trading mode (allowed values are "live" and "paper")
     * @param portNumber The socket port number to be used for API connections
     * @param exportIbGatewayLogs If true, IBGateway logs will be exported at predefined times
     * @param restarting If true, the automater will assume the gateway is starting after a
     * soft daily restart and won't try to log in
     * @param useAccountGroupsWithAllocationMethods The desired state of the
     * "Use Account Groups with Allocation Methods" check box
     * @param twoFactorAuthenticationMethod The two-factor authentication method
     * @param mobileAuthenticatorSecret The Base32 setup key for Gateway's
     * "Mobile Authenticator app" option
     * @throws IllegalArgumentException If the authentication method and setup key are invalid
     */
    IBAutomater(String userName, String password, String tradingMode, int portNumber,
        boolean exportIbGatewayLogs, boolean restarting, boolean useAccountGroupsWithAllocationMethods,
        TwoFactorAuthenticationMethod twoFactorAuthenticationMethod, String mobileAuthenticatorSecret) {
        this(new Settings(userName, password, tradingMode, portNumber, exportIbGatewayLogs,
            restarting, useAccountGroupsWithAllocationMethods, twoFactorAuthenticationMethod,
            mobileAuthenticatorSecret));
    }

    private IBAutomater(Settings settings) {
        this.settings = settings;

        try
        {
            this.printWriter = new PrintWriter(new FileWriter("IBAutomater.log"), true);
        }
        catch (IOException exception)
        {
            System.out.println(exception.getMessage());
        }

        Toolkit.getDefaultToolkit().addAWTEventListener(new WindowEventListener(this), 64L);

        this.logMessage("IBGateway started");
    }

    /**
     * Writes the text message to the log file.
     *
     * @param text The text message to be logged
     */
    public void logMessage(String text) {
        try
        {
            this.printWriter.println(text);
        }
        catch (Exception exception)
        {
            System.out.println(exception.getMessage());
        }
    }

    /**
     * Writes the exception message to the log file.
     *
     * @param exception The exception to be logged
     */
    public void logError(Exception exception) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);

        this.logMessage("Error: " + sw.toString());
    }

    /**
     * Gets the IBGateway main window.
     *
     * @return Returns the IBGateway main window
     */
    public Window getMainWindow() {
        return this.mainWindow;
    }

    /**
     * Sets the IBGateway main window.
     *
     * @param window The IBGateway main window
     */
    public void setMainWindow(Window window) {
        this.mainWindow = window;
    }

    /**
     * Gets the IBAutomater settings.
     *
     * @return Returns the {@link Settings} instance
     */
    public Settings getSettings() {
        return this.settings;
    }
}
