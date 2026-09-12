package com.example.lobbyban;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.logging.Level;

public class LobbyBanEnforcer extends JavaPlugin implements Listener {

    private Connection connection;
    private String dbHost;
    private int dbPort;
    private String dbName;
    private String dbUser;
    private String dbPass;
    private String dbTable;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        dbHost = getConfig().getString("database.host", "localhost");
        dbPort = getConfig().getInt("database.port", 3306);
        dbName = getConfig().getString("database.name", "easybans");
        dbUser = getConfig().getString("database.user", "root");
        dbPass = getConfig().getString("database.password", "");
        dbTable = getConfig().getString("database.table", "bans");

        try {
            // Для старых версий Java/Paper лучше использовать этот драйвер
            Class.forName("com.mysql.jdbc.Driver");
            String url = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName + "?useSSL=false&serverTimezone=UTC";
            connection = DriverManager.getConnection(url, dbUser, dbPass);
            getLogger().info("Успешное подключение к базе данных EasyBans!");
        } catch (Exception e) {
            getLogger().severe("Не удалось подключиться к БД! Плагин не будет работать.");
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Плагин LobbyBanEnforcer включен. Проверка банов активна.");
    }

    @Override
    public void onDisable() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        String playerNick = player.getName();
        long currentTime = System.currentTimeMillis();

        // Логирование только для админов в консоль, чтобы не спамить
        getLogger().fine("Проверка входа игрока: " + playerNick);

        // ВАЖНО: Мы берем и причину (reason), и время (expiry)
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT reason, expiry FROM " + dbTable + " WHERE object = ? AND type = 'nick'")) {
            
            ps.setString(1, playerNick);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                long expiry = rs.getLong("expiry");
                String dbReason = rs.getString("reason");

                // Логика проверки бана:
                // expiry = -1 -> вечный бан
                // expiry > currentTime -> бан еще действует
                if (expiry == -1 || expiry > currentTime) {
                    
                    // Если в базе есть причина - берем её, если нет - ставим дефолтную
                    String finalReason = (dbReason != null && !dbReason.isEmpty()) 
                            ? dbReason 
                            : "Вы забанены на проекте. Обратитесь к администрации.";

                    getLogger().warning("Игрок " + playerNick + " забанен! Кикаем с лобби. Причина: " + finalReason);
                    
                    // ЭТОТ МЕТОД ДЕЛАЕТ ВСЁ, ЧТО ТЫ ХОТЕЛ:
                    // 1. Разрывает соединение.
                    // 2. Показывает finalReason на экране загрузки у игрока.
                    // 3. Не пишет в чат лобби.
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, finalReason);
                    
                    return;
                } else {
                    getLogger().info("Игрок " + playerNick + " был забанен, но срок бана истек.");
                }
            } else {
                getLogger().fine("Игрок " + playerNick + " не найден в списке банов. Разрешаем вход.");
            }

        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Ошибка при проверке бана в БД", e);
            // При ошибке БД мы НЕ кикаем игрока, чтобы избежать массового кика из-за падения MySQL
        }
    }
}
