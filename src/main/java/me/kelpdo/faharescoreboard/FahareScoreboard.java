package me.kelpdo.faharescoreboard;

import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.lib.PaperLib;
import me.kelpdo.faharescoreboard.listeners.ScoreboardListener;
import me.kelpdo.faharescoreboard.scoreboard.ScoreboardManager;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException;
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary;

public class FahareScoreboard extends JavaPlugin {
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
    }

    @Override
    public void onDisable() {
        scoreboardManager.close();
        scoreboardLibrary.close();
    }
}
