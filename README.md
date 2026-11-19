# IvalonaTutoriel

Cinématique d'arrivée + tutoriel de 10 quêtes pour Ivalona (Paper 26.2, Java 21 par défaut).

## Installation
1. Ouvre le dossier dans ton IDE ou lance `gradle build` (le jar est dans `build/libs/`).
2. Si besoin, adapte `paperVersion` et `targetJava` dans `gradle.properties` (copie ceux de tes autres plugins).
3. Mets le jar dans `plugins/`, démarre le serveur, puis édite `config.yml` et `messages.yml` (`/tutoriel reload`).

Dépendances : **Vault** (obligatoire). Facultatifs : Nexo, PlaceholderAPI (requis par CustomNamePlates), CustomNamePlates, Essentials, LuckPerms, NuVotifier.

## Fonctionnement
- Nouveau joueur (jamais connecté) : attente du resource pack Nexo, cinématique, puis quête 1.
- 10 quêtes dans l'ordre, 5.000 versés via Vault à chaque quête, message final « Tutoriel terminé ! Bon jeu sur Ivalona ».
- La progression est sauvegardée (`data.yml`) ; à la reconnexion, la quête en cours est rappelée.
- Si le serveur plante pendant la cinématique, le joueur retrouve son mode de jeu et sa position à la reconnexion.

## Bossbar avec CustomNamePlates
Dans CustomNamePlates, utilise ces placeholders (PlaceholderAPI) pour ta bossbar :

| Placeholder | Valeur |
|---|---|
| `%tutoriel_quest%` | texte complet (format `bossbar.format` de la config), couleurs en `§` |
| `%tutoriel_quest_mm%` | idem en MiniMessage |
| `%tutoriel_quest_plain%` | idem sans couleurs |
| `%tutoriel_quest_text%` | uniquement le texte de la quête |
| `%tutoriel_step%` | numéro de la quête en cours : `0` pas commencé, `1`–`10` en cours, `11` terminé |
| `%tutoriel_active%` | `true` si une quête est en cours (à utiliser comme condition d'affichage) |
| `%tutoriel_progress%` | `3/10` |
| `%tutoriel_percent%` | `0` à `100` |
| `%tutoriel_completed%`, `%tutoriel_total%` | quêtes terminées / total |
| `%tutoriel_started%`, `%tutoriel_finished%`, `%tutoriel_in_cinematic%` | `true`/`false` |
| `%tutoriel_quest_id%`, `_title`, `_description`, `_reward` | infos de la quête en cours |

Affiche la bossbar seulement quand `%tutoriel_active%` vaut `true` (ou `%tutoriel_in_cinematic%` vaut `false` pour la cacher pendant la cinématique). Sans CustomNamePlates, mets `bossbar.native-enabled: true` pour une bossbar simple.

## Textes
Tout est dans `config.yml` (quêtes, cinématique) et `messages.yml` : codes `&c`, `&b`, hex `&#ff8800`, MiniMessage et glyphs Nexo `<glyph:dollars1>`. Les placeholders PlaceholderAPI (`%player_name%`) fonctionnent aussi.

## Cinématique
Par défaut : spirale descendante autour du spawn au lever du soleil, musique, titres, feux d'artifice, anneaux et colonnes de particules, fondu au noir. La timeline est entièrement modifiable.
- `/tutoriel cinematic setcenter` : centre de l'orbite (à ta position)
- `/tutoriel cinematic addpoint <secondes>` : trajectoire sur mesure (2 points minimum, passage fluide)
- `/tutoriel cinematic clearpoints` : retour à la trajectoire automatique
- `/tutoriel cinematic preview [joueur]` : tester sans toucher à la progression
- `/tutoriel skip` : passer la cinématique (désactivable)

## Commandes
`/tutoriel` (ou `/tuto`) : quête en cours. Admin (`tutoriel.admin`) : `reload`, `start <joueur>` (cinématique + tutoriel), `reset <joueur>` (quête 1), `setstep <joueur> <n>`, `complete <joueur>`, `vote <joueur>`.
Permission `tutoriel.bypass` : jamais de cinématique ni de tutoriel (par exemple pour le staff, via LuckPerms).

## Points d'attention
- **Quête 1** : elle est validée quand le joueur tape `/oneblock create <nom>` (le plugin ne peut pas vérifier que l'île a vraiment été créée). Le délai est réglable (`delay-ticks`).
- **Quête 3** : elle demande `/vote` et un vote reçu. Avec NuVotifier c'est automatique ; avec un autre plugin de vote, ajoute dans ses commandes de récompense : `tutoriel vote {player}`.
- **Quête 6** : `/warp Animaux` puis téléportation effective (compatible délai de téléportation Essentials).
- Les commandes lancées par un menu via `performCommand` ne déclenchent pas toujours l'événement de commande : si un menu doit valider une quête, fais-le exécuter la commande en tant que joueur par chat.
- Glyphs Nexo : le plugin réutilise le MiniMessage de Nexo, avec un repli par réflexion. Si un glyph n'apparaît pas, regarde les logs au démarrage (ligne « Glyphs Nexo »).
- `completion.commands` et `reward-commands` permettent d'ajouter des commandes console (LuckPerms, Essentials...).
