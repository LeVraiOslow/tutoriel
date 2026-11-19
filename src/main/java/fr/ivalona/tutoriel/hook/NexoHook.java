package fr.ivalona.tutoriel.hook;

import fr.ivalona.tutoriel.TutorielPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Support des glyphs Nexo (<glyph:nom>). Strategie 1 : reutiliser le MiniMessage de Nexo.
 * Strategie 2 : balise <glyph:...> maison, resolue par reflexion via l'API de Nexo.
 */
public final class NexoHook {
    private static MiniMessage mm = MiniMessage.miniMessage();
    private static boolean warned;

    private NexoHook() {}

    public static MiniMessage miniMessage() {
        return mm;
    }

    public static void init(TutorielPlugin plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Nexo")) return;
        MiniMessage nexo = findNexoMiniMessage();
        if (nexo != null) {
            mm = nexo;
            plugin.getLogger().info("Glyphs Nexo : utilisation du MiniMessage de Nexo.");
            return;
        }
        mm = MiniMessage.builder()
                .tags(TagResolver.builder().resolver(StandardTags.defaults()).resolver(glyphResolver()).build())
                .build();
        plugin.getLogger().info("Glyphs Nexo : resolveur interne (reflexion sur l'API Nexo).");
    }

    private static MiniMessage findNexoMiniMessage() {
        try {
            Class<?> c = Class.forName("com.nexomc.nexo.utils.AdventureUtils");
            for (String name : new String[]{"getMINI_MESSAGE", "getMiniMessage"}) {
                try {
                    Method m = c.getMethod(name);
                    Object target = Modifier.isStatic(m.getModifiers()) ? null : c.getField("INSTANCE").get(null);
                    if (m.invoke(target) instanceof MiniMessage x) return x;
                } catch (ReflectiveOperationException ignored) {
                }
            }
            try {
                if (c.getField("MINI_MESSAGE").get(null) instanceof MiniMessage x) return x;
            } catch (ReflectiveOperationException ignored) {
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static TagResolver glyphResolver() {
        return TagResolver.resolver("glyph", (args, ctx) -> {
            String name = args.popOr("Nom du glyph attendu").value();
            return Tag.inserting(glyph(name));
        });
    }

    private static Component glyph(String name) {
        try {
            Class<?> pc = Class.forName("com.nexomc.nexo.NexoPlugin");
            Object pl = pc.getMethod("instance").invoke(null);
            Object fm = pl.getClass().getMethod("fontManager").invoke(pl);
            Object g = fm.getClass().getMethod("glyphFromName", String.class).invoke(fm, name);
            if (g != null) {
                Object ch = call(g, "character", "getCharacter", "getChars");
                if (ch != null) return Component.text(String.valueOf(ch));
            }
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Bukkit.getLogger().warning("[IvalonaTutoriel] Impossible de resoudre les glyphs Nexo : " + t);
            }
        }
        return Component.empty();
    }

    private static Object call(Object o, String... names) {
        for (String n : names) {
            try {
                return o.getClass().getMethod(n).invoke(o);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
