import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class LobbyBanEnforcer extends JavaPlugin {

    private Path configPath;
    private Map<String, Object> config;

    @Override
    public void onEnable() {
        // 1. Путь к папке плагина и config.yml
        Path dataFolder = Paths.get(getDataFolder().getPath());
        configPath = dataFolder.resolve("config.yml");

        // 2. Создаём папку, если её нет
        try {
            if (!Files.exists(dataFolder)) {
                Files.createDirectories(dataFolder);
                getLogger().info("Папка плагина создана: " + dataFolder);
            }
        } catch (IOException e) {
            getLogger().severe("Не удалось создать папку плагина: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Создаём config.yml, если его нет
        if (!Files.exists(configPath)) {
            createDefaultConfig();
            getLogger().info("Создан новый config.yml. Пожалуйста, заполните настройки вручную.");
            Bukkit.getPluginManager().disablePlugin(this); // Отключаем, чтобы админ сразу настроил конфиг
            return;
        }

        // 4. Читаем конфиг
        try {
            config = loadConfig();
            getLogger().info("Конфиг успешно загружен.");

            // Пример: читаем пароль из конфига
            String dbPassword = (String) config.getOrDefault("database.password", "");
            if (dbPassword == null || dbPassword.isEmpty()) {
                getLogger().warning("Пароль от БД не указан в config.yml!");
                // Здесь можно не отключать плагин, а просто не инициализировать подключение к БД
            }

            // Дальше — твоя логика плагина
            // Например, регистрация команд/событий

        } catch (Exception e) {
            getLogger().severe("Ошибка чтения config.yml: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // При выключении можно закрыть соединение с БД и т.п.
    }

    // Создаёт config.yml с шаблоном
    private void createDefaultConfig() {
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("enabled", true);
        defaultConfig.put("lobby-world", "world");
        
        // Шаблон для БД — пароль пустой, его заполняют вручную
        Map<String, Object> dbConfig = new HashMap<>();
        dbConfig.put("host", "localhost");
        dbConfig.put("port", 3306);
        dbConfig.put("database", "lobby_bans");
        dbConfig.put("username", "lobby_user");
        dbConfig.put("password", ""); // <-- сюда вписывают пароль вручную
        defaultConfig.put("database", dbConfig);

        Yaml yaml = new Yaml();
        try (FileWriter writer = new FileWriter(configPath.toFile(), StandardCharsets.UTF_8)) {
            yaml.dump(defaultConfig, writer);
        } catch (IOException e) {
            getLogger().severe("Не удалось создать config.yml: " + e.getMessage());
        }
    }

    // Загружает config.yml
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadConfig() throws IOException {
        Yaml yaml = new Yaml();
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            return yaml.load(reader);
        }
    }
}
