package com.github.iamtakagi.edge;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ChatColor;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException;
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import net.megavex.scoreboardlibrary.api.sidebar.component.ComponentSidebarLayout;
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent;

public class Edge extends JavaPlugin {

  private static Edge instance;
  private EdgeConfig config;
  private ScoreboardLibrary scoreboard;
  private Map<UUID, PlayerSidebar> sidebars = new HashMap();

  @Override
  public void onEnable() {
    instance = this;
    this.saveDefaultConfig();
    this.loadConfig();
    this.setupScoreboard();
    this.getServer().getPluginManager().registerEvents(new SidebarListener(), this);
  }

  @Override
  public void onDisable() {
    this.saveDefaultConfig();
    scoreboard.close();
  }

  private void loadConfig() {
    this.config = new EdgeConfig((YamlConfiguration) this.getConfig());
  }

  private void setupScoreboard() {
    try {
      scoreboard = ScoreboardLibrary.loadScoreboardLibrary(this);
    } catch (NoPacketAdapterAvailableException e) {
      scoreboard = new NoopScoreboardLibrary();
      this.getLogger().warning("No scoreboard packet adapter available!");
    }
  }

  class PlayerSidebar {
    private Player player;
    private Sidebar sidebar;
    private ComponentSidebarLayout layout;
    private BukkitTask tickTask;

    PlayerSidebar(Player player) {
      this.player = player;
    }

    public void setup() {
      this.sidebar = scoreboard.createSidebar();
      this.sidebar.addPlayer(player);
      this.tickTask = Bukkit.getScheduler().runTaskTimer(instance, this::tick, 0, 20);
    }

    private void tick() {
      SidebarComponent.Builder builder = SidebarComponent.builder();
      SidebarComponent title = SidebarComponent.staticLine(
          Component.text(ChatColor.translateAlternateColorCodes('&', config.getSidebarSettings().getTitle())));
      for (int i = 0; i < config.getSidebarSettings().getLines().size(); i++) {
        String line = config.getSidebarSettings().getLines().get(i);
        if (line.length() > 0) {
          builder.addStaticLine(Component.text(ChatColor.translateAlternateColorCodes('&', parseLine(line))));
        } else {
          builder.addStaticLine(Component.empty());
        }
      }
      SidebarComponent component = builder.build();
      this.layout = new ComponentSidebarLayout(title, component);
      this.layout.apply(this.sidebar);
    }

    private String parseLine(String raw) {
      if (raw.contains("{PING}")) {
        raw = raw.replace("{PING}", "" + player.getPing());
      }

      if (raw.contains("{X}")) {
        raw = raw.replace("{X}", "" + player.getLocation().getBlockX());
      }

      if (raw.contains("{Y}")) {
        raw = raw.replace("{Y}", "" + player.getLocation().getBlockY());
      }

      if (raw.contains("{Z}")) {
        raw = raw.replace("{Z}", "" + player.getLocation().getBlockZ());
      }

      if (raw.contains("{DIRECTION}")) {
        raw = raw.replace("{DIRECTION}", Utils.getCardinalDirection(player));
      }

      if (raw.contains("{TPS}")) {
        double[] recetTps = Utils.getRecentTps();
        double avgTps = (recetTps[0] + recetTps[1] + recetTps[2]) / 3;
        raw = raw.replace("{TPS}", "" + (Math.floor(avgTps * 100)) / 100);
      }

      if (raw.contains("{DATE}")) {
        raw = raw.replace("{DATE}",
            new SimpleDateFormat(config.getSidebarSettings().getPatternSettings().getDatePattern())
                .format(Calendar.getInstance().getTime()));
      }

      if (raw.contains("{TIME}")) {
        raw = raw.replace("{TIME}",
            new SimpleDateFormat(config.getSidebarSettings().getPatternSettings().getTimePattern())
                .format(Calendar.getInstance().getTime()));
      }

      if (raw.contains("{USAGE_RAM}")) {
        raw = raw.replace("{USAGE_RAM}", String.format("%,d", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024));
      }

      if (raw.contains("{TOTAL_RAM}")) {
        raw = raw.replace("{TOTAL_RAM}", String.format("%,d", Runtime.getRuntime().totalMemory() / 1024 / 1024));
      }

      return raw;
    }
  }

  class SidebarListener implements Listener {
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
      PlayerSidebar sidebar = new PlayerSidebar(event.getPlayer());
      sidebar.setup();
      sidebars.put(event.getPlayer().getUniqueId(), sidebar);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
      sidebars.get(event.getPlayer().getUniqueId()).tickTask.cancel();
      sidebars.remove(event.getPlayer().getUniqueId());
    }
  }

  class EdgeConfig {
    private SidebarSettings sidebarSettings;

    EdgeConfig(YamlConfiguration yaml) {
      this.sidebarSettings = new SidebarSettings(yaml);
    }

    public SidebarSettings getSidebarSettings() {
      return this.sidebarSettings;
    }

    class SidebarSettings {
      private String title;
      private List<String> lines;
      private PatternSettings patternSettings;

      SidebarSettings(YamlConfiguration yaml) {
        this.title = yaml.getString("sidebar.title");
        this.lines = yaml.getStringList("sidebar.lines");
        this.patternSettings = new PatternSettings(yaml);
      }

      public String getTitle() {
        return this.title;
      }

      public List<String> getLines() {
        return this.lines;
      }

      public PatternSettings getPatternSettings() {
        return this.patternSettings;
      }

      class PatternSettings {
        private String datePattern;
        private String timePattern;

        PatternSettings(YamlConfiguration yaml) {
          this.datePattern = yaml.getString("sidebar.pattern.date");
          this.timePattern = yaml.getString("sidebar.pattern.time");
        }

        public String getDatePattern() {
          return this.datePattern;
        }

        public String getTimePattern() {
          return this.timePattern;
        }
      }
    }
  }

  static class Utils {
    static String getCardinalDirection(Player player) {
      double rotation = (player.getLocation().getYaw() - 90.0F) % 360.0F;
      if (rotation < 0.0D) {
        rotation += 360.0D;
      }
      if ((0.0D <= rotation) && (rotation < 22.5D)) {
        return "北";
      }
      if ((22.5D <= rotation) && (rotation < 67.5D)) {
        return "北東";
      }
      if ((67.5D <= rotation) && (rotation < 112.5D)) {
        return "東";
      }
      if ((112.5D <= rotation) && (rotation < 157.5D)) {
        return "南東";
      }
      if ((157.5D <= rotation) && (rotation < 202.5D)) {
        return "南";
      }
      if ((202.5D <= rotation) && (rotation < 247.5D)) {
        return "南西";
      }
      if ((247.5D <= rotation) && (rotation < 292.5D)) {
        return "西";
      }
      if ((292.5D <= rotation) && (rotation < 337.5D)) {
        return "北西";
      }
      if ((337.5D <= rotation) && (rotation < 360.0D)) {
        return "北";
      }
      return null;
    }

    static double[] getRecentTps() {
      double[] recentTps = null;
      try {
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        recentTps = ((double[]) server.getClass().getField("recentTps").get(server));
      } catch (ReflectiveOperationException e) {
        e.printStackTrace();
      }
      return recentTps;
    }
  }
}
