package fr.aurel943.cowbrawl.commands;

import fr.aurel943.cowbrawl.CowBrawl;
import fr.aurel943.cowbrawl.game.GameState;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * Commande /cowbrawl — réservée aux administrateurs (cowbrawl.admin).
 *
 * Sous-commandes :
 *   start  → force le lancement immédiat de la partie
 *   stop   → annule la partie en cours et reset
 *   status → affiche l'état actuel (joueurs, état, countdown)
 */
public class CowBrawlCommand implements CommandExecutor, TabCompleter {

    private final CowBrawl plugin;

    public CowBrawlCommand(CowBrawl plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cowbrawl.admin")) {
            sender.sendMessage(ChatColor.RED + "Tu n'as pas la permission d'utiliser cette commande.");
            return true;
        }

        if (args.length == 0) {
            envoyerAide(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "start" -> {
                GameState etat = plugin.getGameManager().getEtat();
                if (etat == GameState.IN_GAME) {
                    sender.sendMessage(ChatColor.RED + "Une partie est déjà en cours.");
                    return true;
                }
                if (etat == GameState.ENDING) {
                    sender.sendMessage(ChatColor.RED + "La partie se termine, attends le reset.");
                    return true;
                }
                int joueurs = plugin.getGameManager().getSession().getNombreJoueursLobby();
                int min = plugin.getConfig().getInt("game.min-joueurs", 2);
                if (joueurs < min) {
                    sender.sendMessage(ChatColor.RED + "Pas assez de joueurs (" + joueurs + "/" + min + ").");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "Lancement forcé de la partie.");
                plugin.getGameManager().lancerPartie();
            }

            case "stop" -> {
                GameState etat = plugin.getGameManager().getEtat();
                if (etat == GameState.WAITING) {
                    sender.sendMessage(ChatColor.RED + "Aucune partie en cours.");
                    return true;
                }
                sender.sendMessage(ChatColor.RED + "Partie arrêtée par un administrateur.");
                plugin.getGameManager().reset();
            }

            case "status" -> {
                GameState etat = plugin.getGameManager().getEtat();
                int lobby = plugin.getGameManager().getSession().getNombreJoueursLobby();
                int enJeu = plugin.getGameManager().getSession().getNombreJoueursEnJeu();
                int max = plugin.getConfig().getInt("game.max-joueurs", 8);

                sender.sendMessage(ChatColor.GOLD + "=== CowBrawl Status ===");
                sender.sendMessage(ChatColor.YELLOW + "État : " + ChatColor.WHITE + etat.name());
                sender.sendMessage(ChatColor.YELLOW + "Lobby : " + ChatColor.WHITE + lobby + "/" + max);
                sender.sendMessage(ChatColor.YELLOW + "En jeu : " + ChatColor.WHITE + enJeu);

                if (etat == GameState.IN_GAME) {
                    sender.sendMessage(ChatColor.YELLOW + "Scores :");
                    plugin.getGameManager().getSession().getScores().forEach((uuid, score) -> {
                        var joueur = plugin.getServer().getPlayer(uuid);
                        String nom = joueur != null ? joueur.getName() : uuid.toString();
                        sender.sendMessage(ChatColor.GRAY + "  " + nom + " : " + ChatColor.GOLD + score + " blocs");
                    });
                }
            }

            default -> envoyerAide(sender);
        }

        return true;
    }

    private void envoyerAide(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== CowBrawl Admin ===");
        sender.sendMessage(ChatColor.YELLOW + "/cowbrawl start " + ChatColor.GRAY + "→ Force le lancement");
        sender.sendMessage(ChatColor.YELLOW + "/cowbrawl stop  " + ChatColor.GRAY + "→ Arrête la partie");
        sender.sendMessage(ChatColor.YELLOW + "/cowbrawl status" + ChatColor.GRAY + "→ Affiche l'état");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cowbrawl.admin")) return List.of();
        if (args.length == 1) {
            return List.of("start", "stop", "status").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}