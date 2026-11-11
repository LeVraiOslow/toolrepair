package fr.toolrepair;

import fr.customtools.CustomTools;
import fr.customtools.ToolDefinition;
import fr.customtools.ToolManager;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class ToolRepair extends JavaPlugin {
    private NexoHook nexo;
    private Economy economy;
    private RepairMenu menu;
    private ItemSpec orbSpec;
    private int orbAmount = 1;
    private final Set<String> repairable = new HashSet<>();
    private boolean repairAll;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.nexo = new NexoHook(this);
        this.loadSettings();
        this.menu = new RepairMenu(this);
        this.getServer().getPluginManager().registerEvents(this.menu, this);
        RepairCommand cmd = new RepairCommand(this);
        PluginCommand pc = this.getCommand("toolrepair");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        if (this.economy() == null) {
            this.getLogger().warning("Aucune économie Vault trouvée pour l'instant (un plugin d'économie doit être installé).");
        }
    }

    @Override
    public void onDisable() {
        if (this.menu != null) {
            this.menu.closeAll();
        }
    }

    public void reload() {
        this.menu.closeAll();
        this.reloadConfig();
        this.economy = null;
        this.loadSettings();
    }

    private void loadSettings() {
        this.orbSpec = ItemSpec.parse(this.getConfig().getString("orb.item", "nexo:orbre"));
        if (!this.orbSpec.valid()) {
            this.getLogger().warning("orb.item invalide : " + this.orbSpec.raw());
        }
        this.orbAmount = Math.max(1, this.getConfig().getInt("orb.amount", 1));
        this.repairable.clear();
        this.repairAll = false;
        for (String id : this.getConfig().getStringList("repairable-tools")) {
            if (id.trim().equals("*")) {
                this.repairAll = true;
            } else {
                this.repairable.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        ToolManager tm = this.tools();
        for (String id : this.repairable) {
            if (tm.get(id) == null) {
                this.getLogger().warning("repairable-tools : outil CustomTools inconnu '" + id + "'.");
            }
        }
    }

    // ───────────── Accès aux autres plugins ─────────────

    public ToolManager tools() {
        return ((CustomTools) this.getServer().getPluginManager().getPlugin("CustomTools")).tools();
    }

    public RepairMenu menu() {
        return this.menu;
    }

    public NexoHook nexo() {
        return this.nexo;
    }

    public Economy economy() {
        if (this.economy == null) {
            RegisteredServiceProvider<Economy> rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                this.economy = rsp.getProvider();
            }
        }
        return this.economy;
    }

    public ItemSpec orbSpec() {
        return this.orbSpec;
    }

    public int orbAmount() {
        return this.orbAmount;
    }

    public boolean isRepairable(ToolDefinition def) {
        return def != null && def.durability() > 0 && (this.repairAll || this.repairable.contains(def.id()));
    }

    // ───────────── Messages / sons ─────────────

    public void msg(CommandSender to, String key, TagResolver... resolvers) {
        String raw = this.getConfig().getString("messages." + key);
        if (raw != null && raw.isBlank()) {
            return;
        }
        String prefix = this.getConfig().getString("messages.prefix", "");
        to.sendMessage(Text.parse(prefix + (raw == null ? "<red>Message manquant : " + key : raw), resolvers));
    }

    public void sound(Player p, String key) {
        String s = this.getConfig().getString("sounds." + key, "");
        if (s != null && !s.isBlank()) {
            p.playSound(p.getLocation(), s, 1.0f, 1.0f);
        }
    }

    // ───────────── Construction d'items depuis la config ─────────────

    /**
     * Section : item (nexo:xxx ou MATERIAL), name, lore, amount, glow.
     * Si name est vide, on garde le nom de l'item Nexo.
     */
    public ItemStack buildItem(ConfigurationSection s, TagResolver... resolvers) {
        if (s == null) {
            ItemStack b = new ItemStack(org.bukkit.Material.BARRIER);
            ItemMeta m = b.getItemMeta();
            m.displayName(Component.text("Config manquante"));
            b.setItemMeta(m);
            return b;
        }
        ItemStack it = ItemSpec.parse(s.getString("item", "STONE")).build(this.nexo);
        it.setAmount(Math.max(1, Math.min(64, s.getInt("amount", 1))));
        ItemMeta meta = it.getItemMeta();
        String name = s.getString("name", "");
        if (!name.isEmpty()) {
            meta.displayName(Text.parse(name, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        List<String> lore = s.getStringList("lore");
        if (!lore.isEmpty()) {
            List<Component> out = new java.util.ArrayList<>();
            for (String l : lore) {
                out.add(Text.parse(l, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
            meta.lore(out);
        }
        if (s.getBoolean("glow", false)) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }
}
