package me.kelpdo.faharescoreboard.listeners;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import me.kelpdo.faharescoreboard.scoreboard.ScoreboardManager;

public class ScoreboardListener implements Listener {
    private final Plugin plugin;
    private final ScoreboardManager scoreboard;

    private Instant lastDeath;

    @SuppressFBWarnings(value = "EI2")
    public ScoreboardListener(@NotNull Plugin plugin, @NotNull ScoreboardManager scoreboard) {
        this.plugin = plugin;
        this.scoreboard = scoreboard;

        this.lastDeath = Instant.MIN;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        List<String> spectators = this.plugin.getConfig().getStringList("spectators");
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (spectators.contains(player.getName()) || spectators.contains(player.getUniqueId().toString())) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        }

        Duration sinceLastDeath = Duration.between(lastDeath, Instant.now());
        if (sinceLastDeath.toMinutes() < 1)
        {
            return;
        }

        this.scoreboard.deathReset();
        this.lastDeath = Instant.now();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTick(ServerTickEndEvent event) {
        this.scoreboard.tick();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.scoreboard.addPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLeave(PlayerQuitEvent event) {
        this.scoreboard.removePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPortal(WorldLoadEvent event) {
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
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamaged(EntityDamageEvent event) {
        if (event.getEntityType() == EntityType.ENDER_DRAGON) {
            EnderDragon dragonEntity = (EnderDragon)event.getEntity();
            AttributeInstance maxHealthAttribute = dragonEntity.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttribute != null) {
                double percentage = (dragonEntity.getHealth() / maxHealthAttribute.getValue()) * 100;
                this.scoreboard.setDragonHealth(percentage);
            }
        }
    }
}