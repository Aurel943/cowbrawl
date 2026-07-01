package fr.aurel943.cowbrawl.listeners;

import fr.aurel943.cowbrawl.CowBrawl;
import fr.aurel943.cowbrawl.game.GameState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Gère l'arrivée et le départ des joueurs sur le serveur CowBrawl.
 *
 * À la connexion  → le joueur rejoint automatiquement le lobby.
 * À la déconnexion → le joueur est retiré de la session en cours,
 *                    et si c'était le dernier joueur en jeu après
 *                    la fin de partie, on déclenche le reset complet.
 */
public class PlayerJoinListener implements Listener {

    private final CowBrawl plugin;

    public PlayerJoinListener(CowBrawl plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Supprimer le message de connexion vanilla
        event.setJoinMessage(null);

        // Faire rejoindre le lobby automatiquement
        plugin.getGameManager().rejoindreLobby(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Supprimer le message de déconnexion vanilla
        event.setQuitMessage(null);

        plugin.getGameManager().quitterPartie(event.getPlayer());

        // Si la partie est en ENDING et qu'il ne reste plus personne
        // en jeu → reset complet pour la prochaine partie
        GameState etat = plugin.getGameManager().getEtat();
        if (etat == GameState.ENDING) {
            int restants = plugin.getGameManager().getSession().getNombreJoueursEnJeu();
            if (restants == 0) {
                plugin.getGameManager().reset();
            }
        }
    }
}