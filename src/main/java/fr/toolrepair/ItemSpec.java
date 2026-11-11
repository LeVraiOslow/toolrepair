package fr.toolrepair;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Référence d'item configurable :
 *   "nexo:orbre"        -> item Nexo d'id "orbre"
 *   "minecraft:diamond" -> item vanilla
 *   "DIAMOND"           -> item vanilla
 */
public record ItemSpec(String nexoId, Material material, String raw) {

    public static ItemSpec parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ItemSpec(null, null, "");
        }
        String s = raw.trim();
        if (s.toLowerCase(Locale.ROOT).startsWith("nexo:")) {
            return new ItemSpec(s.substring(5), null, s);
        }
        if (s.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            s = s.substring(10);
        }
        return new ItemSpec(null, Material.matchMaterial(s), raw.trim());
    }

    public boolean valid() {
        return this.nexoId != null || this.material != null;
    }

    public boolean matches(ItemStack item, NexoHook nexo) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (this.nexoId != null) {
            return this.nexoId.equals(nexo.idOf(item));
        }
        return this.material != null && item.getType() == this.material && nexo.idOf(item) == null;
    }

    /** Construit l'item (quantité 1). Retourne une BARRIÈRE si la référence est invalide. */
    public ItemStack build(NexoHook nexo) {
        ItemStack it = null;
        if (this.nexoId != null) {
            it = nexo.build(this.nexoId);
        } else if (this.material != null) {
            it = new ItemStack(this.material);
        }
        return it != null ? it : new ItemStack(Material.BARRIER);
    }
}
