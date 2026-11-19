package fr.ivalona.tutoriel.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/** Isole l'appel a PlaceholderAPI (classe chargee uniquement si PAPI est present). */
public final class PapiHook {
    private PapiHook() {}

    public static String apply(Player p, String s) {
        return PlaceholderAPI.setPlaceholders(p, s);
    }
}
