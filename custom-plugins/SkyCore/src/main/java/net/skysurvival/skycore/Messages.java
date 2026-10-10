package net.skysurvival.skycore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** config.yml'deki &-renk kodlu mesajlari okur, {yer-tutucu}lari doldurur ve gonderir. */
final class Messages {
    /** Mesajdaki https://, www. ve discord.gg/ baglantilari tiklanabilir olur (/oy, /discord, /site). */
    private static final Pattern LINK = Pattern.compile("(?:https?://|www\\.|discord\\.gg/)\\S+");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').extractUrls(LINK).build();
    /** Yazidaki [[esya:iron_ingot]] oyuncunun kendi dilinde esya adina doner (Turkce istemcide "Demir Kulce"). */
    private static final Pattern ITEM_TOKEN = Pattern.compile("\\[\\[esya:([a-z0-9_]+)]]");
    private static final TextReplacementConfig ITEM_NAMES = TextReplacementConfig.builder().match(ITEM_TOKEN)
            .replacement((match, builder) -> {
                Material material = Material.matchMaterial(match.group(1));
                return material == null ? Component.text(match.group(1)) : Component.translatable(material.translationKey());
            }).build();

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
        Component component = LEGACY.deserialize(text);
        return text.contains("[[esya:") ? component.replaceText(ITEM_NAMES) : component;
    }

    /** Mesajlarda esyanin adi yerine kullanilir; {@link #color} cevirir. */
    static String item(Material material) {
        return "[[esya:" + material.getKey().getKey() + "]]";
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

    /** Minecraft ses anahtariyla (orn. "entity.player.levelup") ses calar. */
    static void sound(Player to, String key, float pitch) {
        to.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, 0.8f, pitch));
    }

    void title(Player to, String titlePath, String subtitlePath, Object... pairs) {
        to.showTitle(net.kyori.adventure.title.Title.title(color(raw(titlePath, pairs)), color(raw(subtitlePath, pairs))));
    }
}
