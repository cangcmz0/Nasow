package net.skysurvival.skycore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Testlerin ortak kurulumu: sahte sunucu, sahte Vault ekonomisi ve yardimcilar. */
@SuppressWarnings("deprecation")
abstract class TestBase {
    protected ServerMock server;
    protected SkyCore plugin;
    protected final Map<UUID, Double> balances = new HashMap<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin vault = MockBukkit.createMockPlugin("Vault");
        server.getServicesManager().register(Economy.class, fakeEconomy(), vault, ServicePriority.Normal);
        plugin = MockBukkit.load(SkyCore.class);
        // Yeni oyuncu korumasi PvP testlerini bozmasin; kendi testinde acilir.
        plugin.getConfig().set("yeni-oyuncu-korumasi.aktif", false);
        plugin.saveConfig();
        plugin.reload();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Vault ekonomisinin bakiyeleri bir haritada tutan sahtesi. */
    protected Economy fakeEconomy() {
        return (Economy) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Economy.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "isEnabled", "hasAccount", "createPlayerAccount":
                    return true;
                case "getName":
                    return "Test";
                case "format":
                    return String.format("%.0f₺", (Double) args[0]);
                case "getBalance":
                    return balance(args[0]);
                case "has":
                    return balance(args[0]) >= (Double) args[1];
                case "withdrawPlayer": {
                    UUID id = ((OfflinePlayer) args[0]).getUniqueId();
                    double after = balance(args[0]) - (Double) args[1];
                    if (after < 0) {
                        return new EconomyResponse(0, balance(args[0]), EconomyResponse.ResponseType.FAILURE, "yetersiz");
                    }
                    balances.put(id, after);
                    return new EconomyResponse((Double) args[1], after, EconomyResponse.ResponseType.SUCCESS, null);
                }
                case "depositPlayer": {
                    UUID id = ((OfflinePlayer) args[0]).getUniqueId();
                    double after = balance(args[0]) + (Double) args[1];
                    balances.put(id, after);
                    return new EconomyResponse((Double) args[1], after, EconomyResponse.ResponseType.SUCCESS, null);
                }
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "FakeEconomy";
                default:
                    Class<?> type = method.getReturnType();
                    return type == boolean.class ? false : type == int.class ? 0 : type == double.class ? 0.0 : null;
            }
        });
    }

    protected double balance(Object player) {
        return balances.getOrDefault(((OfflinePlayer) player).getUniqueId(), 0.0);
    }

    protected static List<String> messages(PlayerMock player) {
        List<String> list = new ArrayList<>();
        Component message;
        while ((message = player.nextComponentMessage()) != null) {
            list.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
        return list;
    }

    /** Gercek sunucudaki gibi oyuncunun oyuncuya vurdugu olayi tetikler. */
    protected void hit(PlayerMock attacker, PlayerMock victim) {
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(attacker).withDirectEntity(attacker).build();
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 2.0));
    }

    protected static PlayerDeathEvent death(PlayerMock victim, List<ItemStack> drops) {
        return new PlayerDeathEvent(victim, DamageSource.builder(DamageType.GENERIC).build(), drops, 0, Component.text("x"), true);
    }

    protected static boolean saw(List<String> messages, String part) {
        return messages.stream().anyMatch(m -> m.contains(part));
    }

}
