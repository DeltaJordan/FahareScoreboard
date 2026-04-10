package me.kelpdo.faharescoreboard.listeners;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import me.kelpdo.faharescoreboard.scoreboard.ScoreboardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public class ScoreboardListener implements Listener {
    private final Plugin plugin;
    private final ScoreboardManager scoreboard;
    private final HashMap<UUID, Instant> lastActionMade = new HashMap<>();

    private Instant lastDeath;

    @SuppressFBWarnings(value = "EI2")
    public ScoreboardListener(@NotNull Plugin plugin, @NotNull ScoreboardManager scoreboard) {
        this.plugin = plugin;
        this.scoreboard = scoreboard;

        this.lastDeath = Instant.MIN;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Duration sinceLastDeath = Duration.between(lastDeath, Instant.now());
        if (sinceLastDeath.toMinutes() < 1) {
            return;
        }

        this.scoreboard.deathReset();
        this.lastDeath = Instant.now();
    }

    @EventHandler(priority = EventPriority.MONITOR) 
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        plugin.getServer().sendMessage(
            Component.text(
                "You will be granted 5 minutes of respawn protection while the world loads. " +
                "Note that this protection does not protect you from entity damage (e.g. mobs or players). " +
                "Your protection is also cancelled if you perform an action, like breaking a block or hitting Network."
            ).color(TextColor.color(108, 59, 170))
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        lastActionMade.put(player.getUniqueId(), Instant.now());
        notifyProtectionLost(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamagedByEntity(EntityDamageByEntityEvent event) {
        Entity causingEntity = event.getDamager();
        if (causingEntity.getType() == EntityType.PLAYER) {
            Player player = (Player)event.getEntity();
            lastActionMade.put(player.getUniqueId(), Instant.now());
            notifyProtectionLost(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamaged(EntityDamageEvent event) {
        Duration sinceLastDeath = Duration.between(lastDeath, Instant.now());
        if (event.getEntityType() != EntityType.PLAYER || sinceLastDeath.toMinutes() < 5) {
            return;
        }

        Player damagedPlayer = (Player)event.getEntity();
        Instant lastActionInstant = lastActionMade.getOrDefault(damagedPlayer.getUniqueId(), Instant.MIN);
        Duration sinceLastAction = Duration.between(lastActionInstant, Instant.now());
        if (sinceLastAction.toMinutes() < 5) {
            return;
        }

        if (event.getDamageSource().getCausingEntity() == null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerGameModeChanged(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (!player.isOnline() || event.getNewGameMode() == GameMode.SPECTATOR) return;

        List<String> spectators = this.plugin.getConfig().getStringList("spectators");
        if (spectators.contains(player.getName()) || spectators.contains(player.getUniqueId().toString())) {
            event.setCancelled(true);
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTick(ServerTickEndEvent event) {
        this.scoreboard.tick();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        this.scoreboard.addPlayer(player);

        List<String> spectators = this.plugin.getConfig().getStringList("spectators");
        if (spectators.contains(player.getName()) || spectators.contains(player.getUniqueId().toString())) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLeave(PlayerQuitEvent event) {
        this.scoreboard.removePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldInit(WorldInitEvent event) {
        FileConfiguration config = plugin.getConfig();

        if (config.getBoolean("fixDifficulty")) {
            try {
                String difficultyStr = config.getString("difficulty");
                Difficulty difficulty = Difficulty.valueOf(difficultyStr);
                event.getWorld().setDifficulty(difficulty);
                event.getWorld().setHardcore(config.getBoolean("isHardcore"));
            } catch (Exception e) {
                plugin.getLogger().warning(() -> "Failed to fix difficulty of world " + event.getWorld().getName() + ":\n" + e);
            }
        }

        if (config.getBoolean("fixChainedTogether")) {
            try {
                event.getWorld().setGameRule(GameRules.RESPAWN_RADIUS, 0);
            } catch (Exception e) {
                plugin.getLogger().warning(() -> "Failed to set respawn radius of world " + event.getWorld().getName() + ":\n" + e);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDragonDamaged(EntityDamageEvent event) {
        if (event.getEntityType() == EntityType.ENDER_DRAGON) {
            EnderDragon dragonEntity = (EnderDragon)event.getEntity();
            AttributeInstance maxHealthAttribute = dragonEntity.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttribute != null) {
                double percentage = (dragonEntity.getHealth() / maxHealthAttribute.getValue()) * 100;
                this.scoreboard.setDragonHealth(percentage);
            }
        }
    }

    private void notifyProtectionLost(Player player) {
        Duration sinceLastDeath = Duration.between(lastDeath, Instant.now());
        if (sinceLastDeath.toMinutes() < 5) {
            player.sendMessage(Component.text("Your spawn protection was lost. Good luck!").color(TextColor.color(255, 0, 0)));
        }
    }
}