package org.example.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class DeviceUtil {

    /**
     * Retrieves a unique hardware device identifier using PowerShell, WMIC fallback, or username/computer name concatenation.
     */
    public static String getDeviceId() {
        String uuid = getUUIDFromPowerShell();
        if (uuid != null && !uuid.isEmpty()) {
            return uuid;
        }

        uuid = getUUIDFromWMIC();
        if (uuid != null && !uuid.isEmpty()) {
            return uuid;
        }

        return System.getProperty("user.name") + "_" + System.getenv("COMPUTERNAME");
    }

    /**
     * Attempts to retrieve the system UUID via PowerShell Win32_ComputerSystemProduct.
     */
    private static String getUUIDFromPowerShell() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                    "powershell", "-Command", "Get-CimInstance -ClassName Win32_ComputerSystemProduct | Select-Object -ExpandProperty UUID"
            });
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    return line.trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Attempts to retrieve the system UUID via the legacy WMIC command-line utility.
     */
    private static String getUUIDFromWMIC() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"wmic", "csproduct", "get", "UUID"});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                String output = sb.toString().replace("UUID", "").trim();
                if (!output.isEmpty()) {
                    return output;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}