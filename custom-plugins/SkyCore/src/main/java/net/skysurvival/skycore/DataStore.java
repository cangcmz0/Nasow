package net.skysurvival.skycore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Oyuncu verileri ve kelle odulleri: plugins/SkyCore/veriler.yml */
final class DataStore {
    record Bounty(UUID id, String name, double amount) {}

    record Origin(String world, int x, int y, int z) {}

    private final SkyCore plugin;
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();
    private boolean dirty;

    DataStore(SkyCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "veriler.yml");
    }

    void load() {
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    void save() {
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("veriler.yml kaydedilemedi: " + e.getMessage());
        }
    }

    void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    private String player(UUID id, String key) {
        return "oyuncular." + id + "." + key;
    }

    private void set(String path, Object value) {
        yaml.set(path, value);
        dirty = true;
    }

    // ---- Gunluk odul ----

    String lastDaily(UUID id) {
        return yaml.getString(player(id, "gunluk-son"));
    }

    int dailyStreak(UUID id) {
        return yaml.getInt(player(id, "gunluk-seri"), 0);
    }

    void setDaily(UUID id, String name, String date, int streak) {
        set(player(id, "isim"), name);
        set(player(id, "gunluk-son"), date);
        set(player(id, "gunluk-seri"), streak);
    }

    /** Bugun bu IP'den gunluk odul alan hesaplar (alt hesap siniri icin). */
    List<String> dailyClaimsFromIp(String ip, String date) {
        return yaml.getStringList("ip-gunluk." + ipKey(ip) + "." + date);
    }

    void addDailyClaimFromIp(String ip, String date, UUID id) {
        String key = ipKey(ip);
        List<String> claims = new ArrayList<>(dailyClaimsFromIp(ip, date));
        claims.add(id.toString());
        set("ip-gunluk." + key, null); // eski gunleri temizle
        set("ip-gunluk." + key + "." + date, claims);
    }

    private static String ipKey(String ip) {
        return ip.replace('.', '_').replace(':', '_');
    }

    // ---- Aktiflik ----

    int activeMinutes(UUID id) {
        return yaml.getInt(player(id, "aktif-dakika"), 0);
    }

    void setActiveMinutes(UUID id, int minutes) {
        set(player(id, "aktif-dakika"), minutes);
    }

    // ---- Spawn adasi (kurulan yer) ----

    Origin islandOrigin() {
        return origin("spawn-adasi");
    }

    void setIslandOrigin(Origin origin) {
        setOrigin("spawn-adasi", origin);
    }

    /** Kurulan yapinin (spawn-adasi, koth-arenasi) yeri; kurulmadiysa null. */
    Origin origin(String key) {
        String world = yaml.getString(key + ".dunya");
        if (world == null) {
            return null;
        }
        return new Origin(world, yaml.getInt(key + ".x"), yaml.getInt(key + ".y"), yaml.getInt(key + ".z"));
    }

    void setOrigin(String key, Origin origin) {
        set(key + ".dunya", origin.world());
        set(key + ".x", origin.x());
        set(key + ".y", origin.y());
        set(key + ".z", origin.z());
    }

    // ---- Gunluk gorevler ----

    String questDate(UUID id) {
        return yaml.getString(player(id, "gorev.tarih"));
    }

    List<String> quests(UUID id) {
        return yaml.getStringList(player(id, "gorev.liste"));
    }

    void setQuests(UUID id, String name, String date, List<String> quests) {
        set(player(id, "isim"), name);
        set(player(id, "gorev"), null);
        set(player(id, "gorev.tarih"), date);
        set(player(id, "gorev.liste"), quests);
    }

    int questProgress(UUID id, String quest) {
        return yaml.getInt(player(id, "gorev.ilerleme." + quest), 0);
    }

    void setQuestProgress(UUID id, String quest, int value) {
        set(player(id, "gorev.ilerleme." + quest), value);
    }

    boolean questBonus(UUID id) {
        return yaml.getBoolean(player(id, "gorev.bonus"), false);
    }

    void setQuestBonus(UUID id) {
        set(player(id, "gorev.bonus"), true);
    }

    int questsDone(UUID id) {
        return yaml.getInt(player(id, "gorev-toplam"), 0);
    }

    void addQuestDone(UUID id) {
        set(player(id, "gorev-toplam"), questsDone(id) + 1);
    }

    // ---- Yeni oyuncu korumasi ----

    boolean protectionOff(UUID id) {
        return yaml.getBoolean(player(id, "koruma-kapali"), false);
    }

    void setProtectionOff(UUID id) {
        set(player(id, "koruma-kapali"), true);
    }

    // ---- KOTH ----

    int kothWins(UUID id) {
        return yaml.getInt(player(id, "koth-galibiyet"), 0);
    }

    void addKothWin(UUID id, String name) {
        set(player(id, "isim"), name);
        set(player(id, "koth-galibiyet"), kothWins(id) + 1);
    }

    String lastKothWinner() {
        return yaml.getString("koth.son-kazanan", "");
    }

    void setLastKothWinner(String name) {
        set("koth.son-kazanan", name);
    }

    // ---- Siralama ----

    String name(UUID id) {
        return yaml.getString(player(id, "isim"));
    }

    /** Sayisal bir oyuncu degeri (skor.para, gorev-toplam, oy.toplam...). */
    double score(UUID id, String key) {
        return yaml.getDouble(player(id, key), 0);
    }

    /** Degisen degeri yazar (her dakika ayni degeri yazip dosyayi kirletmesin). */
    void setScore(UUID id, String name, String key, double value) {
        if (!name.equals(name(id))) {
            set(player(id, "isim"), name);
        }
        if (yaml.getDouble(player(id, key), Double.NaN) != value) {
            set(player(id, key), value);
        }
    }

    /** Yetkililer gibi siralamada gorunmeyecek oyuncular. */
    void setHidden(UUID id, boolean hidden) {
        if (hidden != yaml.getBoolean(player(id, "siralama-gizli"), false)) {
            set(player(id, "siralama-gizli"), hidden ? true : null);
        }
    }

    /** key degeri en yuksek oyuncular (0 olanlar ve gizliler haric). */
    List<Score> top(String key, int limit) {
        List<Score> list = new ArrayList<>();
        ConfigurationSection players = yaml.getConfigurationSection("oyuncular");
        if (players != null) {
            for (String id : players.getKeys(false)) {
                ConfigurationSection section = players.getConfigurationSection(id);
                if (section == null || section.getBoolean("siralama-gizli")) {
                    continue;
                }
                double value = section.getDouble(key, 0);
                if (value > 0) {
                    try {
                        list.add(new Score(UUID.fromString(id), section.getString("isim", "?"), value));
                    } catch (IllegalArgumentException ignored) {
                        // bozuk kayit
                    }
                }
            }
        }
        list.sort(Comparator.comparingDouble(Score::value).reversed().thenComparing(Score::name, String.CASE_INSENSITIVE_ORDER));
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }

    record Score(UUID id, String name, double value) {}

    /** Ismi (buyuk/kucuk harf fark etmeden) bilinen oyuncu; yoksa null. */
    UUID findByName(String name) {
        ConfigurationSection players = yaml.getConfigurationSection("oyuncular");
        if (players != null) {
            for (String id : players.getKeys(false)) {
                if (name.equalsIgnoreCase(players.getString(id + ".isim"))) {
                    try {
                        return UUID.fromString(id);
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    // ---- Oy verme ----

    static String voteMonthKey(String month) {
        return "oy.ay." + month;
    }

    private static String siteKey(String site) {
        return site.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "_");
    }

    void addVote(UUID id, String name, String month) {
        set(player(id, "isim"), name);
        set(player(id, voteMonthKey(month)), yaml.getInt(player(id, voteMonthKey(month)), 0) + 1);
        set(player(id, "oy.toplam"), yaml.getInt(player(id, "oy.toplam"), 0) + 1);
    }

    int votes(UUID id, String month) {
        return yaml.getInt(player(id, voteMonthKey(month)), 0);
    }

    int totalVotes(UUID id) {
        return yaml.getInt(player(id, "oy.toplam"), 0);
    }

    long lastVote(UUID id, String site) {
        return yaml.getLong(player(id, "oy.son." + siteKey(site)), 0);
    }

    long lastVoteAny(UUID id) {
        return yaml.getLong(player(id, "oy.son-zaman"), 0);
    }

    void setLastVote(UUID id, String site, long time) {
        set(player(id, "oy.son." + siteKey(site)), time);
        set(player(id, "oy.son-zaman"), time);
    }

    int pendingVotes(UUID id) {
        return yaml.getInt(player(id, "oy.bekleyen"), 0);
    }

    void setPendingVotes(UUID id, int count) {
        set(player(id, "oy.bekleyen"), count > 0 ? count : null);
    }

    /** Bugun bu IP'den bu sitede oyu odullendirilen hesaplar (alt hesaplara oy verme siniri). */
    List<String> voteClaimsFromIp(String ip, String site, String date) {
        return yaml.getStringList("ip-oy." + ipKey(ip) + "." + siteKey(site) + "." + date);
    }

    void addVoteClaimFromIp(String ip, String site, String date, UUID id) {
        String key = "ip-oy." + ipKey(ip) + "." + siteKey(site);
        List<String> claims = new ArrayList<>(voteClaimsFromIp(ip, site, date));
        claims.add(id.toString());
        set(key, null); // eski gunleri temizle
        set(key + "." + date, claims);
    }

    int votePartyCount() {
        return yaml.getInt("oy-partisi.sayac", 0);
    }

    void setVotePartyCount(int count) {
        set("oy-partisi.sayac", count);
    }

    // ---- Kelle avi ----

    double bounty(UUID id) {
        return yaml.getDouble("kelleler." + id + ".miktar", 0);
    }

    void setBounty(UUID id, String name, double amount) {
        set("kelleler." + id + ".isim", name);
        set("kelleler." + id + ".miktar", amount);
    }

    void removeBounty(UUID id) {
        set("kelleler." + id, null);
    }

    List<Bounty> topBounties(int limit) {
        List<Bounty> list = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("kelleler");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    list.add(new Bounty(UUID.fromString(key), section.getString(key + ".isim", "?"), section.getDouble(key + ".miktar")));
                } catch (IllegalArgumentException ignored) {
                    // bozuk kayit
                }
            }
        }
        list.sort(Comparator.comparingDouble(Bounty::amount).reversed());
        return list.size() > limit ? list.subList(0, limit) : list;
    }
}
