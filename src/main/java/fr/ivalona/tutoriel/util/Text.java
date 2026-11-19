package fr.ivalona.tutoriel.util;

import fr.ivalona.tutoriel.TutorielPlugin;
import fr.ivalona.tutoriel.hook.NexoHook;
import fr.ivalona.tutoriel.hook.PapiHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pipeline de texte : {variables} -> PlaceholderAPI -> codes &c / &#RRGGBB -> MiniMessage (+ glyphs Nexo).
 */
public final class Text {
    private static final Pattern HEX = Pattern.compile("(?i)[&§]#([0-9a-f]{6})");
    private static final Pattern CODE = Pattern.compile("(?i)[&§]([0-9a-fk-or])");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static boolean papi;

    private Text() {}

    public static void init(TutorielPlugin plugin) {
        papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        NexoHook.init(plugin);
    }

    public static Component parse(String raw, Player viewer, Map<String, String> vars) {
        String s = raw == null ? "" : raw;
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                s = s.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        if (papi && viewer != null) s = PapiHook.apply(viewer, s);
        return NexoHook.miniMessage().deserialize(toMini(s));
    }

    /** Convertit &c, &l, &#ff8800... en balises MiniMessage (un code couleur remet le style a zero, comme en legacy). */
    public static String toMini(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String s = HEX.matcher(raw).replaceAll("<reset><#$1>");
        Matcher m = CODE.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(tag(Character.toLowerCase(m.group(1).charAt(0)))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String tag(char c) {
        return switch (c) {
            case '0' -> "<reset><black>";
            case '1' -> "<reset><dark_blue>";
            case '2' -> "<reset><dark_green>";
            case '3' -> "<reset><dark_aqua>";
            case '4' -> "<reset><dark_red>";
            case '5' -> "<reset><dark_purple>";
            case '6' -> "<reset><gold>";
            case '7' -> "<reset><gray>";
            case '8' -> "<reset><dark_gray>";
            case '9' -> "<reset><blue>";
            case 'a' -> "<reset><green>";
            case 'b' -> "<reset><aqua>";
            case 'c' -> "<reset><red>";
            case 'd' -> "<reset><light_purple>";
            case 'e' -> "<reset><yellow>";
            case 'f' -> "<reset><white>";
            case 'l' -> "<bold>";
            case 'o' -> "<italic>";
            case 'n' -> "<underlined>";
            case 'm' -> "<strikethrough>";
            case 'k' -> "<obfuscated>";
            case 'r' -> "<reset>";
            default -> "";
        };
    }

    public static String legacy(Component c) {
        return LEGACY.serialize(c);
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    public static String mini(Component c) {
        return MiniMessage.miniMessage().serialize(c);
    }
}
