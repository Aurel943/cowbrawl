package fr.aurel943.cowbrawl.listeners;

import fr.aurel943.cowbrawl.CowBrawl;
import fr.aurel943.cowbrawl.game.GameState;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Annule tous les effets indésirables pendant le lobby et la partie :
 * dégâts, faim, casse de blocs, pose de blocs, drop d'items, clic inventaire.
 *
 * Le bâton de knockback est géré dans KnockbackListener — ici on annule
 * uniquement les dégâts directs (chute, contact, explosion, etc.).
 */
public class ProtectionListener implements Listener {

    private final CowBrawl plugin;

    public ProtectionListener(CowBrawl plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------
    // Dégâts — annulés pour tous les joueurs CowBrawl
    // ---------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player joueur)) return;
        if (!plugin.getGameManager().getSession().contientJoueur(joueur.getUniqueId())) return;
        event.setCancelled(true);
    }

    // ---------------------------------------------------------------
    // Faim — bloquée en permanence
    // ---------------------------------------------------------------

    @EventHandler
    public void onFaim(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player joueur)) return;
        if (!plugin.getGameManager().getSession().contientJoueur(joueur.getUniqueId())) return;
        event.setCancelled(true);
    }

    // ---------------------------------------------------------------
    // Blocs — pas de casse ni de pose
    // ---------------------------------------------------------------

    @EventHandler
    public void onCasserBloc(BlockBreakEvent event) {
        if (!plugin.getGameManager().getSession().contientJoueur(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPoserBloc(BlockPlaceEvent event) {
        if (!plugin.getGameManager().getSession().contientJoueur(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    // ---------------------------------------------------------------
    // Drop d'items — interdit (le bâton ne peut pas être jeté)
    // ---------------------------------------------------------------

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.getGameManager().getSession().contientJoueur(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    // ---------------------------------------------------------------
    // Inventaire — pas de déplacement d'items
    // ---------------------------------------------------------------

    @EventHandler
    public void onInventaire(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player joueur)) return;
        if (!plugin.getGameManager().getSession().contientJoueur(joueur.getUniqueId())) return;
        event.setCancelled(true);
    }

    // ---------------------------------------------------------------
    // Interaction — gérer le clic sur le lit de retour
    // ---------------------------------------------------------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player joueur = event.getPlayer();
        if (!plugin.getGameManager().getSession().contientJoueur(joueur.getUniqueId())) return;

        // Clic droit sur le lit (slot 8) → retour au Hub
        if (event.getItem() != null && event.getItem().getType() == Material.RED_BED) {
            event.setCancelled(true);
            // Seulement si la partie est terminée (ENDING) ou en lobby
            GameState etat = plugin.getGameManager().getEtat();
            if (etat == GameState.ENDING || etat == GameState.WAITING || etat == GameState.STARTING) {
                String nomHub = plugin.getConfig().getString("hub.nom-serveur", "hub");
                plugin.getGameManager().envoyerVersHub(joueur, nomHub);
            }
        }
    }
}