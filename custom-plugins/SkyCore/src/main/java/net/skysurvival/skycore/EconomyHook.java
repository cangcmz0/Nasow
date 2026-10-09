package net.skysurvival.skycore;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Vault uzerinden EssentialsX ekonomisine baglanir. Ekonomi sonradan yuklenebildigi icin her seferinde aranir. */
@SuppressWarnings("deprecation") // EssentialsX, Vault'un v1 ekonomi arayuzunu saglar
final class EconomyHook {
    private final SkyCore plugin;

    EconomyHook(SkyCore plugin) {
        this.plugin = plugin;
    }

    private Economy economy() {
        RegisteredServiceProvider<Economy> provider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }

    boolean available() {
        return economy() != null;
    }

    String format(double amount) {
        Economy economy = economy();
        return economy != null ? economy.format(amount) : String.format("%,.0f", amount);
    }

    double balance(OfflinePlayer player) {
        Economy economy = economy();
        return economy == null ? 0 : economy.getBalance(player);
    }

    boolean has(OfflinePlayer player, double amount) {
        Economy economy = economy();
        return economy != null && economy.has(player, amount);
    }

    boolean withdraw(OfflinePlayer player, double amount) {
        Economy economy = economy();
        return economy != null && economy.has(player, amount) && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    boolean deposit(OfflinePlayer player, double amount) {
        Economy economy = economy();
        return economy != null && economy.depositPlayer(player, amount).transactionSuccess();
    }
}
