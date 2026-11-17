package io.github.dheightsan.tradepost.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Messages {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final YamlConfiguration yaml;

    private Messages(YamlConfiguration yaml) {
        this.yaml = yaml;
    }

    public static Messages load(File file, InputStream defaults) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (defaults != null) {
            try (InputStreamReader reader = new InputStreamReader(defaults, StandardCharsets.UTF_8)) {
                yaml.setDefaults(YamlConfiguration.loadConfiguration(reader));
            } catch (java.io.IOException ignored) {
            }
        }
        return new Messages(yaml);
    }

    public static Messages of(YamlConfiguration yaml) {
        return new Messages(yaml);
    }

    public String raw(String key) {
        return yaml.getString(key, "<red>Missing message: " + key);
    }

    public Component get(String key, TagResolver... resolvers) {
        return MM.deserialize(raw(key), resolvers);
    }

    public Component item(String key, TagResolver... resolvers) {
        return noItalic(get(key, resolvers));
    }

    public List<Component> itemLines(String key, TagResolver... resolvers) {
        List<Component> lines = new ArrayList<>();
        for (String line : yaml.getStringList(key)) {
            lines.add(noItalic(MM.deserialize(line, resolvers)));
        }
        return lines;
    }

    public static Component parse(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(miniMessage, resolvers);
    }

    public static Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        String text = raw(key);
        if (text.isEmpty()) {
            return;
        }
        to.sendMessage(MM.deserialize(raw("prefix") + text, resolvers));
    }
}
