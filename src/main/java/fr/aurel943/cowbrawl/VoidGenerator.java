package fr.aurel943.cowbrawl;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

/**
 * Générateur de monde totalement vide (aucun bloc, aucune structure).
 * Utilisé pour l'arène CowBrawl, construite entièrement à la main.
 *
 * Utilise l'API ChunkGenerator historique (World, pas WorldInfo) car
 * c'est la seule signature disponible dans cette version de l'API Paper —
 * même situation que les GameRule dépréciées : ça fonctionne très bien,
 * juste marqué comme "à migrer un jour" par Paper.
 */
public class VoidGenerator extends ChunkGenerator {

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        return createChunkData(world);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}