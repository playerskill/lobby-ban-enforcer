package com.example.lobbyenforcer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;

public class LobbyBanEnforcer extends JavaPlugin implements Listener {

    private static final String BAN_QUERY =
            "SELECT reason, expiry FROM banlist WHERE object = ? AND type = 'nick'";

    private final Object databaseLock = new Object();
    private volatile Connection connection;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            Class.forName("com.mysql.jdbc.Driver");

            String host = getConfig().getString("database.host", "localhost");
            int port = getConfig().getInt("database.port", 3306);
            String database = getConfig().getString("database.name", "your_database");
            String user = getConfig().getString("database.user", "your_user");
            String password = getConfig().getString("database.password", "your_password");
            int timeout = getConfig().getInt("database.connection-timeout-ms", 2000);

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&useUnicode=true&characterEncoding=UTF-8"
                    + "&serverTimezone=UTC&connectTimeout=" + timeout;

            Properties properties = new Properties();
            properties.setProperty("user", user);
            properties.setProperty("password", password);

            connection = DriverManager.getConnection(url, properties);
            getServer().getPluginManager().registerEvents(this, this);
            getLogger().info("Database connection established.");
        } catch (Exception exception) {
            getLogger().severe("Could not connect to the ban database. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);

        synchronized (databaseLock) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // The server is shutting down; there is nothing left to recover.
                } finally {
                    connection = null;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!hasOpenConnection()) {
            return;
        }

        final String playerName = event.getPlayer().getName();
        final UUID playerId = event.getPlayer().getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            BanRecord ban = findActiveBan(playerName);
            if (ban == null) {
                return;
            }

            final String kickMessage = createKickMessage(ban.reason);
            Bukkit.getScheduler().runTask(this, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.kickPlayer(kickMessage);
                }
            });
        });
    }

    private boolean hasOpenConnection() {
        synchronized (databaseLock) {
            try {
                return connection != null && !connection.isClosed();
            } catch (SQLException exception) {
                return false;
            }
        }
    }

    private BanRecord findActiveBan(String playerName) {
        synchronized (databaseLock) {
            if (connection == null) {
                return null;
            }

            try (PreparedStatement statement = connection.prepareStatement(BAN_QUERY)) {
                statement.setString(1, playerName);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return null;
                    }

                    long expiry = resultSet.getLong("expiry");
                    if (expiry != -1 && expiry <= System.currentTimeMillis()) {
                        return null;
                    }

                    return new BanRecord(resultSet.getString("reason"), expiry);
                }
            } catch (SQLException exception) {
                getLogger().warning("Could not check the ban for " + playerName + ".");
                return null;
            }
        }
    }

    private String createKickMessage(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return ChatColor.RED + "Вы забанены.";
        }
        return ChatColor.RED + "Бан: " + ChatColor.YELLOW + reason;
    }

    private static final class BanRecord {
        private final String reason;
        private final long expiry;

        private BanRecord(String reason, long expiry) {
            this.reason = reason;
            this.expiry = expiry;
        }
    }
}