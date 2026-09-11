package com.example.lobbyenforcer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class LobbyBanEnforcer extends JavaPlugin {

    @Override
    public void onEnable() {
        // 1. Создает папку плагина и config.yml, если их нет
        saveDefaultConfig();

        // 2. Читаем настройки
        boolean enabled = getConfig().getBoolean("enabled", true);
        String lobbyWorld = getConfig().getString("lobby-world", "world");

        String dbHost = getConfig().getString("database.host", "localhost");
        int dbPort = getConfig().getInt("database.port", 3306);
        String dbName = getConfig().getString("database.database", "lobby_bans");
        String dbUser = getConfig().getString("database.username", "lobby_user");
        String dbPass = getConfig().getString("database.password", "");

        // 3. Логика проверки
        if (!enabled) {
            getLogger().info("Плагин отключён в конфиге.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (dbPass == null || dbPass.isEmpty()) {
            getLogger().warning("Пароль от БД не указан в config.yml!");
            getLogger().warning("Заполните поле database.password и перезапустите сервер.");
            // Плагин продолжит работу, но без подключения к БД
        }

        getLogger().info("LobbyBanEnforcer запущен! Лобби: " + lobbyWorld);
        
        // Здесь будет твоя основная логика
    }

    @Override
    public void onDisable() {
        getLogger().info("LobbyBanEnforcer выключен.");
    }
}
