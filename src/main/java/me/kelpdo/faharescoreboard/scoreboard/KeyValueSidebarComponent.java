package me.kelpdo.faharescoreboard.scoreboard;

import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.megavex.scoreboardlibrary.api.sidebar.component.LineDrawable;
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent;

public class KeyValueSidebarComponent implements SidebarComponent {
  private final Component key;
  private final Supplier<Component> valueSupplier;

  public KeyValueSidebarComponent(@NotNull Component key, @NotNull Supplier<Component> valueSupplier) {
    this.key = key;
    this.valueSupplier = valueSupplier;
  }

  @Override
  public void draw(@NotNull LineDrawable drawable) {
    var value = valueSupplier.get();
    var line = Component.text()
      .append(key)
      .append(Component.text(": "))
      .append(value.colorIfAbsent(NamedTextColor.AQUA))
      .build();

    drawable.drawLine(line);
  }
}