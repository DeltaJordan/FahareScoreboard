package me.kelpdo.faharescoreboard.scoreboard;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import net.megavex.scoreboardlibrary.api.sidebar.component.ComponentSidebarLayout;
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent;
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.CollectionSidebarAnimation;
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.SidebarAnimation;

public class ScoreboardManager {
    private static final PersistentDataType PDC_INT_TYPE = PersistentDataType.INTEGER;
    private static final PersistentDataType PDC_LONG_TYPE = PersistentDataType.LONG;
    private static final PersistentDataType PDC_DOUBLE_TYPE = PersistentDataType.DOUBLE;

    private final Plugin plugin;

    private final Sidebar sidebar;
    private final ComponentSidebarLayout componentSidebar;
    private final SidebarAnimation<Component> titleAnimation;

    private final NamespacedKey longestRunKey;
    private final NamespacedKey currentRunKey;
    private final NamespacedKey deathsKey;
    private final NamespacedKey dragonHealthKey;

    private final boolean ensurePlayersOnline;
    private final List<String> playerIdentifiers;

    private Instant runStart;

    private Duration longestRun;
    private TextColor longestRunColor;
    private double lowestDragonHealthPercentage = 100;

    private Duration currentRun;
    private TextColor currentRunColor;

    private int totalDeaths;

    @SuppressFBWarnings(value = "EI2")
    public ScoreboardManager(@NotNull Plugin plugin, @NotNull Sidebar sidebar) {
        this.plugin = plugin;
        this.sidebar = sidebar;

        this.currentRunKey = new NamespacedKey(plugin, "currentRun");
        this.longestRunKey = new NamespacedKey(plugin, "longestRun");
        this.deathsKey = new NamespacedKey(plugin, "deaths");
        this.dragonHealthKey = new NamespacedKey(plugin, "dragonHealth");

        FileConfiguration config = plugin.getConfig();
        this.ensurePlayersOnline = config.getBoolean("ensurePlayersOnline");
        this.playerIdentifiers = config.getStringList("players");

        World world = plugin.getServer().getWorlds().getFirst();
        PersistentDataContainer pdc = world.getPersistentDataContainer();

        long storedCurrentRun = pdc.getOrDefault(this.currentRunKey, PDC_LONG_TYPE, 0L);
        this.runStart = Instant.now().minus(storedCurrentRun, ChronoUnit.NANOS);
        this.currentRun = Duration.ofNanos(storedCurrentRun);

        long storedLongestRun = pdc.getOrDefault(this.longestRunKey, PDC_LONG_TYPE, 0L);
        this.longestRun = Duration.ofNanos(storedLongestRun);

        this.lowestDragonHealthPercentage = pdc.getOrDefault(dragonHealthKey, PDC_DOUBLE_TYPE, 100);
        this.totalDeaths = pdc.getOrDefault(deathsKey, PDC_INT_TYPE, 0);

        this.titleAnimation = this.createGradientAnimation(Component.text("⫘⫘⫘⫘⫘Chained Together⫘⫘⫘⫘⫘"));
        SidebarComponent title = SidebarComponent.animatedLine(this.titleAnimation);

        SidebarComponent totalDeathComponent = new KeyValueSidebarComponent(
            Component.text("Total Deaths"),
            () -> Component.text(this.totalDeaths)
        );

        SidebarComponent lowestDragonHealth = new KeyValueSidebarComponent(
            Component.text("Lowest Dragon Health"),
            () -> Component.text(String.format("%.1f%s", this.lowestDragonHealthPercentage, "%"))
        );

        SidebarComponent lines = SidebarComponent.builder()
            .addStaticLine(Component.text("Longest Run"))
            .addDynamicLine(() -> {
                String longestRunText = String.format("%02d:%02d:%02d",
                        this.longestRun.toHours(), 
                        this.longestRun.toMinutesPart(), 
                        this.longestRun.toSecondsPart());
                return Component.text(longestRunText, this.longestRunColor);
            })
            .addComponent(lowestDragonHealth)
            .addBlankLine()
            .addStaticLine(Component.text("Current Run"))
            .addDynamicLine(() -> {
                String currentRunText = String.format("%02d:%02d:%02d",
                        this.currentRun.toHours(), 
                        this.currentRun.toMinutesPart(), 
                        this.currentRun.toSecondsPart());
                return Component.text(currentRunText, this.currentRunColor);
            })
            .addBlankLine()
            .addComponent(totalDeathComponent)
            .build();

        this.componentSidebar = new ComponentSidebarLayout(title, lines);
    }

    public void addPlayer(Player player) {
        this.sidebar.addPlayer(player);
    }

    public void removePlayer(Player player) {
        this.sidebar.removePlayer(player);
    }

    public void setDeaths(int deathCount) {
        this.totalDeaths = deathCount;
        this.save();
    }

    public void setDragonHealth(double percentage) {
        this.setDragonHealth(percentage, false);
    }

    public void setDragonHealth(double percentage, boolean overwrite) {
        if (overwrite || percentage < this.lowestDragonHealthPercentage) {
            this.lowestDragonHealthPercentage = percentage;
        }
    }

    public void deathReset() {
        this.deathReset(true);
    }

    public void deathReset(boolean incrementDeaths) {
        if (this.currentRun.compareTo(this.longestRun) > 0) {
            this.longestRun = this.currentRun;
        }

        this.runStart = Instant.now();
        this.currentRun = Duration.ZERO;

        if (incrementDeaths) {
            this.totalDeaths++;
        }
        
        this.save();
    }

    public void tick() {
        boolean shouldTickRun;

        if (this.ensurePlayersOnline) {
            if (this.playerIdentifiers.isEmpty()) {
                shouldTickRun = true;
            } else {
                int gamers = 0;
                for (Player player : this.plugin.getServer().getOnlinePlayers()) {
                    if (this.playerIdentifiers.contains(player.getName()) || this.playerIdentifiers.contains(player.getUniqueId().toString())) {
                        gamers++;
                    }
                }

                shouldTickRun = gamers == this.playerIdentifiers.size();
            }
        } else {
            int gamers = 0;
            for (Player player : this.plugin.getServer().getOnlinePlayers()) {
                if (player.getGameMode() == GameMode.SURVIVAL) {
                    gamers++;
                }
            }

            shouldTickRun = gamers > 0;
        }

        if (shouldTickRun) {
            this.currentRun = Duration.between(this.runStart, Instant.now());

            if (this.currentRun.compareTo(this.longestRun) > 0) {
                this.longestRun = this.currentRun;
                this.longestRunColor = this.currentRunColor = NamedTextColor.GOLD;
            } else {
                this.longestRunColor = this.currentRunColor = NamedTextColor.GRAY;
            }

            this.save();
        } else {
            this.runStart = Instant.now().minus(this.currentRun.toNanos(), ChronoUnit.NANOS);
            this.currentRunColor = NamedTextColor.RED;
        }

        this.titleAnimation.nextFrame();
        this.componentSidebar.apply(this.sidebar);
    }

    public void close()
    {
        this.sidebar.close();
    }

    private void save() {
        World world = this.plugin.getServer().getWorlds().getFirst();
        PersistentDataContainer pdc = world.getPersistentDataContainer();
        pdc.set(this.longestRunKey, PDC_LONG_TYPE, this.longestRun.toNanos());
        pdc.set(this.currentRunKey, PDC_LONG_TYPE, this.currentRun.toNanos());
        pdc.set(this.deathsKey, PDC_INT_TYPE, this.totalDeaths);
        pdc.set(this.dragonHealthKey, PDC_DOUBLE_TYPE, this.lowestDragonHealthPercentage);
    }

    @SuppressFBWarnings(value = "FL")
    private @NotNull SidebarAnimation<Component> createGradientAnimation(@NotNull Component text) {
        float step = 1f / 8f;

        TagResolver.Single textPlaceholder = Placeholder.component("text", text);
        List<Component> frames = new ArrayList<>((int) (2f / step));

        float phase = -1f;
        while (phase < 1) {
            frames.add(MiniMessage.miniMessage().deserialize("<gradient:yellow:gold:" + phase + "><text>", textPlaceholder));
            phase += step;
        }

        return new CollectionSidebarAnimation<>(frames);
    }
}