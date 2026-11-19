package fr.ivalona.tutoriel.tutorial;

import java.util.List;

/**
 * COMMAND : le joueur tape une des commandes (prefixe), avec au moins minArgs arguments en plus.
 * VOTE    : le joueur a vote (Votifier ou /tutoriel vote).
 * WARP    : le joueur tape la commande de warp puis est reellement teleporte.
 */
public record Condition(Type type, List<String[]> commands, int minArgs, int delayTicks) {
    public enum Type { COMMAND, VOTE, WARP }
}
