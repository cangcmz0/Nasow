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

    private CombatModule combat;
    private ChatGameModule chatGames;
    private BanknoteModule banknotes;
    private BountyModule bounties;
    private DailyRewardModule dailyRewards;
    private PlaytimeModule playtime;
    private AnnouncerModule announcer;
    private DeathModule deaths;
    private WelcomeModule welcome;
    private List<Module> modules;

    /** AuthMe girisi tamamlanmis ve "hos geldin" islemleri yapilmis oyuncular. */
    private final Set<UUID> ready = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        economy = new EconomyHook(this);
        data = new DataStore(this);
        data.load();
        auth = AuthHook.create(this);

        combat = new CombatModule(this);
        chatGames = new ChatGameModule(this);
        banknotes = new BanknoteModule(this);
        bounties = new BountyModule(this);
        dailyRewards = new DailyRewardModule(this);
        playtime = new PlaytimeModule(this);
        announcer = new AnnouncerModule(this);
        deaths = new DeathModule(this);
        welcome = new WelcomeModule(this);
        modules = List.of(combat, chatGames, banknotes, bounties, dailyRewards, playtime, announcer, deaths, welcome);

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

        modules.forEach(Module::start);
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
        }
        if (data != null) {
            data.save();
        }
    }

    public void reload() {
        reloadConfig();
        messages.reload();
        modules.forEach(Module::stop);
        modules.forEach(Module::start);
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
}
