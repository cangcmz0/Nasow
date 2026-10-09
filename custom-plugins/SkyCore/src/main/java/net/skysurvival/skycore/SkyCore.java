package net.skysurvival.skycore;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sky Survival'a ozel ozellikler. Her ozellik ayri bir modul sinifindadir ve
 * config.yml'den acilip kapatilabilir.
 */
public class SkyCore extends JavaPlugin implements Listener {
    private Messages messages;
    private EconomyHook economy;
    private DataStore data;
    private AuthHook auth;
    private Integrations integrations;

    private CombatModule combat;
    private ChatGameModule chatGames;
    private BanknoteModule banknotes;
    private BountyModule bounties;
    private DailyRewardModule dailyRewards;
    private PlaytimeModule playtime;
    private AnnouncerModule announcer;
    private DeathModule deaths;
    private WelcomeModule welcome;
    private SpawnModule spawn;
    private WildModule wild;
    private CrateModule crates;
    private KothModule koth;
    private ClanModule clans;
    private MarketModule market;
    private List<Module> modules;

    /** AuthMe girisi tamamlanmis ve "hos geldin" islemleri yapilmis oyuncular. */
    private final Set<UUID> ready = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        addMissingSettings();
        messages = new Messages(this);
        economy = new EconomyHook(this);
        data = new DataStore(this);
        data.load();
        auth = AuthHook.create(this);
        integrations = new Integrations(this);

        combat = new CombatModule(this);
        chatGames = new ChatGameModule(this);
        banknotes = new BanknoteModule(this);
        bounties = new BountyModule(this);
        dailyRewards = new DailyRewardModule(this);
        playtime = new PlaytimeModule(this);
        announcer = new AnnouncerModule(this);
        deaths = new DeathModule(this);
        welcome = new WelcomeModule(this);
        spawn = new SpawnModule(this);
        wild = new WildModule(this);
        crates = new CrateModule(this);
        koth = new KothModule(this);
        clans = new ClanModule(this);
        market = new MarketModule(this);
        modules = List.of(combat, chatGames, banknotes, bounties, dailyRewards, playtime, announcer, deaths, welcome,
                spawn, wild, crates, koth, clans, market);

        for (Module module : modules) {
            getServer().getPluginManager().registerEvents(module, this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        if (auth.isAuthMe()) {
            getServer().getPluginManager().registerEvents(new AuthMeLoginListener(this), this);
        }

        command("skycore", new AdminCommand(this));
        command("banknot", banknotes);
        command("kelle", bounties);
        command("odul", dailyRewards);
        InfoCommand info = new InfoCommand(this);
        command("discord", info);
        command("site", info);
        command("vahsi", wild);
        command("market", market);
        command("sat", market);
        command("klan", clans);
        command("klansohbet", clans);
        command("koth", koth);

        modules.forEach(Module::start);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            SkyPlaceholders.register(this);
        }
        // Veriler dakikada bir (degistiyse) diske yazilir.
        getServer().getScheduler().runTaskTimer(this, data::saveIfDirty, 20L * 60, 20L * 60);

        // /reload ile yuklenirse oyundaki oyuncular icin.
        for (Player player : getServer().getOnlinePlayers()) {
            if (auth.isLoggedIn(player)) {
                ready.add(player.getUniqueId());
            }
        }
        getLogger().info("SkyCore acildi" + (auth.isAuthMe() ? " (AuthMe uyumlu)" : "") + ".");
    }

    @Override
    public void onDisable() {
        if (modules != null) {
            modules.forEach(Module::stop);
            spawn.shutdown();
        }
        if (data != null) {
            data.save();
        }
    }

    public void reload() {
        reloadConfig();
        addMissingSettings();
        messages.reload();
        modules.forEach(Module::stop);
        modules.forEach(Module::start);
    }

    /**
     * Eski surumden kalan config.yml'de olmayan yeni ayarlari (ornegin yeni ozelliklerin bolumleri) ekler;
     * oyuncunun degistirdigi degerlere dokunmaz.
     */
    private void addMissingSettings() {
        org.bukkit.configuration.Configuration defaults = getConfig().getDefaults();
        if (defaults == null) {
            return;
        }
        for (String key : defaults.getKeys(true)) {
            if (!getConfig().isSet(key)) {
                getConfig().options().copyDefaults(true);
                saveConfig();
                getLogger().info("config.yml'e yeni surumun ayarlari eklendi.");
                return;
            }
        }
    }

    private void command(String name, Object executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("plugin.yml icinde komut yok: " + name);
            return;
        }
        command.setExecutor((org.bukkit.command.CommandExecutor) executor);
        if (executor instanceof org.bukkit.command.TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    // ---- Giris (AuthMe varsa sifre girildikten sonra) ----

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        welcome.rememberFirstJoin(player);
        if (!auth.isAuthMe()) {
            playerReady(player);
            return;
        }
        // Premium / oturum girisi gibi durumlarda AuthMe girisi hemen tamamlayabilir.
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && auth.isLoggedIn(player)) {
                playerReady(player);
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ready.remove(event.getPlayer().getUniqueId());
    }

    /** Oyuncu oyuna girdi ve (varsa) AuthMe girisini yapti. Bir oturumda bir kez calisir. */
    void playerReady(Player player) {
        if (!ready.add(player.getUniqueId())) {
            return;
        }
        welcome.greet(player);
        dailyRewards.remind(player);
        koth.showTo(player);
    }

    // ---- Diger siniflarin kullandigi ortak parcalar ----

    Messages messages() {
        return messages;
    }

    EconomyHook economy() {
        return economy;
    }

    DataStore data() {
        return data;
    }

    AuthHook auth() {
        return auth;
    }

    ChatGameModule chatGames() {
        return chatGames;
    }

    AnnouncerModule announcer() {
        return announcer;
    }

    Integrations integrations() {
        return integrations;
    }

    CombatModule combat() {
        return combat;
    }

    SpawnModule spawn() {
        return spawn;
    }

    CrateModule crates() {
        return crates;
    }

    KothModule koth() {
        return koth;
    }

    ClanModule clans() {
        return clans;
    }

    MarketModule market() {
        return market;
    }
}
