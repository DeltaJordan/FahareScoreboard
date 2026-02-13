package me.kelpdo.faharescoreboard.listeners;

import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;

import dev.qixils.fahare.events.FahareResetEvent;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import me.kelpdo.faharescoreboard.scoreboard.ScoreboardManager;

public class ScoreboardListener implements Listener {
    private final Plugin plugin;
    private final ScoreboardManager scoreboard;

    @SuppressFBWarnings(value = "EI2")
    public ScoreboardListener(@NotNull Plugin plugin, @NotNull ScoreboardManager scoreboard) {
        this.plugin = plugin;
        this.scoreboard = scoreboard;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFahareReset(FahareResetEvent event) {
        this.scoreboard.deathReset();

        List<String> spectators = this.plugin.getConfig().getStringList("spectators");
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (spectators.contains(player.getName()) || spectators.contains(player.getUniqueId().toString())) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTick(ServerTickEndEvent event) {
        this.scoreboard.tick();
    }
}