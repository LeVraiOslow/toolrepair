# ToolRepair (Paper 26.2 / Java 25)

Menu de réparation complète des outils **CustomTools** : outil + orbe Nexo (`nexo:orbre`) + argent Vault.

## Installation
1. **Rebuild CustomTools** avec le patch fourni (2 petites méthodes publiques ajoutées : `CustomTools#tools()` et `ToolManager#maxDurability`).
2. Copie le jar de CustomTools patché dans `libs/CustomTools.jar`.
3. `gradle build` -> `build/libs/ToolRepair.jar` dans `plugins/`.
4. Prérequis serveur : CustomTools (patché), Vault + un plugin d'économie, Nexo.

## Utilisation
- `/toolrepair` (alias `/reparation`) ouvre le menu. `/toolrepair reload` recharge la config.
- Permissions : `toolrepair.use` (défaut : tous), `toolrepair.admin` (défaut : op).
- Dépose l'outil et l'orbe, clic gauche sur le prix (l'argent est pris), clic gauche sur « Valider ».
- Fermer le menu avant de valider = argent remboursé + items rendus.

Tout (titre, rows, slots, items, noms, lores, prix, messages, sons) est dans `config.yml`.
