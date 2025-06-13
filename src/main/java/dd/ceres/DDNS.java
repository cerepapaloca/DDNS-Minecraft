package dd.ceres;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.ConfigurationSection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public final class DDNS extends JavaPlugin {

    private static final String DYNAMIC_DNS_URL = "https://dynamicdns.park-your-domain.com/update";
    private final List<DomainEntry> entries = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadEntriesFromConfig();

        long now = System.currentTimeMillis();
        for (DomainEntry e : entries) {
            if (e.lastUpdate < now) {
                try {
                    forceUpdateIP(e, getPublicIP());
                } catch (Exception ex) {
                    getLogger().warning(formatException("Error al forzar actualización de " + e.host + "." + e.domain, ex));
                }
                e.lastUpdate = now + 1000L * 60 * 15;
            }
        }
        saveEntriesToConfig();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    updateAll();
                } catch (Exception ex) {
                    getLogger().warning(formatException("Error en updateAll()", ex));
                }
            }
        }.runTaskTimerAsynchronously(this, 0, 20L * 10);
    }

    @Override
    public void onDisable() {
        saveEntriesToConfig();
    }

    private void loadEntriesFromConfig() {
        ConfigurationSection sec = getConfig().getConfigurationSection("domains");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection d = sec.getConfigurationSection(key);
            DomainEntry e = new DomainEntry();
            e.domain      = d.getString("domain");
            e.host        = d.getString("host", "@");
            e.password    = d.getString("password");
            e.lastUpdate  = d.getLong("last-update", 0L);
            e.lastIp      = d.getString("last-ip", null);
            entries.add(e);
        }
    }

    private void saveEntriesToConfig() {
        getConfig().set("domains", null); // Limpiar sección
        for (int i = 0; i < entries.size(); i++) {
            DomainEntry e = entries.get(i);
            String path = "domains." + i;
            getConfig().set(path + ".domain",       e.domain);
            getConfig().set(path + ".host",         e.host);
            getConfig().set(path + ".password",     e.password);
            getConfig().set(path + ".last-update",  e.lastUpdate);
            getConfig().set(path + ".last-ip",      e.lastIp);
        }
        saveConfig();
    }

    private void updateAll() throws IOException {
        if (!isInternetAvailable()) return;
        String currentPublicIp = getPublicIP();

        for (DomainEntry e : entries) {
            if (e.lastIp == null || !e.lastIp.equals(currentPublicIp)) {
                forceUpdateIP(e, currentPublicIp);
            } else if (e.isStarting) {
                getLogger().info("[" + e.host + "." + e.domain + "] IP actual: " + e.lastIp);
                e.isStarting = false;
            }
            e.lastIp = currentPublicIp;
        }

        saveEntriesToConfig();
    }

    private void forceUpdateIP(DomainEntry e, String newIp) throws IOException {
        String url = DYNAMIC_DNS_URL +
                "?host="   + URLEncoder.encode(e.host, StandardCharsets.UTF_8) +
                "&domain=" + URLEncoder.encode(e.domain, StandardCharsets.UTF_8) +
                "&password=" + URLEncoder.encode(e.password, StandardCharsets.UTF_8) +
                "&ip="     + URLEncoder.encode(newIp, StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        if (code == HttpURLConnection.HTTP_OK) {
            getLogger().info(String.format("DDNS actualizado: %s.%s -> %s", e.host, e.domain, newIp));
            e.lastIp = newIp;
            e.lastUpdate = System.currentTimeMillis() + 1000L * 60 * 15;
        } else {
            getLogger().severe("Error al actualizar " + e.host + "." + e.domain + ". Código: " + code);
        }
    }

    public String getPublicIP() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.ipify.org").openConnection();
        conn.setRequestMethod("GET");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            return sb.toString().trim();
        }
    }

    public static boolean isInternetAvailable() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL("https://www.google.com").openConnection();
            conn.setConnectTimeout(3000);
            conn.connect();
            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (IOException ex) {
            return false;
        }
    }

    private String formatException(String msg, Exception ex) {
        StringBuilder b = new StringBuilder(msg)
                .append(" [").append(ex.getClass().getSimpleName())
                .append("=").append(ex.getMessage()).append("]\n");
        for (StackTraceElement ste : ex.getStackTrace()) {
            b.append("    at ").append(ste).append("\n");
        }
        return b.toString();
    }

    private static class DomainEntry {
        String domain;
        String host;
        String password;
        long lastUpdate;
        String lastIp;
        boolean isStarting = true;
    }
}
