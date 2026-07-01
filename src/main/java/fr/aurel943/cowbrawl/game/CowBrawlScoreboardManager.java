package fr.aurel943.cowbrawl.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

/**
 * Scoreboard latéral affiché UNIQUEMENT pendant une partie (IN_GAME) :
 * classement des joueurs par nombre de blocs collectés.
 *
 * Suit le même principe que ScoreboardManager du Hub : une instance de
 * Scoreboard Bukkit par joueur, réécriture seulement si le contenu a
 * changé (évite tout scintillement).
 */
public class CowBrawlScoreboardManager {

    private static final String OBJECTIVE_ID = "cowbrawl_sidebar";
    private static final String TITRE = "&6&l★ CowBrawl ★";

    private final GameManager gameManager;
    private final Map<UUID, List<String>> dernierAffichage = new HashMap<>();

    public CowBrawlScoreboardManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void afficher(Player joueur) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(OBJECTIVE_ID, "dummy", versComponent(TITRE));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        joueur.setScoreboard(scoreboard);
        dernierAffichage.remove(joueur.getUniqueId());
    }

    public void retirer(Player joueur) {
        joueur.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        dernierAffichage.remove(joueur.getUniqueId());
    }

    /** Recalcule le classement et ne réécrit que chez les joueurs dont le contenu a changé. */
    public void mettreAJourTous(GameSession session) {
        int objectif = gameManager.getObjectifBlocs();

        List<Map.Entry<UUID, Integer>> classement = new ArrayList<>(session.getScores().entrySet());
        classement.sort((a, b) -> b.getValue() - a.getValue());

        List<String> lignes = new ArrayList<>();
        lignes.add("");
        lignes.add("&fObjectif: &6" + objectif + " blocs");
        lignes.add(" ");

        int rang = 1;
        for (Map.Entry<UUID, Integer> entree : classement) {
            Player joueur = Bukkit.getPlayer(entree.getKey());
            String nom = joueur != null ? joueur.getName() : "?";
            String couleur = switch (rang) {
                case 1 -> "&6";
                case 2 -> "&7";
                case 3 -> "&f";
                default -> "&7";
            };
            lignes.add(couleur + rang + ". &e" + nom + " &7— &a" + entree.getValue());
            rang++;
        }

        for (UUID uuid : session.getJoueursEnJeu()) {
            Player joueur = Bukkit.getPlayer(uuid);
            if (joueur != null) ecrireSiChange(joueur, lignes);
        }
    }

    private void ecrireSiChange(Player joueur, List<String> lignesBrutes) {
        Scoreboard scoreboard = joueur.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_ID);
        if (objective == null) return;

        List<String> lignesColorees = new ArrayList<>();
        for (String ligne : lignesBrutes) {
            lignesColorees.add(ChatColor.translateAlternateColorCodes('&', ligne));
        }

        List<String> precedentes = dernierAffichage.get(joueur.getUniqueId());
        if (precedentes != null && precedentes.equals(lignesColorees)) return;

        for (String entree : scoreboard.getEntries()) {
            scoreboard.resetScores(entree);
        }

        int score = lignesColorees.size();
        Set<String> utilisees = new HashSet<>();
        for (String ligne : lignesColorees) {
            String entree = rendreUnique(ligne, utilisees);
            utilisees.add(entree);
            objective.getScore(entree).setScore(score);
            score--;
        }

        dernierAffichage.put(joueur.getUniqueId(), lignesColorees);
    }

    private String rendreUnique(String ligne, Set<String> dejaUtilisees) {
        String candidate = ligne.isEmpty() ? " " : ligne;
        while (dejaUtilisees.contains(candidate)) {
            candidate = candidate + "§r";
        }
        return candidate;
    }

    public void clearOnQuit(Player joueur) {
        dernierAffichage.remove(joueur.getUniqueId());
    }

    private Component versComponent(String texte) {
        String legacy = ChatColor.translateAlternateColorCodes('&', texte);
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }
}