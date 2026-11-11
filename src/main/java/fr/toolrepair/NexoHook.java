package fr.toolrepair;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/** Accès à Nexo par réflexion (aucune dépendance à la compilation). */
public final class NexoHook {
    private boolean enabled;
    private Object target;
    private Method itemFromId;
    private Method idFromItem;

    public NexoHook(ToolRepair plugin) {
        if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
            plugin.getLogger().warning("Nexo introuvable : les items 'nexo:xxx' ne fonctionneront pas.");
            return;
        }
        try {
            Class<?> cls = Class.forName("com.nexomc.nexo.api.NexoItems");
            this.itemFromId = cls.getMethod("itemFromId", String.class);
            this.idFromItem = cls.getMethod("idFromItem", ItemStack.class);
            if (!Modifier.isStatic(this.itemFromId.getModifiers())) {
                this.target = cls.getField("INSTANCE").get(null);
            }
            this.enabled = true;
            plugin.getLogger().info("Nexo détecté : items 'nexo:xxx' activés.");
        } catch (Throwable t) {
            plugin.getLogger().warning("Nexo détecté mais API introuvable : " + t);
        }
    }

    public boolean enabled() {
        return this.enabled;
    }

    public ItemStack build(String nexoId) {
        if (!this.enabled || nexoId == null || nexoId.isBlank()) {
            return null;
        }
        try {
            Object builder = this.itemFromId.invoke(this.target, nexoId);
            if (builder == null) {
                return null;
            }
            return (ItemStack) builder.getClass().getMethod("build").invoke(builder);
        } catch (Throwable t) {
            return null;
        }
    }

    public String idOf(ItemStack item) {
        if (!this.enabled || item == null) {
            return null;
        }
        try {
            return (String) this.idFromItem.invoke(this.target, item);
        } catch (Throwable t) {
            return null;
        }
    }
}
