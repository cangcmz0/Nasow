package net.skysurvival.skycore;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.Plugin;

/**
 * Oy verme odulleri. Sunucu listesi siteleri NuVotifier uzerinden oy bildirir; oy veren oyuncu para ve kasa
 * anahtari kazanir, cevrimdisiysa odul girince verilir. Her X oyda bir herkese "oy partisi" odulu.
 * NuVotifier'a yansima ile baglanir; NuVotifier yoksa sadece /oy site listesi calisir.
 */
final class VoteModule implements Module, CommandExecutor, TabCompleter {
    static final String VOTIFIER_EVENT = "com.vexsoftware.votifier.model.VotifierEvent";

    private final SkyCore plugin;
    private boolean enabled;
    private boolean hooked;

    VoteModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("oy.aktif", true);
        if (enabled && !hooked) {
            hook();
        }
    }

    @Override
    public void stop() {
    }

    boolean hooked() {
        return hooked;
    }

    /** NuVotifier'in VotifierEvent olayini dinler (eklentinin kendi sinif yukleyicisiyle). */
    private void hook() {
        Plugin votifier = plugin.getServer().getPluginManager().getPlugin("Votifier");
        ClassLoader loader = votifier != null ? votifier.getClass().getClassLoader() : getClass().getClassLoader();
        Class<? extends Event> type;
        try {
            type = Class.forName(VOTIFIER_EVENT, true, loader).asSubclass(Event.class);
        } catch (ClassNotFoundException | ClassCastException e) {
            if (votifier != null) {
                plugin.getLogger().warning("NuVotifier bulundu ama oy olayi okunamadi: " + e);
            }
            return;
        }
        plugin.getServer().getPluginManager().registerEvent(type, this, EventPriority.NORMAL, (listener, event) -> {
            if (type.isInstance(event)) {
                onVotifierEvent(event);
            }
        }, plugin);
        hooked = true;
        plugin.getLogger().info("NuVotifier'a baglanildi: oy odulleri acik.");
    }

    private void onVotifierEvent(Event event) {
        String username;
        String service;
        String address;
        try {
            Object vote = event.getClass().getMethod("getVote").invoke(event);
            username = (String) vote.getClass().getMethod("getUsername").invoke(vote);
            service = (String) vote.getClass().getMethod("getServiceName").invoke(vote);
            address = (String) vote.getClass().getMethod("getAddress").invoke(vote);
        } catch (ReflectiveOperationException | ClassCastException e) {
            Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
            plugin.getLogger().warning("Oy okunamadi: " + cause);
            return;
        }
        if (plugin.getServer().isPrimaryThread()) {
            vote(username, service, address);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> vote(username, service, address));
        }
    }

    private String today() {
        return LocalDate.now(ZoneId.of(plugin.getConfig().getString("gunluk-odul.saat-dilimi", "Europe/Istanbul"))).toString();
    }

    private Player onlinePlayer(String name) {
        Player exact = plugin.getServer().getPlayerExact(name);
        if (exact != null) {
            return exact;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    /**
     * Bir oyu isler. Sadece sunucuda daha once gorulmus oyunculara odul verilir (crack sunucuda rastgele isimlere
     * para dagitilmasin). Ayni site ayni oyuncu icin bekleme suresi icinde ikinci kez odul vermez; ayni IP'den
     * gunde en fazla ip-basina-max hesap odullendirilir. Odul verildiyse true.
     */
    boolean vote(String username, String service, String address) {
        if (!enabled || username == null || username.isBlank() || username.length() > 32) {
            return false;
        }
        String name = username.trim();
        String site = service == null || service.isBlank() ? "?" : service.trim();
        Player online = onlinePlayer(name);
        UUID id = online != null ? online.getUniqueId() : plugin.data().findByName(name);
        if (id == null) {
            OfflinePlayer cached = plugin.getServer().getOfflinePlayerIfCached(name);
            if (cached != null && cached.getName() != null) {
                id = cached.getUniqueId();
                name = cached.getName();
            }
        } else if (online != null) {
            name = online.getName();
        } else if (plugin.data().name(id) != null) {
            name = plugin.data().name(id);
        }
        if (id == null) {
            plugin.getLogger().info("Oy: " + name + " (" + site + ") bu sunucuda hic oynamamis, odul verilmedi.");
            return false;
        }
        long now = System.currentTimeMillis();
        long wait = Math.max(0, plugin.getConfig().getLong("oy.ayni-site-bekleme-saat", 12)) * 3_600_000L;
        if (wait > 0 && now - plugin.data().lastVote(id, site) < wait) {
            plugin.getLogger().info("Oy: " + name + " (" + site + ") bekleme suresi dolmadan tekrar geldi, sayilmadi.");
            return false;
        }
        int ipLimit = plugin.getConfig().getInt("oy.ip-basina-max", 2);
        String ip = address == null ? "" : address.trim();
        if (ipLimit > 0 && !ip.isEmpty() && !ip.equals("?")) {
            List<String> claims = plugin.data().voteClaimsFromIp(ip, site, today());
            if (claims.size() >= ipLimit && !claims.contains(id.toString())) {
                plugin.getLogger().info("Oy: " + name + " (" + site + ") ayni IP'den " + ipLimit + " hesaptan fazla, sayilmadi.");
                if (online != null) {
                    plugin.messages().send(online, "oy.mesajlar.ip-siniri", "max", ipLimit);
                }
                return false;
            }
            plugin.data().addVoteClaimFromIp(ip, site, today(), id);
        }

        plugin.data().setLastVote(id, site, now);
        plugin.data().addVote(id, name, plugin.leaderboards().currentMonth());
        if (online != null && plugin.isReady(online)) {
            reward(online, site);
        } else {
            plugin.data().setPendingVotes(id, plugin.data().pendingVotes(id) + 1);
        }
        plugin.messages().broadcast("oy.mesajlar.duyuru", "oyuncu", name, "site", site);
        countParty();
        return true;
    }

    private void reward(Player player, String site) {
        double money = plugin.getConfig().getDouble("oy.odul.para", 250);
        if (money > 0 && Double.isFinite(money)) {
            plugin.economy().deposit(player, money);
        }
        plugin.crates().giveKey(player, plugin.getConfig().getString("oy.odul.anahtar", ""),
                plugin.getConfig().getInt("oy.odul.anahtar-adet", 1));
        runCommands("oy.odul.komutlar", player);
        plugin.messages().send(player, "oy.mesajlar.tesekkur", "para", plugin.economy().format(money), "site", site);
        Messages.sound(player, "entity.player.levelup", 1.4f);
    }

    private void runCommands(String path, Player player) {
        for (String command : plugin.getConfig().getStringList(path)) {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                    Messages.fill(command, "oyuncu", player.getName()));
        }
    }

    /** Cevrimdisiyken (ya da AuthMe girisi bitmeden) gelen oylarin odulleri. */
    void deliverPending(Player player) {
        int pending = plugin.data().pendingVotes(player.getUniqueId());
        if (pending <= 0) {
            return;
        }
        plugin.data().setPendingVotes(player.getUniqueId(), 0);
        plugin.messages().send(player, "oy.mesajlar.bekleyen", "adet", pending);
        for (int i = 0; i < pending; i++) {
            reward(player, "?");
        }
    }

    /** Bugun oy vermemis oyuncuya girista hatirlatma. */
    void remind(Player player) {
        if (!enabled || sites().isEmpty()) {
            return;
        }
        if (System.currentTimeMillis() - plugin.data().lastVoteAny(player.getUniqueId()) > 24 * 3_600_000L) {
            plugin.messages().send(player, "oy.mesajlar.hatirlatma");
        }
    }

    private int partyTarget() {
        return plugin.getConfig().getBoolean("oy.parti.aktif", true) ? plugin.getConfig().getInt("oy.parti.hedef", 30) : 0;
    }

    private void countParty() {
        int target = partyTarget();
        if (target <= 0) {
            return;
        }
        int count = plugin.data().votePartyCount() + 1;
        if (count < target) {
            plugin.data().setVotePartyCount(count);
            if (target - count <= 5) {
                plugin.messages().broadcast("oy.mesajlar.parti-yaklasiyor", "kalan", target - count);
            }
            return;
        }
        plugin.data().setVotePartyCount(0);
        plugin.messages().broadcast("oy.mesajlar.parti", "hedef", target);
        plugin.announceToDiscord("oy-partisi", "hedef", target);
        double money = plugin.getConfig().getDouble("oy.parti.para", 0);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.isReady(player)) {
                continue;
            }
            if (money > 0 && Double.isFinite(money)) {
                plugin.economy().deposit(player, money);
            }
            plugin.crates().giveKey(player, plugin.getConfig().getString("oy.parti.anahtar", ""),
                    plugin.getConfig().getInt("oy.parti.anahtar-adet", 1));
            runCommands("oy.parti.komutlar", player);
            plugin.messages().title(player, "oy.mesajlar.parti-baslik", "oy.mesajlar.parti-alt-baslik");
            Messages.sound(player, "ui.toast.challenge_complete", 1.0f);
        }
    }

    String partyProgress() {
        int target = partyTarget();
        return target <= 0 ? "-" : plugin.data().votePartyCount() + "/" + target;
    }

    private List<Map<?, ?>> sites() {
        return plugin.getConfig().getMapList("oy.siteler");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!enabled) {
            messages.send(sender, "genel-mesajlar.yetki-yok");
            return true;
        }
        if (args.length > 0 && List.of("sira", "siralama", "top").contains(args[0].toLowerCase(Locale.ROOT))) {
            return plugin.leaderboards().onCommand(sender, command, label, new String[] {"oy"});
        }
        messages.sendLines(sender, "oy.mesajlar.liste-baslik");
        List<Map<?, ?>> sites = sites();
        if (sites.isEmpty()) {
            messages.send(sender, "oy.mesajlar.site-yok");
        }
        int index = 1;
        for (Map<?, ?> site : sites) {
            sender.sendMessage(Messages.color(messages.raw("oy.mesajlar.site-satir", "sira", index++,
                    "isim", String.valueOf(site.get("isim")), "adres", String.valueOf(site.get("adres")))));
        }
        if (sender instanceof Player player) {
            UUID id = player.getUniqueId();
            messages.sendLines(player, "oy.mesajlar.durum", "ay", plugin.data().votes(id, plugin.leaderboards().currentMonth()),
                    "toplam", plugin.data().totalVotes(id), "parti", partyProgress(),
                    "para", plugin.economy().format(plugin.getConfig().getDouble("oy.odul.para", 250)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1 && "siralama".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            result.add("siralama");
        }
        return result;
    }
}
