package fr.aurel943.cowbrawl.listeners;

import fr.aurel943.cowbrawl.CowBrawl;
import fr.aurel943.cowbrawl.game.GameState;
import org.bukkit.Material;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

/**
 * Gère le bâton de knockback.
 *
 * Déclenché par l'attaque normale (clic gauche → EntityDamageByEntityEvent),
 * pas par un clic droit — c'est le comportement naturel attendu par un joueur.
 *
 * La vache étant invulnérable (setInvulnerable(true)), le knockback vanilla
 * lié à l'enchantement Knockback du bâton ne peut jamais s'appliquer (la
 * chaîne dégâts+knockback de Minecraft est court-circuitée dès qu'une entité
 * est invulnérable) — on pousse donc toujours manuellement via setVelocity(),
 * comme avant, juste déclenché par le bon événement cette fois.
 */
public class KnockbackListener implements Listener {

    private final CowBrawl plugin;

    // Force du knockback — ajustable si besoin
    private static final double FORCE_KNOCKBACK = 0.8;

    public KnockbackListener(CowBrawl plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (plugin.getGameManager().getEtat() != GameState.IN_GAME) return;

        if (!(event.getDamager() instanceof Player attaquant)) return;
        if (!plugin.getGameManager().getSession().estEnJeu(attaquant.getUniqueId())) return;
        if (attaquant.getInventory().getItemInMainHand().getType() != Material.STICK) return;

        // La cible touchée est presque toujours la vache montée par l'adversaire
        // (c'est elle qui a la hitbox), pas directement le joueur assis dessus.
        Player cible = null;
        if (event.getEntity() instanceof Player p) {
            cible = p;
        } else if (event.getEntity() instanceof Cow vacheTouchee) {
            for (var passager : vacheTouchee.getPassengers()) {
                if (passager instanceof Player p) {
                    cible = p;
                    break;
                }
            }
        }
        if (cible == null || cible.equals(attaquant)) return;
        if (!plugin.getGameManager().getSession().estEnJeu(cible.getUniqueId())) return;

        // Aucun dégât : juste la poussée
        event.setCancelled(true);

        Vector direction = cible.getLocation().toVector()
                .subtract(attaquant.getLocation().toVector())
                .normalize()
                .multiply(FORCE_KNOCKBACK);
        direction.setY(0.3);

        var vache = plugin.getGameManager().getSession().getVache(cible.getUniqueId());
        if (vache != null && !vache.isDead()) {
            vache.setVelocity(direction);
            plugin.getGameManager().marquerKnockback(cible.getUniqueId());
        } else {
            cible.setVelocity(direction);
        }

        attaquant.playSound(attaquant.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f);
    }
}