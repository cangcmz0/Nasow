package net.skysurvival.skycore;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Siralama tablolari: en zenginler, en cok oynayanlar, en cok gorev bitirenler, KOTH sampiyonlari ve bu ayin
 * oyculari. /siralama ile sohbette, %skycore_top_para_1% gibi yer tutucularla spawn'daki hologramlarda gorunur.
 * Para ve oynama suresi cevrimici oyunculardan dakikada bir okunur; cevrimdisi oyuncunun son bilinen degeri kalir.
 */
final class LeaderboardModule implements Module, CommandExecutor, TabCompleter {
    static final int SIZE = 10;
    private static final Pattern COLOR = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    /** Tablo adi -> veriler.yml'deki oyuncu anahtari (oy tablosu her ay degisir, bkz. key()). */
    private static final Map<String, String> BOARDS = new LinkedHashMap<>();

    static {
        BOARDS.put("para", "skor.para");
        BOARDS.put("sure", "skor.sure");
        BOARDS.put("gorev", "gorev-toplam");
        BOARDS.put("koth", "koth-galibiyet");
        BOARDS.put("oy", "");
    }

    private final SkyCore plugin;
    /** Tablo -> butun oyuncularin sirali listesi (yer tutucular her seferinde siralamasin diye). */
    private final Map<String, List<DataStore.Score>> cache = new HashMap<>();
    private BukkitTask task;
    private boolean enabled;

    LeaderboardModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("siralama.aktif", true);
        if (!enabled) {
            return;
        }
        long period = Math.max(10, plugin.getConfig().getInt("siralama.yenileme-saniye", 60)) * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 20L, period);
    }

    @Override
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        cache.clear();
    }

    static String month(ZoneId zone) {
        return LocalDate.now(zone).format(MONTH);
    }

    String currentMonth() {
        return month(ZoneId.of(plugin.getConfig().getString("gunluk-odul.saat-dilimi", "Europe/Istanbul")));
    }

    private String key(String board) {
        return board.equals("oy") ? DataStore.voteMonthKey(currentMonth()) : BOARDS.get(board);
    }

    /** Cevrimici oyuncularin parasini ve suresini kaydeder, tablolari yeniden hesaplar. */
    void refresh() {
        plugin.getServer().getOnlinePlayers().forEach(this::sample);
        for (String board : BOARDS.keySet()) {
            refreshBoard(board);
        }
    }

    void sample(Player player) {
        UUID id = player.getUniqueId();
        DataStore data = plugin.data();
        data.setHidden(id, player.hasPermission(plugin.getConfig().getString("siralama.gizli-yetki", "skycore.siralama.gizli")));
        if (plugin.economy().available()) {
            data.setScore(id, player.getName(), "skor.para", Math.max(0, plugin.economy().balance(player)));
        }
        data.setScore(id, player.getName(), "skor.sure", player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (enabled) {
            sample(event.getPlayer());
        }
    }

    private List<DataStore.Score> all(String board) {
        List<DataStore.Score> list = cache.get(board);
        if (list == null && enabled && BOARDS.containsKey(board)) {
            list = refreshBoard(board);
        }
        return list == null ? List.of() : list;
    }

    /** Ilk 10. */
    List<DataStore.Score> top(String board) {
        List<DataStore.Score> list = all(board);
        return list.size() > SIZE ? list.subList(0, SIZE) : list;
    }

    String format(String board, double value) {
        return switch (board) {
            case "para" -> plugin.economy().format(value);
            case "sure" -> plugin.messages().raw("siralama.sure-bicim", "saat", (long) value / 60, "dakika", (long) value % 60);
            default -> (long) value + " " + plugin.messages().raw("siralama.birimler." + board);
        };
    }

    /** Hologram satiri: "#1 Ali » 12.500₺" ya da bos sira. Renkler § ile (DecentHolograms, TAB). */
    String line(String board, int rank) {
        List<DataStore.Score> list = top(board);
        String text = rank >= 1 && rank <= list.size()
                ? plugin.messages().raw("siralama.mesajlar.satir", "sira", rank, "oyuncu", list.get(rank - 1).name(),
                        "deger", format(board, list.get(rank - 1).value()))
                : plugin.messages().raw("siralama.mesajlar.bos-satir", "sira", rank);
        return COLOR.matcher(text).replaceAll("§$1");
    }

    /** Oyuncunun sirasi (1'den baslar); tabloda yoksa 0. Tum oyunculara bakar, ilk 10 ile sinirli degil. */
    int rankOf(UUID id, String board) {
        List<DataStore.Score> list = all(board);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    static boolean isBoard(String board) {
        return BOARDS.containsKey(board);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!enabled) {
            messages.send(sender, "genel-mesajlar.yetki-yok");
            return true;
        }
        if (args.length == 0) {
            messages.sendLines(sender, "siralama.mesajlar.kullanim", "tablolar", String.join(", ", BOARDS.keySet()));
            return true;
        }
        String board = args[0].toLowerCase(Locale.ROOT);
        if (!BOARDS.containsKey(board)) {
            messages.send(sender, "siralama.mesajlar.bilinmeyen", "tablolar", String.join(", ", BOARDS.keySet()));
            return true;
        }
        if (sender instanceof Player player) {
            sample(player);
        }
        refreshBoard(board);
        messages.send(sender, "siralama.mesajlar.baslik", "tablo", messages.raw("siralama.basliklar." + board));
        List<DataStore.Score> list = top(board);
        if (list.isEmpty()) {
            messages.send(sender, "siralama.mesajlar.bos");
        }
        for (int i = 0; i < list.size(); i++) {
            messages.send(sender, "siralama.mesajlar.satir", "sira", i + 1, "oyuncu", list.get(i).name(),
                    "deger", format(board, list.get(i).value()));
        }
        if (sender instanceof Player player) {
            int rank = rankOf(player.getUniqueId(), board);
            if (rank > 0) {
                messages.send(player, "siralama.mesajlar.sen", "sira", rank,
                        "deger", format(board, plugin.data().score(player.getUniqueId(), key(board))));
            } else {
                messages.send(player, "siralama.mesajlar.sen-yok");
            }
        }
        return true;
    }

    private List<DataStore.Score> refreshBoard(String board) {
        List<DataStore.Score> list = plugin.data().top(key(board), Integer.MAX_VALUE);
        cache.put(board, list);
        return list;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String board : BOARDS.keySet()) {
                if (board.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(board);
                }
            }
        }
        return result;
    }
}
