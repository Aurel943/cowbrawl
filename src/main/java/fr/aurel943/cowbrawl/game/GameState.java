package fr.aurel943.cowbrawl.game;

/**
 * États possibles d'une session CowBrawl.
 *
 * WAITING  → lobby ouvert, on attend des joueurs
 * STARTING → seuil atteint, countdown en cours
 * IN_GAME  → partie en cours dans l'arène
 * ENDING   → partie terminée, countdown retour Hub
 */
public enum GameState {
    WAITING,
    STARTING,
    IN_GAME,
    ENDING
}