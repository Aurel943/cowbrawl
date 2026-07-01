package fr.aurel943.cowbrawl.game;

import fr.aurel943.cowbrawl.CowBrawl;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère le spawn et la suppression des blocs collectables dans l'arène.
 *
 * Chaque bloc est représenté par un ArmorStand invisible tenant un item
 * dans sa main principale — bien plus propre qu'un item au sol (pas de
 * gravité, position précise, pas de merge avec d'autres items).
 *
 * Un bloc spawn toutes les 3 secondes (60 ticks) à une position aléatoire
 * dans la zone définie dans config.yml.
 */
public class BlockSpawner {

    private final CowBrawl plugin;
    private final Random random = new Random();

    // Tous les ArmorStands actuellement en jeu (pour nettoyage rapide)
    private final Set<UUID> blocsActifs = ConcurrentHashMap.newKeySet();

    private BukkitTask tacheSpawn = null;

    public BlockSpawner(CowBrawl plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------
    // Démarrage / arrêt
    // ---------------------------------------------------------------

    public void demarrer() {
        if (tacheSpawn != null) return;

        int intervalle = plugin.getConfig().getInt("game.bloc-spawn-interval-ticks", 60);

        tacheSpawn = plugin.getServer().getScheduler().runTaskTimer(plugin, this::spawnerBloc,
                20L, intervalle);
    }

    public void arreter() {
        if (tacheSpawn != null) {
            tacheSpawn.cancel();
            tacheSpawn = null;
        }
    }

    // ---------------------------------------------------------------
    // Spawn d'un bloc
    // ---------------------------------------------------------------

    private void spawnerBloc() {
        World monde = plugin.getGameManager().getMonde();
        if (monde == null) return;

        var config = plugin.getConfig();
        int xMin = config.getInt("arena.blocs-zone.x-min", -6);
        int xMax = config.getInt("arena.blocs-zone.x-max", 6);
        int zMin = config.getInt("arena.blocs-zone.z-min", -6);
        int zMax = config.getInt("arena.blocs-zone.z-max", 6);
        double y  = config.getDouble("arena.blocs-zone.y", 65.5);

        double x = xMin + random.nextDouble() * (xMax - xMin);
        double z = zMin + random.nextDouble() * (zMax - zMin);
        Location loc = new Location(monde, x, y, z);

        // Choisir un matériau aléatoire parmi une liste de blocs colorés
        Material[] materiaux = {
                Material.GOLD_BLOCK,
                Material.DIAMOND_BLOCK,
                Material.EMERALD_BLOCK,
                Material.REDSTONE_BLOCK,
                Material.LAPIS_BLOCK,
                Material.AMETHYST_BLOCK
        };
        Material materiau = materiaux[random.nextInt(materiaux.length)];

        // Spawner l'ArmorStand invisible
        monde.spawn(loc, ArmorStand.class, stand -> {
            stand.setVisible(false);          // corps invisible
            stand.setGravity(false);          // ne tombe pas
            stand.setSmall(true);             // taille réduite = item bien positionné
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setCanPickupItems(false);
            stand.setCollidable(false);
            stand.setCustomNameVisible(false);
            stand.setInvulnerable(true);
            // L'item est tenu dans la main principale → visible au niveau de la vache
            stand.getEquipment().setItemInMainHand(new ItemStack(materiau));
            blocsActifs.add(stand.getUniqueId());
        });
    }

    // ---------------------------------------------------------------
    // Détection de collecte
    // ---------------------------------------------------------------

    /**
     * Vérifie toutes les 10 ticks si un joueur est assez proche d'un bloc.
     * Appelé depuis GameManager via tacheDetection.
     */
    public void verifierCollectes(GameManager gameManager, double rayon) {
        World monde = gameManager.getMonde();
        if (monde == null) return;

        Set<UUID> aSupprimer = ConcurrentHashMap.newKeySet();

        for (UUID standUuid : blocsActifs) {
            ArmorStand stand = (ArmorStand) monde.getEntity(standUuid);
            if (stand == null || stand.isDead()) {
                aSupprimer.add(standUuid);
                continue;
            }

            // Vérifier chaque joueur en jeu
            for (UUID joueurUuid : gameManager.getSession().getJoueursEnJeu()) {
                Player joueur = plugin.getServer().getPlayer(joueurUuid);
                if (joueur == null) continue;

                // Sécurité : ne pas comparer des locations de mondes différents
                if (!joueur.getWorld().equals(monde)) continue;

                if (joueur.getLocation().distance(stand.getLocation()) <= rayon) {
                    // Collecte !
                    stand.remove();
                    aSupprimer.add(standUuid);
                    gameManager.collecterBloc(joueur);
                    break; // un seul joueur peut collecter ce bloc
                }
            }
        }

        blocsActifs.removeAll(aSupprimer);
    }

    // ---------------------------------------------------------------
    // Nettoyage
    // ---------------------------------------------------------------

    /**
     * Supprime tous les ArmorStands de blocs encore présents dans l'arène.
     * Appelé à la fin de partie et au reset.
     */
    public void nettoyerBlocs() {
        World monde = plugin.getGameManager().getMonde();
        if (monde == null) {
            blocsActifs.clear();
            return;
        }

        for (UUID uuid : blocsActifs) {
            var entite = monde.getEntity(uuid);
            if (entite != null && !entite.isDead()) {
                entite.remove();
            }
        }
        blocsActifs.clear();
    }
}