package fr.aurel943.cowbrawl.listeners;

import fr.aurel943.cowbrawl.CowBrawl;
import fr.aurel943.cowbrawl.game.GameState;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;

/**
 * Capture l'input brut (WASD/sprint) des joueurs montés sur une vache
 * pendant une partie CowBrawl, pour la piloter manuellement — une vache
 * n'étant pas un mount pilotable nativement (contrairement à un cochon
 * avec carotte-au-bâton).
 */
public class CowSteeringListener implements Listener {

    private final CowBrawl plugin;

    public CowSteeringListener(CowBrawl plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        if (plugin.getGameManager().getEtat() != GameState.IN_GAME) return;

        Player joueur = event.getPlayer();
        Cow vache = plugin.getGameManager().getSession().getVache(joueur.getUniqueId());
        if (vache == null || vache.isDead()) return;

        // Sécurité : le joueur doit être actuellement monté sur SA vache
        if (!vache.getPassengers().contains(joueur)) return;

        plugin.getGameManager().enregistrerInput(joueur.getUniqueId(), event.getInput());
    }
}