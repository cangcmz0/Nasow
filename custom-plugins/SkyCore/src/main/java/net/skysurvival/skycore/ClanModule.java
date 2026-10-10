package net.skysurvival.skycore;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Klanlar: /klan kur, davet, kabul, ayril, at, devret, sil, bilgi, liste, ev, evayarla, banka, sohbet.
 * Klan arkadaslari birbirine vuramaz. Veriler plugins/SkyCore/klanlar.yml dosyasinda.
 */
final class ClanModule implements Module, CommandExecutor, TabCompleter {
    static final class Clan {
        String name;
        UUID leader;
        final Map<UUID, String> members = new LinkedHashMap<>();
        String created;
        double bank;
        Location home;

        String key() {
            return name.toLowerCase(Locale.ROOT);
        }
    }

    private static final List<String> SUBCOMMANDS = List.of("kur", "davet", "kabul", "reddet", "ayril", "at", "devret",
            "sil", "bilgi", "liste", "ev", "evayarla", "banka", "sohbet");
    private static final Pattern NAME = Pattern.compile("[\\p{L}0-9]+");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SkyCore plugin;
    private final File file;
    private final Map<String, Clan> clans = new LinkedHashMap<>();
    private final Map<UUID, Clan> byMember = new HashMap<>();
    /** Davet edilen oyuncu -> (klan anahtari -> bitis zamani) */
    private final Map<UUID, Map<String, Long>> invites = new HashMap<>();
    private final Map<UUID, BukkitTask> homeTeleports = new HashMap<>();
    private boolean enabled;
    private boolean dirty;
    private BukkitTask saveTask;

    ClanModule(SkyCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "klanlar.yml");
        load();
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("klan.aktif", true);
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, 20L * 60, 20L * 60);
    }

    @Override
    public void stop() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        homeTeleports.values().forEach(BukkitTask::cancel);
        homeTeleports.clear();
        saveIfDirty();
    }

    // ---- Kayit ----

    private void load() {
        clans.clear();
        byMember.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("klanlar");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            try {
                Clan clan = new Clan();
                clan.name = section.getString("isim", key);
                clan.leader = UUID.fromString(section.getString("lider", ""));
                clan.created = section.getString("kurulus", "");
                clan.bank = section.getDouble("banka", 0);
                ConfigurationSection members = section.getConfigurationSection("uyeler");
                if (members != null) {
                    for (String id : members.getKeys(false)) {
                        clan.members.put(UUID.fromString(id), members.getString(id, "?"));
                    }
                }
                clan.home = section.getLocation("ev");
                if (!clan.members.containsKey(clan.leader)) {
                    clan.members.put(clan.leader, "?");
                }
                register(clan);
            } catch (IllegalArgumentException | NullPointerException e) {
                plugin.getLogger().warning("klanlar.yml: bozuk klan atlandi: " + key);
            }
        }
    }

    void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Clan clan : clans.values()) {
            String path = "klanlar." + clan.key();
            yaml.set(path + ".isim", clan.name);
            yaml.set(path + ".lider", clan.leader.toString());
            yaml.set(path + ".kurulus", clan.created);
            yaml.set(path + ".banka", clan.bank);
            for (Map.Entry<UUID, String> member : clan.members.entrySet()) {
                yaml.set(path + ".uyeler." + member.getKey(), member.getValue());
            }
            yaml.set(path + ".ev", clan.home);
        }
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("klanlar.yml kaydedilemedi: " + e.getMessage());
        }
    }

    private void register(Clan clan) {
        clans.put(clan.key(), clan);
        for (UUID member : clan.members.keySet()) {
            byMember.put(member, clan);
        }
    }

    private void changed() {
        dirty = true;
    }

    // ---- Diger modullerin kullandigi bilgiler ----

    Clan clanOf(UUID player) {
        return byMember.get(player);
    }

    String clanName(UUID player) {
        Clan clan = byMember.get(player);
        return clan == null ? null : clan.name;
    }

    String tag(UUID player) {
        Clan clan = byMember.get(player);
        return clan == null ? "" : plugin.messages().raw("klan.etiket", "klan", clan.name);
    }

    boolean sameClan(UUID a, UUID b) {
        Clan clan = byMember.get(a);
        return clan != null && clan == byMember.get(b);
    }

    // ---- Dost atesi ----

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled || plugin.getConfig().getBoolean("klan.dost-atesi", false) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = CombatModule.attacker(event.getDamager());
        if (attacker != null && !attacker.equals(victim) && friendlyFireBlocked(attacker, victim)) {
            event.setCancelled(true);
            plugin.messages().actionBar(attacker, "klan.mesajlar.dost-atesi");
        }
    }

    boolean friendlyFireBlocked(Player attacker, Player victim) {
        return enabled && !plugin.getConfig().getBoolean("klan.dost-atesi", false)
                && sameClan(attacker.getUniqueId(), victim.getUniqueId());
    }

    // ---- Komut ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "genel-mesajlar.sadece-oyuncu");
            return true;
        }
        if (!enabled) {
            messages.send(player, "genel-mesajlar.yetki-yok");
            return true;
        }
        if (command.getName().equals("klansohbet")) {
            chat(player, String.join(" ", args));
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String arg = args.length > 1 ? args[1] : null;
        switch (sub) {
            case "kur" -> create(player, arg);
            case "davet" -> invite(player, arg);
            case "kabul" -> accept(player, arg);
            case "reddet" -> {
                invites.remove(player.getUniqueId());
                messages.send(player, "klan.mesajlar.reddedildi");
            }
            case "ayril" -> leave(player);
            case "at" -> kick(player, arg);
            case "devret" -> transfer(player, arg);
            case "sil" -> disband(player, arg);
            case "bilgi" -> info(player, arg);
            case "liste" -> list(player);
            case "ev" -> home(player);
            case "evayarla" -> setHome(player);
            case "banka" -> bank(player, arg, args.length > 2 ? args[2] : null);
            case "sohbet" -> chat(player, args.length > 1 ? String.join(" ", List.of(args).subList(1, args.length)) : "");
            default -> messages.sendLines(player, "klan.mesajlar.yardim");
        }
        return true;
    }

    private Clan requireClan(Player player) {
        Clan clan = byMember.get(player.getUniqueId());
        if (clan == null) {
            plugin.messages().send(player, "klan.mesajlar.klanin-yok");
        }
        return clan;
    }

    private Clan requireLeader(Player player) {
        Clan clan = requireClan(player);
        if (clan != null && !clan.leader.equals(player.getUniqueId())) {
            plugin.messages().send(player, "klan.mesajlar.lider-degil");
            return null;
        }
        return clan;
    }

    /** Klan adi kurallara uyuyorsa null, uymuyorsa hata mesajinin yolu. */
    String validateName(String name) {
        int min = plugin.getConfig().getInt("klan.isim-min", 3);
        int max = plugin.getConfig().getInt("klan.isim-max", 12);
        if (name == null || name.length() < min || name.length() > max || !NAME.matcher(name).matches()) {
            return "klan.mesajlar.isim-gecersiz";
        }
        if (clans.containsKey(name.toLowerCase(Locale.ROOT))) {
            return "klan.mesajlar.isim-alinmis";
        }
        return null;
    }

    private void create(Player player, String name) {
        Messages messages = plugin.messages();
        if (byMember.containsKey(player.getUniqueId())) {
            messages.send(player, "klan.mesajlar.zaten-klanda");
            return;
        }
        if (name == null) {
            messages.send(player, "klan.mesajlar.kur-kullanim");
            return;
        }
        String error = validateName(name);
        if (error != null) {
            messages.send(player, error, "min", plugin.getConfig().getInt("klan.isim-min", 3),
                    "max", plugin.getConfig().getInt("klan.isim-max", 12));
            return;
        }
        double cost = plugin.getConfig().getDouble("klan.kurma-ucreti", 5000);
        if (cost > 0 && !plugin.economy().withdraw(player, cost)) {
            messages.send(player, "klan.mesajlar.para-yetersiz", "ucret", plugin.economy().format(cost));
            return;
        }
        Clan clan = new Clan();
        clan.name = name;
        clan.leader = player.getUniqueId();
        clan.members.put(player.getUniqueId(), player.getName());
        clan.created = LocalDate.now().format(DATE);
        register(clan);
        changed();
        messages.broadcast("klan.mesajlar.kuruldu", "oyuncu", player.getName(), "klan", clan.name);
    }

    private void invite(Player player, String targetName) {
        Messages messages = plugin.messages();
        Clan clan = requireLeader(player);
        if (clan == null) {
            return;
        }
        Player target = targetName == null ? null : plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            messages.send(player, "klan.mesajlar.oyuncu-yok");
            return;
        }
        if (byMember.containsKey(target.getUniqueId())) {
            messages.send(player, "klan.mesajlar.hedef-klanda", "oyuncu", target.getName());
            return;
        }
        if (clan.members.size() >= plugin.getConfig().getInt("klan.max-uye", 10)) {
            messages.send(player, "klan.mesajlar.klan-dolu");
            return;
        }
        invites.computeIfAbsent(target.getUniqueId(), id -> new LinkedHashMap<>())
                .put(clan.key(), System.currentTimeMillis() + 120_000);
        messages.send(player, "klan.mesajlar.davet-gonderildi", "oyuncu", target.getName());
        messages.send(target, "klan.mesajlar.davet-geldi", "oyuncu", player.getName(), "klan", clan.name);
    }

    private void accept(Player player, String clanName) {
        Messages messages = plugin.messages();
        if (byMember.containsKey(player.getUniqueId())) {
            messages.send(player, "klan.mesajlar.zaten-klanda");
            return;
        }
        Map<String, Long> mine = invites.getOrDefault(player.getUniqueId(), Map.of());
        long now = System.currentTimeMillis();
        String key = null;
        if (clanName != null) {
            Long until = mine.get(clanName.toLowerCase(Locale.ROOT));
            key = until != null && until > now ? clanName.toLowerCase(Locale.ROOT) : null;
        } else {
            for (Map.Entry<String, Long> entry : mine.entrySet()) {
                if (entry.getValue() > now) {
                    key = entry.getKey(); // en son gelen gecerli davet
                }
            }
        }
        Clan clan = key == null ? null : clans.get(key);
        if (clan == null) {
            messages.send(player, "klan.mesajlar.davet-yok");
            return;
        }
        if (clan.members.size() >= plugin.getConfig().getInt("klan.max-uye", 10)) {
            messages.send(player, "klan.mesajlar.klan-dolu");
            return;
        }
        invites.remove(player.getUniqueId());
        clan.members.put(player.getUniqueId(), player.getName());
        byMember.put(player.getUniqueId(), clan);
        changed();
        tell(clan, "klan.mesajlar.katildi", "oyuncu", player.getName(), "klan", clan.name);
    }

    private void leave(Player player) {
        Clan clan = requireClan(player);
        if (clan == null) {
            return;
        }
        if (clan.leader.equals(player.getUniqueId())) {
            plugin.messages().send(player, "klan.mesajlar.lider-ayrilamaz");
            return;
        }
        clan.members.remove(player.getUniqueId());
        byMember.remove(player.getUniqueId());
        changed();
        plugin.messages().send(player, "klan.mesajlar.ayrildin", "klan", clan.name);
        tell(clan, "klan.mesajlar.ayrildi", "oyuncu", player.getName());
    }

    private UUID memberByName(Clan clan, String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<UUID, String> member : clan.members.entrySet()) {
            if (member.getValue().equalsIgnoreCase(name)) {
                return member.getKey();
            }
        }
        return null;
    }

    private void kick(Player player, String name) {
        Clan clan = requireLeader(player);
        if (clan == null) {
            return;
        }
        UUID target = memberByName(clan, name);
        if (target == null || target.equals(player.getUniqueId())) {
            plugin.messages().send(player, "klan.mesajlar.uye-yok");
            return;
        }
        String targetName = clan.members.remove(target);
        byMember.remove(target);
        changed();
        tell(clan, "klan.mesajlar.atildi", "oyuncu", targetName);
        Player online = plugin.getServer().getPlayer(target);
        if (online != null) {
            plugin.messages().send(online, "klan.mesajlar.atildin", "klan", clan.name);
        }
    }

    private void transfer(Player player, String name) {
        Clan clan = requireLeader(player);
        if (clan == null) {
            return;
        }
        UUID target = memberByName(clan, name);
        if (target == null || target.equals(player.getUniqueId())) {
            plugin.messages().send(player, "klan.mesajlar.uye-yok");
            return;
        }
        clan.leader = target;
        changed();
        tell(clan, "klan.mesajlar.devredildi", "oyuncu", clan.members.get(target));
    }

    private void disband(Player player, String confirm) {
        Clan clan = requireLeader(player);
        if (clan == null) {
            return;
        }
        if (!"onayla".equalsIgnoreCase(confirm)) {
            plugin.messages().send(player, "klan.mesajlar.sil-onay", "banka", plugin.economy().format(clan.bank));
            return;
        }
        if (clan.bank > 0) {
            plugin.economy().deposit(player, clan.bank);
        }
        tell(clan, "klan.mesajlar.silindi", "klan", clan.name);
        clans.remove(clan.key());
        clan.members.keySet().forEach(byMember::remove);
        changed();
    }

    private void info(Player player, String name) {
        Clan clan = name == null ? requireClan(player) : clans.get(name.toLowerCase(Locale.ROOT));
        if (clan == null) {
            if (name != null) {
                plugin.messages().send(player, "klan.mesajlar.klan-yok");
            }
            return;
        }
        List<String> members = new ArrayList<>();
        for (Map.Entry<UUID, String> member : clan.members.entrySet()) {
            boolean online = plugin.getServer().getPlayer(member.getKey()) != null;
            members.add((online ? "&a" : "&7") + member.getValue());
        }
        plugin.messages().sendLines(player, "klan.mesajlar.bilgi", "klan", clan.name,
                "lider", clan.members.getOrDefault(clan.leader, "?"), "uye", clan.members.size(),
                "max", plugin.getConfig().getInt("klan.max-uye", 10), "uyeler", String.join("&8, ", members),
                "banka", plugin.economy().format(clan.bank), "kurulus", clan.created);
    }

    private void list(Player player) {
        List<Clan> sorted = new ArrayList<>(clans.values());
        sorted.sort(Comparator.comparingInt((Clan c) -> c.members.size()).thenComparingDouble(c -> c.bank).reversed());
        Messages messages = plugin.messages();
        if (sorted.isEmpty()) {
            messages.send(player, "klan.mesajlar.liste-bos");
            return;
        }
        messages.sendLines(player, "klan.mesajlar.liste-baslik");
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Clan clan = sorted.get(i);
            player.sendMessage(Messages.color(messages.raw("klan.mesajlar.liste-satir", "sira", i + 1, "klan", clan.name,
                    "uye", clan.members.size(), "banka", plugin.economy().format(clan.bank))));
        }
    }

    private void setHome(Player player) {
        Clan clan = requireLeader(player);
        if (clan == null) {
            return;
        }
        clan.home = player.getLocation();
        changed();
        tell(clan, "klan.mesajlar.ev-ayarlandi", "oyuncu", player.getName());
    }

    private void home(Player player) {
        Messages messages = plugin.messages();
        Clan clan = requireClan(player);
        if (clan == null) {
            return;
        }
        if (clan.home == null || clan.home.getWorld() == null) {
            messages.send(player, "klan.mesajlar.ev-yok");
            return;
        }
        if (plugin.combat().isTagged(player)) {
            messages.send(player, "vahsi-doga.mesajlar.savasta");
            return;
        }
        int delay = Math.max(0, plugin.getConfig().getInt("klan.ev-bekleme-saniye", 3));
        BukkitTask previous = homeTeleports.remove(player.getUniqueId());
        if (previous != null) {
            previous.cancel();
        }
        Location home = clan.home.clone();
        if (delay > 0) {
            messages.send(player, "klan.mesajlar.ev-bekle", "sure", delay);
        }
        homeTeleports.put(player.getUniqueId(), plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            homeTeleports.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            if (plugin.combat().isTagged(player)) {
                messages.send(player, "vahsi-doga.mesajlar.savasta"); // beklerken savasa girdi
                return;
            }
            player.teleport(home);
            messages.send(player, "klan.mesajlar.ev-isinlandi");
        }, delay * 20L));
    }

    /** Isinlanma beklerken yuruyen oyuncunun isinlanmasi iptal olur. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (homeTeleports.isEmpty()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        BukkitTask task = homeTeleports.remove(event.getPlayer().getUniqueId());
        if (task != null) {
            task.cancel();
            plugin.messages().send(event.getPlayer(), "klan.mesajlar.ev-iptal");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BukkitTask task = homeTeleports.remove(event.getPlayer().getUniqueId());
        if (task != null) {
            task.cancel();
        }
        Clan clan = byMember.get(event.getPlayer().getUniqueId());
        if (clan != null && !event.getPlayer().getName().equals(clan.members.get(event.getPlayer().getUniqueId()))) {
            clan.members.put(event.getPlayer().getUniqueId(), event.getPlayer().getName()); // isim degistiyse
            changed();
        }
    }

    private void bank(Player player, String action, String amountText) {
        Messages messages = plugin.messages();
        Clan clan = requireClan(player);
        if (clan == null) {
            return;
        }
        if (action == null) {
            messages.send(player, "klan.mesajlar.banka", "banka", plugin.economy().format(clan.bank));
            return;
        }
        double amount = amountText == null ? -1 : BanknoteModule.parseAmount(amountText);
        if (amount <= 0) {
            messages.send(player, "klan.mesajlar.banka-kullanim");
            return;
        }
        switch (action.toLowerCase(Locale.ROOT)) {
            case "yatir" -> {
                if (!plugin.economy().withdraw(player, amount)) {
                    messages.send(player, "kelle-avi.mesajlar.yetersiz");
                    return;
                }
                clan.bank += amount;
                changed();
                tell(clan, "klan.mesajlar.banka-yatirdi", "oyuncu", player.getName(), "miktar", plugin.economy().format(amount),
                        "banka", plugin.economy().format(clan.bank));
            }
            case "cek" -> {
                if (!clan.leader.equals(player.getUniqueId())) {
                    messages.send(player, "klan.mesajlar.lider-degil");
                    return;
                }
                if (amount > clan.bank) {
                    messages.send(player, "klan.mesajlar.banka-yetersiz");
                    return;
                }
                if (!plugin.economy().deposit(player, amount)) {
                    messages.send(player, "genel-mesajlar.ekonomi-yok");
                    return;
                }
                clan.bank -= amount;
                changed();
                tell(clan, "klan.mesajlar.banka-cekti", "oyuncu", player.getName(), "miktar", plugin.economy().format(amount),
                        "banka", plugin.economy().format(clan.bank));
            }
            default -> messages.send(player, "klan.mesajlar.banka-kullanim");
        }
    }

    private void chat(Player player, String message) {
        Clan clan = requireClan(player);
        if (clan == null) {
            return;
        }
        if (message.isBlank()) {
            plugin.messages().send(player, "klan.mesajlar.sohbet-kullanim");
            return;
        }
        // Oyuncu yazisindaki renk kodlari islenmez
        String line = plugin.messages().raw("klan.mesajlar.sohbet", "klan", clan.name, "oyuncu", player.getName());
        net.kyori.adventure.text.Component text = Messages.color(line).append(net.kyori.adventure.text.Component.text(message,
                net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        for (UUID member : clan.members.keySet()) {
            Player online = plugin.getServer().getPlayer(member);
            if (online != null) {
                online.sendMessage(text);
            }
        }
        plugin.getLogger().info("[Klan " + clan.name + "] " + player.getName() + ": " + message);
    }

    private void tell(Clan clan, String path, Object... pairs) {
        for (UUID member : clan.members.keySet()) {
            Player online = plugin.getServer().getPlayer(member);
            if (online != null) {
                plugin.messages().send(online, path, pairs);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (command.getName().equals("klansohbet")) {
            return result;
        }
        if (args.length == 1) {
            for (String option : SUBCOMMANDS) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(option);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("davet")) {
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        result.add(online.getName());
                    }
                }
            } else if ((sub.equals("at") || sub.equals("devret")) && sender instanceof Player player && clanOf(player.getUniqueId()) != null) {
                for (String name : clanOf(player.getUniqueId()).members.values()) {
                    if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        result.add(name);
                    }
                }
            } else if (sub.equals("bilgi")) {
                for (Clan clan : clans.values()) {
                    if (clan.key().startsWith(prefix)) {
                        result.add(clan.name);
                    }
                }
            } else if (sub.equals("banka")) {
                result.addAll(List.of("yatir", "cek"));
            } else if (sub.equals("sil")) {
                result.add("onayla");
            }
        }
        return result;
    }

    /** Testler icin. */
    int clanCount() {
        return clans.size();
    }
}
