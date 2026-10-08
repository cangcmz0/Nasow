package net.skysurvival.skycore;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.scheduler.BukkitTask;

/** Sohbet oyunlari: kelimeyi ilk yazan, islemi ilk bilen ya da karisik kelimeyi ilk cozen para kazanir. */
final class ChatGameModule implements Module {
    record Game(String type, String question, String answer, long startedAt) {}

    private static final Locale TR = Locale.forLanguageTag("tr");

    private final SkyCore plugin;
    private final AtomicReference<Game> active = new AtomicReference<>();
    private boolean enabled;
    private BukkitTask scheduler;
    private BukkitTask timeout;

    ChatGameModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("sohbet-oyunlari.aktif", true);
        if (enabled) {
            long interval = Math.max(1, plugin.getConfig().getInt("sohbet-oyunlari.aralik-dakika", 10)) * 60L * 20L;
            scheduler = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> startGame(false), interval, interval);
        }
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.cancel();
            scheduler = null;
        }
        cancelTimeout();
        active.set(null);
    }

    /** Suren oyunun cevabi; oyun yoksa null. */
    String currentAnswer() {
        Game game = active.get();
        return game == null ? null : game.answer();
    }

    private void cancelTimeout() {
        if (timeout != null) {
            timeout.cancel();
            timeout = null;
        }
    }

    /** Yeni oyun baslatir. force: oyuncu sayisi kontrolunu atla (/skycore oyun). */
    boolean startGame(boolean force) {
        if (active.get() != null) {
            return false;
        }
        int minPlayers = plugin.getConfig().getInt("sohbet-oyunlari.min-oyuncu", 2);
        if (!force && plugin.getServer().getOnlinePlayers().size() < minPlayers) {
            return false;
        }
        Game game = createGame();
        if (game == null) {
            return false;
        }
        active.set(game);
        int seconds = Math.max(10, plugin.getConfig().getInt("sohbet-oyunlari.sure-saniye", 60));
        double reward = plugin.getConfig().getDouble("sohbet-oyunlari.odul", 250);
        plugin.messages().broadcastText(" ");
        plugin.messages().broadcast("sohbet-oyunlari.mesajlar." + game.type(), "soru", game.question());
        plugin.messages().broadcast("sohbet-oyunlari.mesajlar.odul-bilgi", "odul", plugin.economy().format(reward), "sure", seconds);
        plugin.messages().broadcastText(" ");
        cancelTimeout();
        timeout = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (active.compareAndSet(game, null)) {
                plugin.messages().broadcast("sohbet-oyunlari.mesajlar.bilen-yok", "cevap", game.answer());
            }
        }, seconds * 20L);
        return true;
    }

    private Game createGame() {
        List<String> types = new ArrayList<>(plugin.getConfig().getStringList("sohbet-oyunlari.turler"));
        List<String> words = plugin.getConfig().getStringList("sohbet-oyunlari.kelimeler");
        if (words.isEmpty()) {
            types.remove("yaz");
            types.remove("karisik");
        }
        types.removeIf(type -> !type.equals("yaz") && !type.equals("matematik") && !type.equals("karisik"));
        if (types.isEmpty()) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String type = types.get(random.nextInt(types.size()));
        long now = System.currentTimeMillis();
        switch (type) {
            case "matematik" -> {
                int op = random.nextInt(3);
                int a;
                int b;
                int result;
                String symbol;
                if (op == 0) {
                    a = random.nextInt(10, 100);
                    b = random.nextInt(10, 100);
                    result = a + b;
                    symbol = "+";
                } else if (op == 1) {
                    a = random.nextInt(30, 150);
                    b = random.nextInt(5, a);
                    result = a - b;
                    symbol = "-";
                } else {
                    a = random.nextInt(3, 13);
                    b = random.nextInt(3, 21);
                    result = a * b;
                    symbol = "×";
                }
                return new Game(type, a + " " + symbol + " " + b, String.valueOf(result), now);
            }
            case "karisik" -> {
                String word = words.get(random.nextInt(words.size())).toLowerCase(TR);
                return new Game(type, scramble(word), word, now);
            }
            default -> {
                String word = words.get(random.nextInt(words.size())).toLowerCase(TR);
                return new Game(type, word, word, now);
            }
        }
    }

    private static String scramble(String word) {
        List<Character> letters = new ArrayList<>();
        for (char c : word.toCharArray()) {
            letters.add(c);
        }
        String result = word;
        for (int attempt = 0; attempt < 10 && result.equals(word) && word.length() > 1; attempt++) {
            Collections.shuffle(letters);
            StringBuilder sb = new StringBuilder();
            letters.forEach(sb::append);
            result = sb.toString();
        }
        return result;
    }

    /** Buyuk/kucuk harf ve Turkce karakter farkini yok sayar (kılıç = kilic = KILIÇ). */
    static String normalize(String text) {
        String lower = text.trim().toLowerCase(TR);
        StringBuilder sb = new StringBuilder(lower.length());
        for (char c : lower.toCharArray()) {
            sb.append(switch (c) {
                case 'ç' -> 'c';
                case 'ğ' -> 'g';
                case 'ı' -> 'i';
                case 'ö' -> 'o';
                case 'ş' -> 's';
                case 'ü' -> 'u';
                default -> c;
            });
        }
        return sb.toString();
    }

    // MONITOR + ignoreCancelled: AuthMe'nin ya da susturmanin engelledigi mesajlar sayilmaz.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Game game = active.get();
        if (game == null) {
            return;
        }
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!normalize(message).equals(normalize(game.answer())) || !active.compareAndSet(game, null)) {
            return;
        }
        Player player = event.getPlayer();
        // Sohbet olayi ayri bir is parcaciginda gelir; para islemleri ana is parcaciginda yapilir.
        plugin.getServer().getScheduler().runTask(plugin, () -> win(player, game));
    }

    private void win(Player player, Game game) {
        cancelTimeout();
        double reward = plugin.getConfig().getDouble("sohbet-oyunlari.odul", 250);
        if (reward > 0) {
            plugin.economy().deposit(player, reward);
        }
        double seconds = (System.currentTimeMillis() - game.startedAt()) / 1000.0;
        plugin.messages().broadcast("sohbet-oyunlari.mesajlar.kazandi",
                "oyuncu", player.getName(),
                "saniye", String.format(Locale.ROOT, "%.1f", seconds),
                "cevap", game.answer(),
                "odul", plugin.economy().format(reward));
    }
}
