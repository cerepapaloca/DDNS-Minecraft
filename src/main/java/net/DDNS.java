package net;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;

@SuppressWarnings("deprecation")
public final class DDNS extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        password = getConfig().getString("Password-dns");
        domain = getConfig().getString("domain");
        IpNow = getConfig().getString("last-ip");
        new BukkitRunnable() {
            public void run() {
                try {
                    updateIP();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }.runTaskTimerAsynchronously(this, 0, 20*10);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private static final String DYNAMIC_DNS_URL = "https://dynamicdns.park-your-domain.com/update";
    private static String domain = "";
    private static String password = "";
    private static String IpNow = null;

    public String getPublicIP() throws Exception {
        String url = "https://api.ipify.org";
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        return content.toString();
    }

    private static boolean isStarting = true;

    public void updateIP() {
        if (!isInternetAvailable()) return;
        try {
            String ipPublic = getPublicIP();
            if (IpNow == null) {
                IpNow = ipPublic;
            }
            if (!IpNow.equals(getPublicIP())) { // Si son diferentes es a cambiado
                String url = DYNAMIC_DNS_URL + "?host=@&domain=" + domain + "&password=" + password + "&ip=" + ipPublic;
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    getLogger().info(String.format("La ip Se actualizó %s -> %s",IpNow ,ipPublic));
                    IpNow = ipPublic;
                } else {
                    getLogger().severe("Error al actualizar la IP. Código de respuesta: " + responseCode);
                }
            }else {
                if (isStarting) {
                    getLogger().info("IP: " + IpNow);
                    isStarting = false;
                }
            }
            getConfig().set("last-ip", ipPublic);
            saveConfig();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isInternetAvailable() {
        try {
            URL url = new URL("https://www.google.com");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000); // Tiempo límite para conectarse
            connection.connect();
            return connection.getResponseCode() >= 200 && connection.getResponseCode() < 300;
        } catch (IOException e) {
            return false;
        }
    }
}
