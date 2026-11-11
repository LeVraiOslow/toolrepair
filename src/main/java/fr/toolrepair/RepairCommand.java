package fr.toolrepair;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class RepairCommand implements CommandExecutor, TabCompleter {
    private final ToolRepair plugin;

    public RepairCommand(ToolRepair plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length >= 1 && a[0].equalsIgnoreCase("reload")) {
            if (!s.hasPermission("toolrepair.admin")) {
                this.plugin.msg(s, "no-permission");
                return true;
            }
            this.plugin.reload();
            this.plugin.msg(s, "reloaded");
            return true;
        }
        if (a.length >= 2 && a[0].equalsIgnoreCase("open")) {
            if (!s.hasPermission("toolrepair.admin")) {
                this.plugin.msg(s, "no-permission");
                return true;
            }
            Player target = Bukkit.getPlayerExact(a[1]);
            if (target == null) {
                this.plugin.msg(s, "unknown-player");
                return true;
            }
            this.plugin.menu().open(target);
            return true;
        }
        if (!(s instanceof Player p)) {
            this.plugin.msg(s, "player-only");
            return true;
        }
        if (!p.hasPermission("toolrepair.use")) {
            this.plugin.msg(s, "no-permission");
            return true;
        }
        this.plugin.menu().open(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("toolrepair.admin")) {
            return out;
        }
        if (a.length == 1) {
            for (String x : List.of("reload", "open")) {
                if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) {
                    out.add(x);
                }
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("open")) {
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            });
        }
        return out;
    }
}
