package fr.toolrepair;

import fr.customtools.ToolDefinition;
import fr.customtools.ToolManager;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class RepairMenu implements Listener {

    /** Une instance par menu ouvert : porte l'état "payé" (argent en séquestre). */
    public static final class Holder implements InventoryHolder {
        Inventory inventory;
        Player player;
        boolean paid;
        double paidAmount;

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }

    private record State(ToolDefinition def, ItemStack tool, boolean damaged, boolean hasOrb, int orbHave, double price, int current, int max) {
        boolean toolOk(boolean requireDamaged) {
            return this.def != null && (this.damaged || !requireDamaged);
        }
    }

    private enum Kind { TOOL, ORB }

    private final ToolRepair plugin;

    public RepairMenu(ToolRepair plugin) {
        this.plugin = plugin;
    }

    // ───────────── Config ─────────────

    private int toolSlot() {
        return this.plugin.getConfig().getInt("menu.slots.tool", 11);
    }

    private int orbSlot() {
        return this.plugin.getConfig().getInt("menu.slots.orb", 15);
    }

    private int rows() {
        return Math.max(1, Math.min(6, this.plugin.getConfig().getInt("menu.rows", 3)));
    }

    private boolean requireDamaged() {
        return this.plugin.getConfig().getBoolean("require-damaged", true);
    }

    private ClickType clickOf(String button) {
        try {
            return ClickType.valueOf(this.plugin.getConfig().getString("menu.buttons." + button + ".click", "LEFT").toUpperCase());
        } catch (IllegalArgumentException e) {
            return ClickType.LEFT;
        }
    }

    // ───────────── Ouverture ─────────────

    public void open(Player p) {
        if (this.plugin.economy() == null) {
            this.plugin.msg(p, "no-economy");
            return;
        }
        int rows = this.rows();
        int size = rows * 9;
        int tool = this.toolSlot();
        int orb = this.orbSlot();
        int priceSlot = this.plugin.getConfig().getInt("menu.buttons.price.slot", 13);
        int confirmSlot = this.plugin.getConfig().getInt("menu.buttons.confirm.slot", 22);
        for (int s : new int[]{tool, orb, priceSlot, confirmSlot}) {
            if (s < 0 || s >= size) {
                this.plugin.getLogger().warning("Slot " + s + " hors du menu (" + size + " slots). Corrige menu.rows / les slots dans config.yml.");
                this.plugin.msg(p, "config-error");
                return;
            }
        }
        Holder h = new Holder();
        h.player = p;
        Inventory inv = Bukkit.createInventory(h, size, Text.parse(this.plugin.getConfig().getString("menu.title", "&8Réparation")));
        h.inventory = inv;

        ConfigurationSection filler = this.plugin.getConfig().getConfigurationSection("menu.filler");
        if (filler != null && filler.getBoolean("enabled", true)) {
            ItemStack f = this.plugin.buildItem(filler);
            for (int i = 0; i < size; i++) {
                inv.setItem(i, f.clone());
            }
        }
        ConfigurationSection deco = this.plugin.getConfig().getConfigurationSection("menu.decorations");
        if (deco != null) {
            for (String key : deco.getKeys(false)) {
                ConfigurationSection d = deco.getConfigurationSection(key);
                if (d == null) {
                    continue;
                }
                ItemStack it = this.plugin.buildItem(d);
                for (int s : d.getIntegerList("slots")) {
                    if (s >= 0 && s < size) {
                        inv.setItem(s, it.clone());
                    }
                }
            }
        }
        inv.setItem(tool, null);
        inv.setItem(orb, null);
        this.refresh(h);
        p.openInventory(inv);
        this.plugin.sound(p, "open");
    }

    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                p.closeInventory();
            }
        }
    }

    // ───────────── État / affichage ─────────────

    private State state(Holder h) {
        ToolManager tm = this.plugin.tools();
        ItemStack tool = h.inventory.getItem(this.toolSlot());
        ToolDefinition def = null;
        boolean damaged = false;
        int cur = 0;
        int max = 0;
        double price = 0;
        if (tool != null && !tool.getType().isAir()) {
            def = tm.fromItem(tool);
            if (!this.plugin.isRepairable(def)) {
                def = null;
            }
        }
        if (def != null) {
            cur = tm.remaining(tool, def);
            max = tm.maxDurability(tool, def);
            damaged = cur < max;
            price = this.price(def, cur, max);
        }
        ItemStack orb = h.inventory.getItem(this.orbSlot());
        boolean orbOk = this.plugin.orbSpec().matches(orb, this.plugin.nexo());
        int have = orbOk ? orb.getAmount() : 0;
        return new State(def, tool, damaged, orbOk && have >= this.plugin.orbAmount(), have, price, cur, max);
    }

    private double price(ToolDefinition def, int cur, int max) {
        var cfg = this.plugin.getConfig();
        double base = cfg.getDouble("price.per-tool." + def.id(), cfg.getDouble("price.default", 0));
        if (cfg.getBoolean("price.scale-with-damage", false) && max > 0) {
            base = base * ((double) (max - cur) / (double) max);
        }
        base = Math.max(base, cfg.getDouble("price.minimum", 0));
        return Math.round(base * 100.0) / 100.0;
    }

    private String money(double v) {
        Economy eco = this.plugin.economy();
        return eco != null ? eco.format(v) : String.valueOf(v);
    }

    private void refresh(Holder h) {
        State s = this.state(h);
        Economy eco = this.plugin.economy();
        Component toolName = Component.text("-");
        if (s.def() != null) {
            var meta = s.tool().getItemMeta();
            toolName = meta.hasDisplayName() ? meta.displayName() : Component.text(s.def().id());
        }
        TagResolver[] r = new TagResolver[]{
            Placeholder.unparsed("price", this.money(s.price())),
            Placeholder.unparsed("price_raw", String.valueOf(s.price())),
            Placeholder.unparsed("balance", eco != null ? this.money(eco.getBalance(h.player)) : "?"),
            Placeholder.component("tool", toolName),
            Placeholder.unparsed("current", String.valueOf(s.current())),
            Placeholder.unparsed("max", String.valueOf(s.max())),
            Placeholder.unparsed("orb_amount", String.valueOf(this.plugin.orbAmount())),
            Placeholder.unparsed("orb_have", String.valueOf(s.orbHave())),
            Placeholder.unparsed("paid", this.money(h.paidAmount))
        };
        boolean toolOk = s.toolOk(this.requireDamaged());

        String priceState = s.def() == null ? "no-tool" : (!toolOk ? "not-damaged" : (h.paid ? "paid" : "unpaid"));
        this.place(h, "price", priceState, r);

        boolean ready = toolOk && s.hasOrb() && h.paid;
        this.place(h, "confirm", ready ? "ready" : "not-ready", r);
    }

    private void place(Holder h, String button, String state, TagResolver[] r) {
        String base = "menu.buttons." + button;
        int slot = this.plugin.getConfig().getInt(base + ".slot", -1);
        if (slot < 0 || slot >= h.inventory.getSize()) {
            return;
        }
        h.inventory.setItem(slot, this.plugin.buildItem(this.plugin.getConfig().getConfigurationSection(base + ".states." + state), r));
    }

    // ───────────── Clics ─────────────

    private boolean accepts(Kind kind, ItemStack it) {
        if (it == null || it.getType().isAir()) {
            return true;
        }
        if (kind == Kind.TOOL) {
            return this.plugin.isRepairable(this.plugin.tools().fromItem(it));
        }
        return this.plugin.orbSpec().matches(it, this.plugin.nexo());
    }

    private boolean denyIfInvalid(InventoryClickEvent e, Player p, Kind kind, ItemStack moving) {
        if (!this.accepts(kind, moving)) {
            e.setCancelled(true);
            this.plugin.msg(p, kind == Kind.TOOL ? "wrong-tool" : "wrong-orb");
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h) || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (e.getClick() == ClickType.DOUBLE_CLICK || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
            return;
        }
        int raw = e.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();

        if (!inTop) {
            if (e.isShiftClick()) {
                e.setCancelled(true);
                this.quickMove(p, h, e);
            }
            this.later(h);
            return;
        }

        int tool = this.toolSlot();
        int orb = this.orbSlot();
        if (raw == tool || raw == orb) {
            Kind kind = raw == tool ? Kind.TOOL : Kind.ORB;
            ClickType click = e.getClick();
            if (click == ClickType.NUMBER_KEY) {
                int btn = e.getHotbarButton();
                ItemStack moving = btn >= 0 ? p.getInventory().getItem(btn) : null;
                if (this.denyIfInvalid(e, p, kind, moving)) {
                    return;
                }
            } else if (click == ClickType.SWAP_OFFHAND) {
                if (this.denyIfInvalid(e, p, kind, p.getInventory().getItemInOffHand())) {
                    return;
                }
            } else {
                InventoryAction a = e.getAction();
                if (a == InventoryAction.PLACE_ALL || a == InventoryAction.PLACE_ONE || a == InventoryAction.PLACE_SOME || a == InventoryAction.SWAP_WITH_CURSOR) {
                    if (this.denyIfInvalid(e, p, kind, e.getCursor())) {
                        return;
                    }
                }
            }
            this.later(h);
            return;
        }

        // Tout le reste du menu est verrouillé
        e.setCancelled(true);
        if (e.getClick() == this.clickOf("price") && raw == this.plugin.getConfig().getInt("menu.buttons.price.slot", 13)) {
            this.pressPrice(p, h);
        } else if (e.getClick() == this.clickOf("confirm") && raw == this.plugin.getConfig().getInt("menu.buttons.confirm.slot", 22)) {
            this.pressConfirm(p, h);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h) || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        int tool = this.toolSlot();
        int orb = this.orbSlot();
        for (Map.Entry<Integer, ItemStack> en : e.getNewItems().entrySet()) {
            int raw = en.getKey();
            if (raw >= top.getSize()) {
                continue;
            }
            if (raw != tool && raw != orb) {
                e.setCancelled(true);
                return;
            }
            if (!this.accepts(raw == tool ? Kind.TOOL : Kind.ORB, en.getValue())) {
                e.setCancelled(true);
                this.plugin.msg(p, raw == tool ? "wrong-tool" : "wrong-orb");
                return;
            }
        }
        this.later(h);
    }

    /** Shift-clic depuis l'inventaire du joueur : on route l'item vers le bon slot. */
    private void quickMove(Player p, Holder h, InventoryClickEvent e) {
        ItemStack src = e.getCurrentItem();
        if (src == null || src.getType().isAir()) {
            return;
        }
        Inventory top = h.inventory;
        int tool = this.toolSlot();
        int orb = this.orbSlot();
        if (this.accepts(Kind.TOOL, src) && this.plugin.isRepairable(this.plugin.tools().fromItem(src))) {
            ItemStack cur = top.getItem(tool);
            if (cur == null || cur.getType().isAir()) {
                top.setItem(tool, src.clone());
                e.getClickedInventory().setItem(e.getSlot(), null);
            }
            return;
        }
        if (this.plugin.orbSpec().matches(src, this.plugin.nexo())) {
            ItemStack cur = top.getItem(orb);
            if (cur == null || cur.getType().isAir()) {
                top.setItem(orb, src.clone());
                e.getClickedInventory().setItem(e.getSlot(), null);
            } else if (cur.isSimilar(src)) {
                int free = cur.getMaxStackSize() - cur.getAmount();
                int moved = Math.min(free, src.getAmount());
                if (moved > 0) {
                    cur.setAmount(cur.getAmount() + moved);
                    top.setItem(orb, cur);
                    src.setAmount(src.getAmount() - moved);
                    e.getClickedInventory().setItem(e.getSlot(), src.getAmount() <= 0 ? null : src);
                }
            }
            return;
        }
        this.plugin.msg(p, "wrong-item");
    }

    private void later(Holder h) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (h.player.getOpenInventory().getTopInventory().getHolder() == h) {
                this.refresh(h);
            }
        });
    }

    // ───────────── Boutons ─────────────

    private void pressPrice(Player p, Holder h) {
        State s = this.state(h);
        if (s.def() == null) {
            this.plugin.msg(p, "no-tool");
            this.plugin.sound(p, "error");
            return;
        }
        if (!s.toolOk(this.requireDamaged())) {
            this.plugin.msg(p, "tool-full");
            this.plugin.sound(p, "error");
            return;
        }
        if (h.paid) {
            this.plugin.msg(p, "already-paid");
            this.plugin.sound(p, "error");
            return;
        }
        Economy eco = this.plugin.economy();
        if (eco == null) {
            this.plugin.msg(p, "no-economy");
            return;
        }
        double price = s.price();
        if (price > 0) {
            if (!eco.has(p, price)) {
                this.plugin.msg(p, "not-enough-money", Placeholder.unparsed("price", this.money(price)), Placeholder.unparsed("balance", this.money(eco.getBalance(p))));
                this.plugin.sound(p, "error");
                return;
            }
            EconomyResponse resp = eco.withdrawPlayer(p, price);
            if (!resp.transactionSuccess()) {
                this.plugin.msg(p, "not-enough-money", Placeholder.unparsed("price", this.money(price)), Placeholder.unparsed("balance", this.money(eco.getBalance(p))));
                this.plugin.sound(p, "error");
                return;
            }
        }
        h.paid = true;
        h.paidAmount = price;
        this.plugin.msg(p, "paid", Placeholder.unparsed("price", this.money(price)));
        this.plugin.sound(p, "pay");
        this.refresh(h);
    }

    private void pressConfirm(Player p, Holder h) {
        State s = this.state(h);
        if (s.def() == null) {
            this.plugin.msg(p, "no-tool");
            this.plugin.sound(p, "error");
            return;
        }
        if (!s.toolOk(this.requireDamaged())) {
            this.plugin.msg(p, "tool-full");
            this.plugin.sound(p, "error");
            return;
        }
        if (!s.hasOrb()) {
            this.plugin.msg(p, "orb-missing", Placeholder.unparsed("orb_amount", String.valueOf(this.plugin.orbAmount())));
            this.plugin.sound(p, "error");
            return;
        }
        if (!h.paid) {
            this.plugin.msg(p, "not-paid");
            this.plugin.sound(p, "error");
            return;
        }
        if (Math.abs(h.paidAmount - s.price()) > 0.001) {
            // L'outil a changé depuis le paiement : on rembourse, le joueur doit repayer.
            this.refund(h);
            this.plugin.msg(p, "tool-changed");
            this.plugin.sound(p, "error");
            this.refresh(h);
            return;
        }

        ToolManager tm = this.plugin.tools();
        Inventory inv = h.inventory;
        ItemStack tool = s.tool();
        Component name = tool.getItemMeta().hasDisplayName() ? tool.getItemMeta().displayName() : Component.text(s.def().id());

        tm.repair(tool, s.def());

        ItemStack orb = inv.getItem(this.orbSlot());
        int left = orb.getAmount() - this.plugin.orbAmount();
        if (left <= 0) {
            inv.setItem(this.orbSlot(), null);
        } else {
            orb.setAmount(left);
            inv.setItem(this.orbSlot(), orb);
        }

        // L'argent en séquestre est consommé définitivement
        h.paid = false;
        h.paidAmount = 0;

        if (this.plugin.getConfig().getBoolean("on-success.give-to-inventory", true)) {
            inv.setItem(this.toolSlot(), null);
            this.give(p, tool);
        } else {
            inv.setItem(this.toolSlot(), tool);
        }
        this.plugin.msg(p, "success", Placeholder.component("tool", name));
        this.plugin.sound(p, "success");

        if (this.plugin.getConfig().getBoolean("on-success.close-menu", true)) {
            p.closeInventory();
        } else {
            this.refresh(h);
        }
    }

    // ───────────── Fermeture / remboursement ─────────────

    private void refund(Holder h) {
        if (h.paid && h.paidAmount > 0) {
            Economy eco = this.plugin.economy();
            if (eco != null) {
                eco.depositPlayer(h.player, h.paidAmount);
                this.plugin.msg(h.player, "refunded", Placeholder.unparsed("price", this.money(h.paidAmount)));
            } else {
                this.plugin.getLogger().severe("Impossible de rembourser " + h.paidAmount + " à " + h.player.getName() + " (économie indisponible).");
            }
        }
        h.paid = false;
        h.paidAmount = 0;
    }

    private void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h) || !(e.getPlayer() instanceof Player p)) {
            return;
        }
        this.refund(h);
        for (int slot : new int[]{this.toolSlot(), this.orbSlot()}) {
            ItemStack it = h.inventory.getItem(slot);
            if (it != null && !it.getType().isAir()) {
                this.give(p, it);
                h.inventory.setItem(slot, null);
            }
        }
    }
}
