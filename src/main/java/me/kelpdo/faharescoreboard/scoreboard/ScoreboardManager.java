package me.kelpdo.faharescoreboard.scoreboard;

import java.time.Duration;
import java.time.Instant;
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
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import net.megavex.scoreboardlibrary.api.sidebar.component.ComponentSidebarLayout;
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent;
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.CollectionSidebarAnimation;
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.SidebarAnimation;

public class ScoreboardManager {
    private static final PersistentDataType RUN_DURATION_TYPE = PersistentDataType.LONG;

    private final Plugin plugin;

    private final Sidebar sidebar;
    private final ComponentSidebarLayout componentSidebar;
    private final SidebarAnimation<Component> titleAnimation;

    private final NamespacedKey longestRunKey;
    private final NamespacedKey currentRunKey;

    private final boolean ensurePlayersOnline;
    private final List<String> playerIdentifiers;

    private Duration longestRun;
    private TextColor longestRunColor;
    private Duration currentRun;
    private TextColor currentRunColor;
    private Instant lastTick;

    @SuppressFBWarnings(value = "EI2")
    public ScoreboardManager(@NotNull Plugin plugin, @NotNull Sidebar sidebar) {
        this.plugin = plugin;
        this.sidebar = sidebar;

        this.currentRunKey = new NamespacedKey(plugin, "currentRun");
        this.longestRunKey = new NamespacedKey(plugin, "longestRun");

        FileConfiguration config = plugin.getConfig();
        this.ensurePlayersOnline = config.getBoolean("ensurePlayersOnline");
        this.playerIdentifiers = config.getStringList("players");

        this.lastTick = Instant.now();

        World world = plugin.getServer().getWorlds().getFirst();
        PersistentDataContainer pdc = world.getPersistentDataContainer();

        long storedCurrentRun = pdc.getOrDefault(this.currentRunKey, RUN_DURATION_TYPE, 0L);
        this.currentRun = Duration.ofNanos(storedCurrentRun);

        long storedLongestRun = pdc.getOrDefault(this.longestRunKey, RUN_DURATION_TYPE, 0L);
        this.longestRun = Duration.ofNanos(storedLongestRun);

        this.titleAnimation = this.createGradientAnimation(Component.text("⫘⫘⫘⫘⫘Chained Together⫘⫘⫘⫘⫘", Style.style(TextDecoration.BOLD)));
        SidebarComponent title = SidebarComponent.animatedLine(this.titleAnimation);

        SidebarComponent lines = SidebarComponent.builder()
            .addStaticLine(Component.text("Longest Run"))
            .addDynamicLine(() -> {
                String longestRunText = String.format("%s:%s:%s",
                        this.longestRun.toHours(), 
                        this.longestRun.toMinutesPart(), 
                        this.longestRun.toSecondsPart());
                return Component.text(longestRunText, this.longestRunColor);
            })
            .addBlankLine()
            .addStaticLine(Component.text("Current Run"))
            .addDynamicLine(() -> {
                String currentRunText = String.format("%s:%s:%s",
                        this.currentRun.toHours(), 
                        this.currentRun.toMinutesPart(), 
                        this.currentRun.toSecondsPart());
                return Component.text(currentRunText, this.currentRunColor);
            })
            .build();

        this.componentSidebar = new ComponentSidebarLayout(title, lines);
    }

    public void addPlayer(Player player) {
        if (!this.sidebar.players().contains(player)) {
            this.sidebar.addPlayer(player);
        }
    }

    public void deathReset() {
        if (this.currentRun.compareTo(this.longestRun) > 0) {
            this.longestRun = this.currentRun;
        }

        this.currentRun = Duration.ZERO;
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
            Instant currentTick = Instant.now();
            int nanoDiff = currentTick.getNano() - this.lastTick.getNano();

            this.currentRun = this.currentRun.plusNanos(nanoDiff);

            if (this.currentRun.compareTo(this.longestRun) > 0) {
                this.longestRun = this.currentRun;
                this.longestRunColor = this.currentRunColor = NamedTextColor.GOLD;
            } else {
                this.longestRunColor = this.currentRunColor = NamedTextColor.GRAY;
            }

            this.lastTick = currentTick;

            this.save();
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
        pdc.set(this.longestRunKey, RUN_DURATION_TYPE, this.longestRun.toNanos());
        pdc.set(this.currentRunKey, RUN_DURATION_TYPE, this.currentRun.toNanos());
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