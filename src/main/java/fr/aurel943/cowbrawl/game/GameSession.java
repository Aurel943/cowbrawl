package fr.aurel943.cowbrawl.game;

import org.bukkit.entity.Cow;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Représente l'état complet d'une partie en cours.
 * Contient la liste des joueurs, leurs scores, et les vaches associées.
 *
 * Une seule session existe à la fois (le serveur CowBrawl n'a qu'une arène).
 */
public class GameSession {

    // Joueurs en attente dans le lobby (WAITING / STARTING)
    private final Set<UUID> joueursDansLobby = ConcurrentHashMap.newKeySet();

    // Joueurs actifs en jeu (IN_GAME)
    private final Set<UUID> joueursEnJeu = ConcurrentHashMap.newKeySet();

    // Score de chaque joueur (nombre de blocs collectés)
    private final Map<UUID, Integer> scores = new ConcurrentHashMap<>();

    // Vache associée à chaque joueur (pour nettoyage en fin de partie)
    private final Map<UUID, Cow> vaches = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------
    // Lobby
    // ---------------------------------------------------------------

    public void ajouterJoueurLobby(UUID uuid) {
        joueursDansLobby.add(uuid);
    }

    public void retirerJoueurLobby(UUID uuid) {
        joueursDansLobby.remove(uuid);
    }

    public Set<UUID> getJoueursDansLobby() {
        return Collections.unmodifiableSet(joueursDansLobby);
    }

    public int getNombreJoueursLobby() {
        return joueursDansLobby.size();
    }

    // ---------------------------------------------------------------
    // En jeu
    // ---------------------------------------------------------------

    public void transfererVersJeu() {
        joueursEnJeu.addAll(joueursDansLobby);
        joueursDansLobby.clear();
        for (UUID uuid : joueursEnJeu) {
            scores.put(uuid, 0);
        }
    }

    public void retirerJoueurJeu(UUID uuid) {
        joueursEnJeu.remove(uuid);
        scores.remove(uuid);
    }

    public Set<UUID> getJoueursEnJeu() {
        return Collections.unmodifiableSet(joueursEnJeu);
    }

    public int getNombreJoueursEnJeu() {
        return joueursEnJeu.size();
    }

    public boolean estEnJeu(UUID uuid) {
        return joueursEnJeu.contains(uuid);
    }

    // ---------------------------------------------------------------
    // Scores
    // ---------------------------------------------------------------

    public int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }

    public int incrementerScore(UUID uuid) {
        int nouveau = scores.getOrDefault(uuid, 0) + 1;
        scores.put(uuid, nouveau);
        return nouveau;
    }

    public Map<UUID, Integer> getScores() {
        return Collections.unmodifiableMap(scores);
    }

    // ---------------------------------------------------------------
    // Vaches
    // ---------------------------------------------------------------

    public void associerVache(UUID uuid, Cow vache) {
        vaches.put(uuid, vache);
    }

    public Cow getVache(UUID uuid) {
        return vaches.get(uuid);
    }

    public Map<UUID, Cow> getVaches() {
        return Collections.unmodifiableMap(vaches);
    }

    // ---------------------------------------------------------------
    // Réinitialisation
    // ---------------------------------------------------------------

    public void reset() {
        joueursDansLobby.clear();
        joueursEnJeu.clear();
        scores.clear();
        vaches.clear();
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    public boolean contientJoueur(UUID uuid) {
        return joueursDansLobby.contains(uuid) || joueursEnJeu.contains(uuid);
    }
}