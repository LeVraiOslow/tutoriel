package fr.ivalona.tutoriel.hook;

import fr.ivalona.tutoriel.TutorielPlugin;
import fr.ivalona.tutoriel.data.PlayerData;
import fr.ivalona.tutoriel.tutorial.Quest;
import fr.ivalona.tutoriel.tutorial.TutorialManager;
import fr.ivalona.tutoriel.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Placeholders :
 *  %tutoriel_step%            0 = pas commence, 1..N = quete en cours, N+1 = termine
 *  %tutoriel_total%           nombre de quetes
 *  %tutoriel_completed%       quetes terminees
 *  %tutoriel_progress%        ex: 3/10
 *  %tutoriel_percent%         0..100
 *  %tutoriel_active%          true si une quete est en cours (a utiliser comme condition de la bossbar)
 *  %tutoriel_started% / %tutoriel_finished% / %tutoriel_in_cinematic%
 *  %tutoriel_quest%           texte complet de la bossbar (format "bossbar.format"), couleurs en §
 *  %tutoriel_quest_mm%        idem en MiniMessage
 *  %tutoriel_quest_plain%     idem sans couleurs
 *  %tutoriel_quest_text%      uniquement le texte de la quete (champ "bossbar")
 *  %tutoriel_quest_id% / _title / _description / _reward
 */
public final class TutorielExpansion extends PlaceholderExpansion {
    private final TutorielPlugin plugin;

    public TutorielExpansion(TutorielPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "tutoriel";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Ivalona";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer op, @NotNull String params) {
        if (op == null) return "";
        TutorialManager tm = plugin.tutorial();
        PlayerData d = plugin.data().get(op.getUniqueId());
        Player online = op.getPlayer();
        int total = tm.total();
        int step = d == null ? 0 : d.step;
        int completed = Math.min(Math.max(step - 1, 0), total);
        Quest q = tm.current(d);
        String key = params.toLowerCase(Locale.ROOT);

        switch (key) {
            case "step": return String.valueOf(step);
            case "total": return String.valueOf(total);
            case "completed": return String.valueOf(completed);
            case "progress": return completed + "/" + total;
            case "percent": return String.valueOf(total == 0 ? 0 : (int) (completed * 100.0 / total));
            case "active": return String.valueOf(q != null);
            case "started": return String.valueOf(step >= 1);
            case "finished": return String.valueOf(step > total && total > 0);
            case "in_cinematic": return String.valueOf(online != null && plugin.cinematic().isIn(online));
            default: break;
        }
        if (q == null) return "";
        var vars = tm.vars(online, d, q);
        switch (key) {
            case "quest": return Text.legacy(tm.barComponent(online, d, q));
            case "quest_mm": return Text.mini(tm.barComponent(online, d, q));
            case "quest_plain": return Text.plain(tm.barComponent(online, d, q));
            case "quest_text": return Text.legacy(Text.parse(q.bossbar(), online, vars));
            case "quest_title": return Text.legacy(Text.parse(q.title(), online, vars));
            case "quest_description": return Text.legacy(Text.parse(q.description(), online, vars));
            case "quest_reward": {
                Component c = Text.parse(tm.rewardText(q), online, vars);
                return Text.legacy(c);
            }
            case "quest_id": return q.id();
            default: return null;
        }
    }
}
