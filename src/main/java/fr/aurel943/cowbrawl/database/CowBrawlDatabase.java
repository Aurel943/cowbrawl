package fr.aurel943.cowbrawl.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Couche d'accès MySQL pour CowBrawl.
 *
 * Se connecte à la même base plugin2_db que le Hub (Option B retenue :
 * chaque plugin a sa propre classe Database indépendante, pas de lib partagée).
 * Les tables sont préfixées cowbrawl_ pour éviter tout conflit avec les tables
 * du Hub.
 *
 * Tables gérées :
 *   cowbrawl_stats   → stats par joueur (parties, victoires, blocs collectés)
 *   cowbrawl_economy → cristaux gagnés via CowBrawl (s'ajoute à la table
 *                      economy existante du Hub via UPDATE direct)
 */
public class CowBrawlDatabase {

    private final Logger logger;
    private final File configFile;
    private HikariDataSource dataSource;

    public CowBrawlDatabase(File pluginFolder, Logger logger) {
        this.logger = logger;
        this.configFile = new File(pluginFolder, "config.yml");
    }

    // ---------------------------------------------------------------
    // Initialisation
    // ---------------------------------------------------------------

    public void initialiser() {
        if (!configFile.exists()) {
            logger.severe("config.yml introuvable — impossible d'initialiser la BDD.");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                config.getString("mysql.host", "127.0.0.1"),
                config.getInt("mysql.port", 3306),
                config.getString("mysql.database", "plugin2_db")));
        hikari.setUsername(config.getString("mysql.utilisateur", "root"));
        hikari.setPassword(config.getString("mysql.mot-de-passe", ""));
        hikari.setMaximumPoolSize(config.getInt("mysql.pool.taille-max", 5));
        hikari.setMinimumIdle(config.getInt("mysql.pool.taille-min-idle", 1));
        hikari.setConnectionTimeout(config.getLong("mysql.pool.timeout-connexion-ms", 10000));
        hikari.setIdleTimeout(config.getLong("mysql.pool.timeout-idle-ms", 600000));
        hikari.setMaxLifetime(config.getLong("mysql.pool.duree-vie-max-ms", 1800000));
        hikari.setPoolName("CowBrawl-HikariPool");

        try {
            dataSource = new HikariDataSource(hikari);
            creerTables();
            logger.info("Connexion MySQL CowBrawl établie.");
        } catch (Exception e) {
            logger.severe("Impossible de se connecter à MySQL : " + e.getMessage());
        }
    }

    private void creerTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Stats CowBrawl par joueur
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS cowbrawl_stats (
                    uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                    parties INT NOT NULL DEFAULT 0,
                    victoires INT NOT NULL DEFAULT 0,
                    blocs_collectes INT NOT NULL DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            logger.info("Tables CowBrawl vérifiées/créées.");
        } catch (SQLException e) {
            logger.severe("Erreur création tables CowBrawl : " + e.getMessage());
        }
    }

    public void fermer() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Pool MySQL CowBrawl fermé.");
        }
    }

    // ---------------------------------------------------------------
    // Stats joueur
    // ---------------------------------------------------------------

    /**
     * Incrémente le compteur de parties jouées.
     */
    public void incrementerParties(UUID uuid) {
        String sql = """
            INSERT INTO cowbrawl_stats (uuid, parties, victoires, blocs_collectes)
            VALUES (?, 1, 0, 0)
            ON DUPLICATE KEY UPDATE parties = parties + 1;
        """;
        executerUpdate(sql, uuid.toString());
    }

    /**
     * Incrémente le compteur de victoires.
     */
    public void enregistrerVictoire(UUID uuid) {
        String sql = """
            INSERT INTO cowbrawl_stats (uuid, parties, victoires, blocs_collectes)
            VALUES (?, 0, 1, 0)
            ON DUPLICATE KEY UPDATE victoires = victoires + 1;
        """;
        executerUpdate(sql, uuid.toString());
    }

    /**
     * Ajoute des blocs collectés au total du joueur.
     */
    public void ajouterBlocsCollectes(UUID uuid, int blocs) {
        String sql = """
            INSERT INTO cowbrawl_stats (uuid, parties, victoires, blocs_collectes)
            VALUES (?, 0, 0, ?)
            ON DUPLICATE KEY UPDATE blocs_collectes = blocs_collectes + VALUES(blocs_collectes);
        """;
        executerUpdate(sql, uuid.toString(), blocs);
    }

    /**
     * Ajoute des cristaux au joueur dans la table economy du Hub.
     * Les deux plugins partagent la même BDD — on écrit directement
     * dans la table economy existante plutôt que de dupliquer les données.
     */
    public void ajouterCristaux(UUID uuid, int montant) {
        String sql = """
            INSERT INTO economy (uuid, balance)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance);
        """;
        executerUpdate(sql, uuid.toString(), montant);
    }

    // ---------------------------------------------------------------
    // Lecture stats
    // ---------------------------------------------------------------

    public int getParties(UUID uuid) {
        return lireInt(uuid, "parties");
    }

    public int getVictoires(UUID uuid) {
        return lireInt(uuid, "victoires");
    }

    public int getBlocsCollectes(UUID uuid) {
        return lireInt(uuid, "blocs_collectes");
    }

    private int lireInt(UUID uuid, String colonne) {
        String sql = "SELECT " + colonne + " FROM cowbrawl_stats WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(colonne);
            }
        } catch (SQLException e) {
            logger.warning("Erreur lecture " + colonne + " pour " + uuid + " : " + e.getMessage());
        }
        return 0;
    }

    // ---------------------------------------------------------------
    // Utilitaire interne
    // ---------------------------------------------------------------

    private void executerUpdate(String sql, Object... params) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Erreur BDD CowBrawl : " + e.getMessage());
        }
    }
}