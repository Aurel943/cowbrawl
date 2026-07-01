package fr.aurel943.cowbrawl.game;

import fr.aurel943.cowbrawl.CowBrawl;
import fr.aurel943.cowbrawl.database.CowBrawlDatabase;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import java.util.*;
import java.util.logging.Logger;

/**
 * Gère le cycle de vie complet d'une partie CowBrawl :
 * lobby → countdown → in_game → ending → reset.
 *
 * Une seule instance existe (un seul serveur = une seule arène).
 */
public class GameManager {

    private final CowBrawl plugin;
    private final Logger logger;
    private final CowBrawlDatabase database;

    private GameState etat = GameState.WAITING;
    private final GameSession session = new GameSession();

    // Tâches planifiées actives (à annuler lors du reset)
    private BukkitTask tacheCountdown = null;
    private BukkitTask tacheBlocs = null;
    private BukkitTask tacheDetection = null;
    private BukkitTask tacheFin = null;
    private BukkitTask tacheDeplacement = null;
    private final Map<UUID, org.bukkit.Input> derniersInputs = new ConcurrentHashMap<>();
    private final CowBrawlScoreboardManager scoreboardManager = new CowBrawlScoreboardManager(this);

    // Valeur courante du countdown (en secondes)
    private int secondesRestantes = 0;

    // Spawns de l'arène chargés depuis config.yml
    private final List<Location> spawnsArene = new ArrayList<>();
    private Location spawnLobby = null;

    // Monde de jeu
    private World monde = null;

    public GameManager(CowBrawl plugin, CowBrawlDatabase database) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.database = database;
        chargerConfig();
    }

    // ---------------------------------------------------------------
    // Chargement de la configuration
    // ---------------------------------------------------------------

    private void chargerConfig() {
        var config = plugin.getConfig();

        String nomMonde = config.getString("arena.monde", "cowbrawl_world");
        monde = Bukkit.getWorld(nomMonde);
        if (monde == null) {
            logger.warning("Monde '" + nomMonde + "' introuvable — vérifie que le monde est bien généré.");
        }

        // Spawn lobby
        if (monde != null) {
            ConfigurationSection ls = config.getConfigurationSection("arena.lobby-spawn");
            if (ls != null) {
                spawnLobby = new Location(monde,
                        ls.getDouble("x"), ls.getDouble("y"), ls.getDouble("z"),
                        (float) ls.getDouble("yaw"), (float) ls.getDouble("pitch"));
            }
        }

        // Spawns arène
        spawnsArene.clear();
        List<Map<?, ?>> spawns = config.getMapList("arena.spawns");
        if (monde != null) {
            for (Map<?, ?> s : spawns) {
                spawnsArene.add(new Location(monde,
                        toDouble(s.get("x")), toDouble(s.get("y")), toDouble(s.get("z")),
                        toFloat(s.get("yaw")), toFloat(s.get("pitch"))));
            }
        }

        logger.info("GameManager chargé — " + spawnsArene.size() + " spawns arène, lobby: " + (spawnLobby != null));
    }

    private double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private float toFloat(Object o) {
        if (o instanceof Number n) return n.floatValue();
        return 0.0f;
    }

    // ---------------------------------------------------------------
    // Rejoindre / quitter le lobby
    // ---------------------------------------------------------------

    /**
     * Fait rejoindre un joueur au lobby.
     * Appelé depuis PlayerJoinListener quand un joueur arrive sur le serveur.
     */
    public void rejoindreLobby(Player joueur) {
        if (etat == GameState.IN_GAME || etat == GameState.ENDING) {
            joueur.sendMessage(getMessage("prefix") + getMessage("lobby.partie-en-cours"));
            return;
        }

        int max = plugin.getConfig().getInt("game.max-joueurs", 8);
        if (session.getNombreJoueursLobby() >= max) {
            joueur.sendMessage(getMessage("prefix") + getMessage("lobby.lobby-plein"));
            return;
        }

        session.ajouterJoueurLobby(joueur.getUniqueId());

        // Téléporter au lobby
        if (spawnLobby != null) {
            joueur.teleport(spawnLobby);
        }

        // Préparer l'inventaire du lobby (vide, mode aventure)
        preparerInventaireLobby(joueur);

        int nb = session.getNombreJoueursLobby();
        // Annoncer à tous
        diffuserLobby(getMessage("lobby.joueur-rejoint")
                .replace("{joueur}", joueur.getName())
                .replace("{joueurs}", String.valueOf(nb))
                .replace("{max}", String.valueOf(max)));

        // Vérifier si on doit démarrer / ajuster le countdown
        verifierConditionsLancement();
    }

    /**
     * Retire un joueur du lobby ou de la partie en cours.
     * Appelé depuis PlayerQuitListener.
     */
    public void quitterPartie(Player joueur) {
        UUID uuid = joueur.getUniqueId();

        if (session.estEnJeu(uuid)) {
            // Joueur qui quitte en pleine partie
            supprimerVache(uuid);
            session.retirerJoueurJeu(uuid);
            diffuserJeu(getMessage("lobby.joueur-parti")
                    .replace("{joueur}", joueur.getName())
                    .replace("{joueurs}", String.valueOf(session.getNombreJoueursEnJeu()))
                    .replace("{max}", String.valueOf(plugin.getConfig().getInt("game.max-joueurs", 8))));

            // Si plus personne en jeu, on annule la partie
            if (session.getNombreJoueursEnJeu() == 0) {
                annulerPartie();
            }
            return;
        }

        if (session.getJoueursDansLobby().contains(uuid)) {
            session.retirerJoueurLobby(uuid);
            int nb = session.getNombreJoueursLobby();
            int max = plugin.getConfig().getInt("game.max-joueurs", 8);
            diffuserLobby(getMessage("lobby.joueur-parti")
                    .replace("{joueur}", joueur.getName())
                    .replace("{joueurs}", String.valueOf(nb))
                    .replace("{max}", String.valueOf(max)));

            verifierConditionsLancement();
        }
    }

    // ---------------------------------------------------------------
    // Logique de countdown
    // ---------------------------------------------------------------

    private void verifierConditionsLancement() {
        int nb = session.getNombreJoueursLobby();
        int min = plugin.getConfig().getInt("game.min-joueurs", 2);
        int seuil = plugin.getConfig().getInt("game.seuil-joueurs-rapide", 4);
        int countdownRapide = plugin.getConfig().getInt("game.countdown-rapide-secondes", 20);
        int countdownLent = plugin.getConfig().getInt("game.countdown-lent-secondes", 60);

        if (nb < min) {
            // Pas assez de joueurs — on annule le countdown si actif
            if (etat == GameState.STARTING) {
                annulerCountdown();
                diffuserLobby(getMessage("lobby.countdown-reset"));
                etat = GameState.WAITING;
            }
            return;
        }

        if (nb >= seuil) {
            // Seuil rapide atteint
            if (etat == GameState.WAITING) {
                // Pas encore de countdown → démarrer le rapide
                demarrerCountdown(countdownRapide);
            } else if (etat == GameState.STARTING && secondesRestantes > countdownRapide) {
                // Countdown lent en cours → raccourcir au rapide
                annulerCountdown();
                demarrerCountdown(countdownRapide);
            }
        } else {
            // Entre min et seuil → countdown lent
            if (etat == GameState.WAITING) {
                demarrerCountdown(countdownLent);
            }
        }
    }

    private void demarrerCountdown(int secondes) {
        etat = GameState.STARTING;
        secondesRestantes = secondes;
        final int secondesTotal = secondes;

        tacheCountdown = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (secondesRestantes <= 0) {
                annulerCountdown();
                lancerPartie();
                return;
            }

            if (secondesRestantes <= 5 || secondesRestantes == 10 || secondesRestantes == 30 || secondesRestantes == 60) {
                diffuserLobby(getMessage("lobby.countdown")
                        .replace("{temps}", String.valueOf(secondesRestantes)));
            }

            float progression = secondesTotal > 0 ? (float) secondesRestantes / secondesTotal : 0f;
            for (UUID uuid : session.getJoueursDansLobby()) {
                Player joueur = Bukkit.getPlayer(uuid);
                if (joueur == null) continue;
                joueur.setLevel(secondesRestantes);
                joueur.setExp(Math.max(0f, Math.min(1f, progression)));
            }

            secondesRestantes--;
        }, 0L, 20L);
    }

    private void annulerCountdown() {
        if (tacheCountdown != null) {
            tacheCountdown.cancel();
            tacheCountdown = null;
        }
        secondesRestantes = 0;
        for (UUID uuid : session.getJoueursDansLobby()) {
            Player joueur = Bukkit.getPlayer(uuid);
            if (joueur != null) {
                joueur.setLevel(0);
                joueur.setExp(0f);
            }
        }
    }

    // ---------------------------------------------------------------
    // Lancement de la partie
    // ---------------------------------------------------------------

    /**
     * Lance la partie : téléporte les joueurs dans l'arène, spawn les vaches,
     * démarre le spawner de blocs et la détection de collecte.
     */
    public void lancerPartie() {
        if (etat == GameState.IN_GAME) return;

        int nb = session.getNombreJoueursLobby();
        int min = plugin.getConfig().getInt("game.min-joueurs", 2);
        if (nb < min) {
            diffuserLobby(getMessage("lobby.pas-assez-joueurs")
                    .replace("{min}", String.valueOf(min)));
            etat = GameState.WAITING;
            return;
        }

        etat = GameState.IN_GAME;
        session.transfererVersJeu();

        int objectif = plugin.getConfig().getInt("game.blocs-pour-gagner", 15);

        // Téléporter chaque joueur sur un spawn et spawner sa vache
        List<UUID> joueursUuids = new ArrayList<>(session.getJoueursEnJeu());
        Collections.shuffle(joueursUuids); // ordre aléatoire pour les spawns

        for (int i = 0; i < joueursUuids.size(); i++) {
            UUID uuid = joueursUuids.get(i);
            Player joueur = Bukkit.getPlayer(uuid);
            if (joueur == null) continue;

            Location spawn = spawnsArene.get(i % spawnsArene.size());
            joueur.teleport(spawn);
            preparerInventaireJeu(joueur);

            // Spawner la vache et monter le joueur dessus
            Cow vache = spawn.getWorld().spawn(spawn, Cow.class, c -> {
                // IA désactivée : on pilote entièrement la vache à la main via
                // deplacerVaches() (tacheDeplacement) — voir CowSteeringListener pour
                // la capture de l'input joueur (WASD/sprint).
                c.setAI(false);
                c.setInvulnerable(true);
                c.setSilent(false);
                c.setCustomNameVisible(false);
            });
            vache.addPassenger(joueur);
            session.associerVache(uuid, vache);

            joueur.sendMessage(getMessage("prefix") + getMessage("game.debut")
                    .replace("{objectif}", String.valueOf(objectif)));

            scoreboardManager.afficher(joueur);
        }

        // Démarrer le spawner de blocs
        plugin.getBlockSpawner().demarrer();

        // Démarrer la détection de collecte (toutes les 10 ticks = 0.5s)
        double rayon = plugin.getConfig().getDouble("game.bloc-detection-radius", 1.5);
        tacheDetection = Bukkit.getScheduler().runTaskTimer(plugin, () ->
                plugin.getBlockSpawner().verifierCollectes(this, rayon), 0L, 10L);

        // Démarrer le déplacement des vaches (toutes les ticks, pour rester fluide)
        tacheDeplacement = Bukkit.getScheduler().runTaskTimer(plugin, this::deplacerVaches, 0L, 1L);

        scoreboardManager.mettreAJourTous(session);
    }

    /**
     * Applique à chaque tick la vélocité horizontale de chaque vache en
     * fonction du dernier input reçu de son passager (voir CowSteeringListener)
     * et de la direction où il regarde (son yaw). La composante Y (gravité)
     * est toujours préservée, jamais écrasée.
     */
    private void deplacerVaches() {
        if (etat != GameState.IN_GAME) return;

        double vitesseBase = plugin.getConfig().getDouble("game.vache-vitesse", 0.35);
        double multSprint = plugin.getConfig().getDouble("game.vache-vitesse-sprint-multiplicateur", 1.6);

        for (Map.Entry<UUID, Cow> entree : session.getVaches().entrySet()) {
            UUID uuid = entree.getKey();
            Cow vache = entree.getValue();
            if (vache == null || vache.isDead()) continue;

            Player joueur = Bukkit.getPlayer(uuid);
            if (joueur == null || !joueur.isOnline()) continue;

            org.bukkit.Input input = derniersInputs.get(uuid);
            if (input == null || (!input.isForward() && !input.isBackward()
                    && !input.isLeft() && !input.isRight())) {
                // Pas d'input directionnel : on stoppe le mouvement horizontal
                // sans toucher à la composante Y (chute).
                org.bukkit.util.Vector actuelle = vache.getVelocity();
                vache.setVelocity(new org.bukkit.util.Vector(0, actuelle.getY(), 0));
                continue;
            }

            double avant = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
            double cote = (input.isLeft() ? 1 : 0) - (input.isRight() ? 1 : 0);

            double yawRad = Math.toRadians(joueur.getLocation().getYaw());
            double dirX = -Math.sin(yawRad);
            double dirZ = Math.cos(yawRad);
            double droiteX = Math.cos(yawRad);
            double droiteZ = Math.sin(yawRad);

            double vitesse = vitesseBase * (input.isSprint() ? multSprint : 1.0);

            double vx = dirX * avant + droiteX * cote;
            double vz = dirZ * avant + droiteZ * cote;

            double longueur = Math.sqrt(vx * vx + vz * vz);
            if (longueur > 0.0001) {
                vx = (vx / longueur) * vitesse;
                vz = (vz / longueur) * vitesse;
            }

            org.bukkit.util.Vector actuelle = vache.getVelocity();
            vache.setVelocity(new org.bukkit.util.Vector(vx, actuelle.getY(), vz));

            // Rotation visuelle de la vache dans la direction du déplacement
            vache.setRotation(joueur.getLocation().getYaw(), 0);
        }
    }

    /** Appelé depuis CowSteeringListener à chaque changement d'input du joueur. */
    public void enregistrerInput(UUID uuid, org.bukkit.Input input) {
        derniersInputs.put(uuid, input);
    }

    public int getObjectifBlocs() {
        return plugin.getConfig().getInt("game.blocs-pour-gagner", 15);
    }

    // ---------------------------------------------------------------
    // Collecte d'un bloc
    // ---------------------------------------------------------------

    /**
     * Appelé par BlockSpawner quand un joueur est assez proche d'un bloc.
     */
    public void collecterBloc(Player joueur) {
        UUID uuid = joueur.getUniqueId();
        if (!session.estEnJeu(uuid)) return;

        int objectif = plugin.getConfig().getInt("game.blocs-pour-gagner", 15);
        int score = session.incrementerScore(uuid);

        joueur.sendMessage(getMessage("prefix") + getMessage("game.bloc-collecte")
                .replace("{blocs}", String.valueOf(score))
                .replace("{objectif}", String.valueOf(objectif)));

        joueur.playSound(joueur.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

        int seuilAlerte = plugin.getConfig().getInt("game.titre-alerte-blocs", 10);
        if (score == seuilAlerte && score < objectif) {
            Component titre = LegacyComponentSerializer.legacySection().deserialize(
                    getMessage("game.alerte-blocs-titre").replace("{blocs}", String.valueOf(score)));
            Component sousTitre = LegacyComponentSerializer.legacySection().deserialize(
                    getMessage("game.alerte-blocs-sous-titre").replace("{restant}", String.valueOf(objectif - score)));
            joueur.showTitle(Title.title(titre, sousTitre,
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
        }

        scoreboardManager.mettreAJourTous(session);

        if (score >= objectif) {
            terminerPartie(joueur);
        }
    }

    // ---------------------------------------------------------------
    // Fin de partie
    // ---------------------------------------------------------------

    private void terminerPartie(Player vainqueur) {
        if (etat == GameState.ENDING) return;
        etat = GameState.ENDING;

        // Arrêter les tâches de jeu
        plugin.getBlockSpawner().arreter();
        if (tacheDetection != null) { tacheDetection.cancel(); tacheDetection = null; }
        if (tacheDeplacement != null) { tacheDeplacement.cancel(); tacheDeplacement = null; }

        // Supprimer tous les blocs restants
        plugin.getBlockSpawner().nettoyerBlocs();

        int cristaux = plugin.getConfig().getInt("rewards.victoire-cristaux", 50);
        int cristaxParticipation = plugin.getConfig().getInt("rewards.participation-cristaux", 10);
        int scoreVainqueur = session.getScore(vainqueur.getUniqueId());

        // Annoncer la victoire
        String msgVictoire = getMessage("prefix") + getMessage("game.victoire")
                .replace("{joueur}", vainqueur.getName())
                .replace("{blocs}", String.valueOf(scoreVainqueur));
        diffuserJeu(msgVictoire);

        // Passer tous les joueurs en spectateur + récompenses
        for (UUID uuid : session.getJoueursEnJeu()) {
            Player joueur = Bukkit.getPlayer(uuid);
            if (joueur == null) continue;

            supprimerVache(uuid);
            scoreboardManager.retirer(joueur);
            joueur.setGameMode(GameMode.SPECTATOR);
            donnerLitRetour(joueur);

            if (uuid.equals(vainqueur.getUniqueId())) {
                joueur.sendMessage(getMessage("prefix") + getMessage("game.victoire-cristaux")
                        .replace("{cristaux}", String.valueOf(cristaux)));
                database.ajouterCristaux(uuid, cristaux);
                database.enregistrerVictoire(uuid);
            } else {
                joueur.sendMessage(getMessage("prefix") + getMessage("game.participation-cristaux")
                        .replace("{cristaux}", String.valueOf(cristaxParticipation)));
                database.ajouterCristaux(uuid, cristaxParticipation);
            }
            database.incrementerParties(uuid);
        }

        derniersInputs.clear();

        // Countdown retour Hub
        int delai = plugin.getConfig().getInt("game.fin-partie-delai-secondes", 10);
        final int[] restant = {delai};

        tacheFin = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (restant[0] <= 0) {
                tacheFin.cancel();
                renvoyerTousAuHub();
                return;
            }
            if (restant[0] <= 5 || restant[0] == 10) {
                diffuserJeu(getMessage("prefix") + getMessage("game.retour-hub")
                        .replace("{temps}", String.valueOf(restant[0])));
            }
            restant[0]--;
        }, 0L, 20L);
    }

    // ---------------------------------------------------------------
    // Retour au Hub
    // ---------------------------------------------------------------

    private void renvoyerTousAuHub() {
        String nomHub = plugin.getConfig().getString("hub.nom-serveur", "hub");
        for (UUID uuid : new HashSet<>(session.getJoueursEnJeu())) {
            Player joueur = Bukkit.getPlayer(uuid);
            if (joueur != null) {
                envoyerVersHub(joueur, nomHub);
            }
        }
        // Le reset sera déclenché depuis PlayerQuitListener quand tous seront partis
    }

    public void envoyerVersHub(Player joueur, String nomServeur) {
        // Plugin message channel — même mécanique que le Hub
        byte[] nomBytes = nomServeur.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(b);
        try {
            out.writeUTF("Connect");
            out.writeUTF(nomServeur);
        } catch (Exception e) {
            logger.warning("Erreur envoi vers Hub : " + e.getMessage());
        }
        joueur.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
    }

    // ---------------------------------------------------------------
    // Reset complet
    // ---------------------------------------------------------------

    /**
     * Réinitialise complètement le serveur pour une nouvelle partie.
     * Appelé quand tous les joueurs sont retournés au Hub.
     */
    public void reset() {
        annulerCountdown();
        if (tacheBlocs != null) { tacheBlocs.cancel(); tacheBlocs = null; }
        if (tacheDetection != null) { tacheDetection.cancel(); tacheDetection = null; }
        if (tacheFin != null) { tacheFin.cancel(); tacheFin = null; }
        if (tacheDeplacement != null) { tacheDeplacement.cancel(); tacheDeplacement = null; }

        plugin.getBlockSpawner().arreter();
        plugin.getBlockSpawner().nettoyerBlocs();

        // Retirer le scoreboard et supprimer les vaches restantes
        for (UUID uuid : session.getJoueursEnJeu()) {
            Player joueur = Bukkit.getPlayer(uuid);
            if (joueur != null) scoreboardManager.retirer(joueur);
            supprimerVache(uuid);
        }

        derniersInputs.clear();
        session.reset();
        etat = GameState.WAITING;
        logger.info("Partie réinitialisée.");
    }

    // ---------------------------------------------------------------
    // Utilitaires internes
    // ---------------------------------------------------------------

    private void supprimerVache(UUID uuid) {
        Cow vache = session.getVache(uuid);
        if (vache != null && !vache.isDead()) {
            vache.eject();
            vache.remove();
        }
    }

    private void preparerInventaireLobby(Player joueur) {
        joueur.getInventory().clear();
        joueur.setGameMode(GameMode.ADVENTURE);
        joueur.setHealth(joueur.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        joueur.setFoodLevel(20);
        joueur.setSaturation(20f);
    }

    private void preparerInventaireJeu(Player joueur) {
        joueur.getInventory().clear();
        joueur.setGameMode(GameMode.ADVENTURE);
        joueur.setHealth(joueur.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        joueur.setFoodLevel(20);
        joueur.setSaturation(20f);
        joueur.setLevel(0);
        joueur.setExp(0f);

        // Bâton de knockback (slot 0)
        ItemStack baton = new ItemStack(Material.STICK);
        ItemMeta meta = baton.getItemMeta();
        meta.setDisplayName("§6Bâton de poussée");
        meta.addEnchant(org.bukkit.enchantments.Enchantment.KNOCKBACK, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        baton.setItemMeta(meta);
        joueur.getInventory().setItem(0, baton);

        // Lit de retour (slot 8)
        donnerLitRetour(joueur);
    }

    private void donnerLitRetour(Player joueur) {
        ItemStack lit = new ItemStack(Material.RED_BED);
        ItemMeta meta = lit.getItemMeta();
        meta.setDisplayName("§cRetourner au Hub");
        lit.setItemMeta(meta);
        joueur.getInventory().setItem(8, lit);
    }

    private void annulerPartie() {
        plugin.getBlockSpawner().arreter();
        plugin.getBlockSpawner().nettoyerBlocs();
        if (tacheDetection != null) { tacheDetection.cancel(); tacheDetection = null; }
        if (tacheDeplacement != null) { tacheDeplacement.cancel(); tacheDeplacement = null; }

        for (UUID uuid : session.getJoueursEnJeu()) {
            Player joueur = Bukkit.getPlayer(uuid);
            if (joueur != null) scoreboardManager.retirer(joueur);
        }

        derniersInputs.clear();
        session.reset();
        etat = GameState.WAITING;
        logger.info("Partie annulée — plus aucun joueur en jeu.");
    }

    private void diffuserLobby(String message) {
        for (UUID uuid : session.getJoueursDansLobby()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(message);
        }
    }

    private void diffuserJeu(String message) {
        for (UUID uuid : session.getJoueursEnJeu()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(message);
        }
    }

    private String getMessage(String chemin) {
        return ChatColor.translateAlternateColorCodes('&',
                plugin.getMessages().getString(chemin, ""));
    }

    // ---------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------

    public GameState getEtat() { return etat; }
    public GameSession getSession() { return session; }
    public World getMonde() { return monde; }
    public Location getSpawnLobby() { return spawnLobby; }
}