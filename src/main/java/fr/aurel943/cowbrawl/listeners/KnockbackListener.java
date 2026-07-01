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
        // Seulement en IN_GAME
        if (plugin.getGameManager().getEtat() != GameState.IN_GAME) return;

        // Seulement avec la main principale (évite le double déclenchement)
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player attaquant = event.getPlayer();

        // Vérifier que l'attaquant est en jeu
        if (!plugin.getGameManager().getSession().estEnJeu(attaquant.getUniqueId())) return;

        // Vérifier qu'il tient le bâton
        if (attaquant.getInventory().getItemInMainHand().getType() != Material.STICK) return;

        // Vérifier que la cible est un joueur en jeu
        if (!(event.getRightClicked() instanceof Player cible)) return;
        if (!plugin.getGameManager().getSession().estEnJeu(cible.getUniqueId())) return;

        event.setCancelled(true);

        // Calculer la direction de l'attaquant vers la cible
        Vector direction = cible.getLocation().toVector()
                .subtract(attaquant.getLocation().toVector())
                .normalize()
                .multiply(FORCE_KNOCKBACK);

        // Légère composante verticale pour un effet plus naturel
        direction.setY(0.3);

        // Appliquer le knockback à la vache (pas au joueur directement,
        // car le joueur est passager de la vache)
        var vache = plugin.getGameManager().getSession().getVache(cible.getUniqueId());
        if (vache != null && !vache.isDead()) {
            vache.setVelocity(direction);
        } else {
            // Fallback si la vache est introuvable
            cible.setVelocity(direction);
        }

        // Son de frappe
        attaquant.playSound(attaquant.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f);
    }
}