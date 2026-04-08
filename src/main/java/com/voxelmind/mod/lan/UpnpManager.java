package com.voxelmind.mod.lan;

import com.dosse.upnp.UPnP;
import com.voxelmind.mod.VoxelMindMod;

/**
 * Wrapper around WaifUPnP for port forwarding.
 * All methods are safe to call even when UPnP is unavailable.
 */
public class UpnpManager {

    public static boolean tryOpenPort(int port) {
        try {
            if (UPnP.isUPnPAvailable()) {
                boolean success = UPnP.openPortTCP(port);
                if (success) {
                    VoxelMindMod.LOGGER.info("UPnP: Opened TCP port {}", port);
                } else {
                    VoxelMindMod.LOGGER.warn("UPnP: Failed to open TCP port {}", port);
                }
                return success;
            }
            VoxelMindMod.LOGGER.info("UPnP not available on this network.");
            return false;
        } catch (Exception e) {
            VoxelMindMod.LOGGER.warn("UPnP error: {}", e.getMessage());
            return false;
        }
    }

    public static void closePort(int port) {
        try {
            if (UPnP.isUPnPAvailable()) {
                UPnP.closePortTCP(port);
                VoxelMindMod.LOGGER.info("UPnP: Closed TCP port {}", port);
            }
        } catch (Exception e) {
            VoxelMindMod.LOGGER.warn("UPnP close error: {}", e.getMessage());
        }
    }

    public static String getExternalIP() {
        try {
            if (UPnP.isUPnPAvailable()) {
                return UPnP.getExternalIP();
            }
        } catch (Exception e) {
            VoxelMindMod.LOGGER.warn("UPnP external IP error: {}", e.getMessage());
        }
        // Fallback: public IP service
        return fetchPublicIp();
    }

    public static boolean isAvailable() {
        try {
            return UPnP.isUPnPAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    private static String fetchPublicIp() {
        try {
            var http = java.net.http.HttpClient.newHttpClient();
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.ipify.org"))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();
            var response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body().trim();
            }
        } catch (Exception e) {
            VoxelMindMod.LOGGER.warn("Failed to fetch public IP: {}", e.getMessage());
        }
        return null;
    }
}
