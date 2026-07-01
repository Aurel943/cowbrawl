package fr.aurel943.cowbrawl;

import fr.aurel943.cowbrawl.commands.CowBrawlCommand;
import fr.aurel943.cowbrawl.database.CowBrawlDatabase;
import fr.aurel943.cowbrawl.game.BlockSpawner;
import fr.aurel943.cowbrawl.game.GameManager;
import fr.aurel943.cowbrawl.listeners.KnockbackListener;
import fr.aurel943.cowbrawl.listeners.PlayerJoinListener;
import fr.aurel943.cowbrawl.listeners.ProtectionListener;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Classe principale du plugin CowBrawl.
 *
 * Responsabilités :
 *   - Générer le monde vide au démarrage
 *   - Initialiser la BDD, les managers et les listeners
 *   - Enregistrer la commande /cowbrawl
 *   - Enregistrer le canal BungeeCord pour Velocity
 */
public class CowBrawl extends JavaPlugin {

    private CowBrawlDatabase database;
    private GameManager gameManager;
    private BlockSpawner blockSpawner;
    private YamlConfiguration messages;

    @Override
    public void onEnable() {
        // 1. Sauvegarder la config par défaut si absente
        saveDefaultConfig();
        chargerMessages();

        // 2. Générer le monde vide
        genererMonde();

        // 3. Initialiser la BDD
        database = new CowBrawlDatabase(getDataFolder(), getLogger());
        database.initialiser();

        // 4. Initialiser les managers
        blockSpawner = new BlockSpawner(this);
        gameManager = new GameManager(this, database);

        // 5. Enregistrer les listeners
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new KnockbackListener(this), this);

        // 6. Enregistrer la commande
        var cmd = getCommand("cowbrawl");
        if (cmd != null) {
            CowBrawlCommand cowBrawlCommand = new CowBrawlCommand(this);
            cmd.setExecutor(cowBrawlCommand);
            cmd.setTabCompleter(cowBrawlCommand);
        }

        // 7. Enregistrer le canal plugin message pour Velocity/BungeeCord
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        getLogger().info("CowBrawl démarré avec succès !");
    }

    @Override
    public void onDisable() {
        // Arrêter proprement le spawner et nettoyer les entités
        if (blockSpawner != null) {
            blockSpawner.arreter();
            blockSpawner.nettoyerBlocs();
        }

        // Supprimer les vaches restantes
        if (gameManager != null) {
            gameManager.reset();
        }

        // Fermer le pool MySQL
        if (database != null) {
            database.fermer();
        }

        getLogger().info("CowBrawl arrêté proprement.");
    }

    // ---------------------------------------------------------------
    // Génération du monde vide
    // ---------------------------------------------------------------

    /**
     * Génère un monde complètement vide (Void) au démarrage.
     * Si le monde existe déjà sur le disque, il est simplement chargé.
     * Le joueur pourra construire l'arène et le lobby dedans librement.
     *
     * On utilise un ChunkGenerator personnalisé qui ne génère rien —
     * c'est la méthode standard Paper pour un monde vide sans avoir
     * besoin d'un plugin externe comme VoidGenerator.
     */
    private void genererMonde() {
        String nomMonde = getConfig().getString("arena.monde", "cowbrawl_world");

        if (getServer().getWorld(nomMonde) != null) {
            getLogger().info("Monde '" + nomMonde + "' déjà chargé.");
            return;
        }

        WorldCreator creator = new WorldCreator(nomMonde);
        creator.generator(new VoidGenerator());
        creator.generateStructures(false);

        World monde = getServer().createWorld(creator);
        if (monde != null) {
            monde.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            monde.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
            monde.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            monde.setGameRule(org.bukkit.GameRule.ANNOUNCE_ADVANCEMENTS, false);
            monde.setTime(6000);
            getLogger().info("Monde vide '" + nomMonde + "' généré avec succès.");
        } else {
            getLogger().severe("Échec de la génération du monde '" + nomMonde + "' !");
        }
    }

    // ---------------------------------------------------------------
    // Messages
    // ---------------------------------------------------------------

    private void chargerMessages() {
        File fichier = new File(getDataFolder(), "messages.yml");
        if (!fichier.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(fichier);

        // Fusionner avec les valeurs par défaut du jar
        InputStream stream = getResource("messages.yml");
        if (stream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            messages.setDefaults(defaults);
        }
    }

    // ---------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------

    public CowBrawlDatabase getDatabase() { return database; }
    public GameManager getGameManager() { return gameManager; }
    public BlockSpawner getBlockSpawner() { return blockSpawner; }
    public YamlConfiguration getMessages() { return messages; }
}