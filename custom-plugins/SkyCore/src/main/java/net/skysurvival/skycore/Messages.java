package net.skysurvival.skycore;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** config.yml'deki &-renk kodlu mesajlari okur, {yer-tutucu}lari doldurur ve gonderir. */
final class Messages {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final SkyCore plugin;
    private String prefix;

    Messages(SkyCore plugin) {
        this.plugin = plugin;
        reload();
    }

    void reload() {
        prefix = plugin.getConfig().getString("onek", "");
    }

    /** "{anahtar}", "deger" ciftlerini yerlestirir. */
    static String fill(String text, Object... pairs) {
        String result = text;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result = result.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return result;
    }

    static Component color(String text) {
        return LEGACY.deserialize(text);
    }

    /** Esya adi/aciklamasi icin: Minecraft'in varsayilan italik yazisini kapatir. */
    static Component itemText(String text) {
        return color(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    String raw(String path, Object... pairs) {
        return fill(plugin.getConfig().getString(path, ""), pairs);
    }

    List<String> rawList(String path, Object... pairs) {
        List<String> lines = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList(path)) {
            lines.add(fill(line, pairs));
        }
        return lines;
    }

    /** Mesaji onekle gonderir. Ayarlarda bos birakilan mesaj gonderilmez. */
    void send(CommandSender to, String path, Object... pairs) {
        String text = raw(path, pairs);
        if (!text.isEmpty()) {
            to.sendMessage(color(prefix + text));
        }
    }

    /** Liste halindeki mesaji oneksiz, satir satir gonderir. */
    void sendLines(CommandSender to, String path, Object... pairs) {
        for (String line : rawList(path, pairs)) {
            to.sendMessage(color(line));
        }
    }

    void actionBar(Player to, String path, Object... pairs) {
        String text = raw(path, pairs);
        if (!text.isEmpty()) {
            to.sendActionBar(color(text));
        }
    }

    void broadcast(String path, Object... pairs) {
        String text = raw(path, pairs);
        if (!text.isEmpty()) {
            broadcastText(text);
        }
    }

    void broadcastText(String text) {
        plugin.getServer().broadcast(color(text));
    }
}
