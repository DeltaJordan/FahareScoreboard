package me.kelpdo.faharescoreboard;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.lib.PaperLib;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.kelpdo.faharescoreboard.listeners.ScoreboardListener;
import me.kelpdo.faharescoreboard.scoreboard.ScoreboardManager;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException;
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary;

public class FahareScoreboard extends JavaPlugin {
    private final Permission setDeathsPerm = new Permission("faharescoreboard.setDeaths", "Whether to allow setting the death count.", PermissionDefault.OP);

    private ScoreboardLibrary scoreboardLibrary;
    private ScoreboardManager scoreboardManager;

    @Override
    public void onEnable() {
        PaperLib.suggestPaper(this);
        saveDefaultConfig();

        try {
            scoreboardLibrary = ScoreboardLibrary.loadScoreboardLibrary(this);
        } catch (NoPacketAdapterAvailableException e) {
            scoreboardLibrary = new NoopScoreboardLibrary();
            this.getLogger().warning("No scoreboard packet adapter available!");
        }

        scoreboardManager = new ScoreboardManager(this, scoreboardLibrary.createSidebar());

        this.getServer().getPluginManager().registerEvents(new ScoreboardListener(this, scoreboardManager), this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            LiteralCommandNode<CommandSourceStack> buildCommand = Commands.literal("faharescoreboard")
                .then(Commands.literal("setDeaths")
                    .then(Commands.argument("deathCount", IntegerArgumentType.integer()))
                    .requires(Commands.restricted(source -> source.getSender().hasPermission(setDeathsPerm)))
                    .executes(ctx -> {
                        this.scoreboardManager.setDeaths(IntegerArgumentType.getInteger(ctx, "deathCount"));
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .build();

            commands.registrar().register(buildCommand);
        });
    }

    @Override
    public void onDisable() {
        scoreboardManager.close();
        scoreboardLibrary.close();
    }
}
