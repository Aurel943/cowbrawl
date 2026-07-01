package fr.aurel943.cowbrawl.listeners;

import fr.aurel943.cowbrawl.CowBrawl;
import fr.aurel943.cowbrawl.game.GameState;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

/**
 * Gère le bâton de knockback.
 *
 * En mode ADVENTURE, un clic gauche sur un joueur ne déclenche pas de vrai
 * combat — on utilise PlayerInteractEntityEvent (clic droit sur entité) pour
 * simuler la poussée. Le joueur cible reçoit un vecteur de knockback calculé
 * depuis la direction de l'attaquant.
 *
 * Aucun dégât n'est infligé (EntityDamageEvent est annulé dans ProtectionListener).
 */
public class KnockbackListener implements Listener {

    private final CowBrawl plugin;

    // Force du knockback — ajustable si besoin
    private static final double FORCE_KNOCKBACK = 0.8;

    public KnockbackListener(CowBrawl plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (plugin.getGameManager().getEtat() != GameState.IN_GAME) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player attaquant = event.getPlayer();
        if (!plugin.getGameManager().getSession().estEnJeu(attaquant.getUniqueId())) return;
        if (attaquant.getInventory().getItemInMainHand().getType() != Material.STICK) return;

        // La cible cliquée est presque toujours la VACHE montée par l'adversaire
        // (c'est elle qui a la hitbox visible/cliquable), pas directement le
        // joueur assis dessus — on gère donc les deux cas.
        Player cible = null;
        if (event.getRightClicked() instanceof Player p) {
            cible = p;
        } else if (event.getRightClicked() instanceof org.bukkit.entity.Cow vacheCliquee) {
            for (var passager : vacheCliquee.getPassengers()) {
                if (passager instanceof Player p) {
                    cible = p;
                    break;
                }
            }
        }
        if (cible == null || cible.equals(attaquant)) return;
        if (!plugin.getGameManager().getSession().estEnJeu(cible.getUniqueId())) return;

        event.setCancelled(true);

        Vector direction = cible.getLocation().toVector()
                .subtract(attaquant.getLocation().toVector())
                .normalize()
                .multiply(FORCE_KNOCKBACK);
        direction.setY(0.3);

        var vache = plugin.getGameManager().getSession().getVache(cible.getUniqueId());
        if (vache != null && !vache.isDead()) {
            vache.setVelocity(direction);
        } else {
            cible.setVelocity(direction);
        }

        attaquant.playSound(attaquant.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f);
    }
}