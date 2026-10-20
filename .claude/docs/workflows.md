# Workflow & Task Management

## Boucle agentique

```
/issue <besoin>          →  tâches GitHub Issues (proposition → validation → création)
/dev <tâche>             →  worktree dédié → dev-integration → dev-architecture → PR → QA (sur demande)
/dev-with-plan <tâche>   →  Plan (validé) → worktree dédié → dev-integration → dev-architecture → PR → QA (sur demande)
```

L'orchestrateur ne code pas : il transmet **tout** le contexte de la tâche à chaque agent
(les agents n'ont ni mémoire partagée ni accès au tracker) et lance un seul agent à la fois.

Chaque agent lit `.claude/docs/product.md` avant de commencer : c'est là que sont les enjeux du projet
(D-pad, UI à 10 pieds, vie privée, sécurité WebView, performance TV). Une tâche qui respecte la lettre
de ses critères mais casse un de ces enjeux n'est pas terminée.

## Worktrees — une tâche = un worktree

Chaque tâche est traitée dans un **worktree git dédié**, sur sa propre branche, jamais dans le dépôt principal
(que l'utilisateur garde pour lui). Plusieurs tâches sans dépendance entre elles avancent donc **en
parallèle** : sources, branche et répertoires de build Gradle sont isolés, et deux tâches qui touchent les
mêmes fichiers ne peuvent pas entrer en conflit. Les agents `dev-integration`, `dev-architecture` et `qa`
partagent le **même** worktree pour une tâche donnée, sur toutes les itérations.

| Élément      | Convention |
|--------------|------------|
| Emplacement  | `.claude/worktrees/<réf>` (gitignoré) |
| Branche      | `feat/<num>-slug`, basée sur `origin/main` |
| Script       | `.claude/scripts/worktree.sh` |
| Nettoyage    | `/worktree-clean`, **après fusion de la PR** |

```bash
WT="$(.claude/scripts/worktree.sh setup 42 feat/42-slug)"  # idempotent
.claude/scripts/worktree.sh path 42   # retrouver un worktree existant
.claude/scripts/worktree.sh list      # worktrees + branche + état de la PR
.claude/scripts/worktree.sh clean --dry-run             # simule le nettoyage
```

- `setup` crée le worktree s'il n'existe pas, le rend tel quel sinon, et relie les fichiers non versionnés
  nécessaires au build (`local.properties`) : Gradle fonctionne depuis le worktree. Sans
  `local.properties`, il ne trouve pas le SDK Android et tous les runs de tests échouent pour une raison sans
  rapport avec la tâche.
- `clean` ne supprime que les worktrees dont la PR est `merged`/`closed` **et** dont l'arbre est
  propre ; il ignore les worktrees `agent-*` du harnais Claude Code.
- Aucun agent ne supprime de worktree : le nettoyage passe par `/worktree-clean`, après fusion.

Les agents ne sortent **jamais** de leur worktree : aucune écriture dans le dépôt principal, aucune lecture du
travail en cours d'un autre worktree, aucun `git checkout` — une branche déjà sortie dans un worktree ne peut
pas l'être ailleurs.

## Plan Mode
- Plan pour toute tâche non triviale (3+ étapes ou décision d'architecture)
- Si ça dérape, on STOPPE et on replanifie immédiatement
- Questions de clarification uniquement pour les décisions d'architecture ambiguës,
  jamais pour des détails d'implémentation

## Task Lifecycle
1. Plan écrit dans `tasks/plan-<num>.md` avec des items cochables
2. Validation avant implémentation
3. Items cochés au fil de l'avancement
4. Résumé de haut niveau à chaque étape
5. Section « revue » ajoutée au plan en fin de tâche

## Self-Improvement
- Après TOUTE correction de l'utilisateur : consigner le pattern dans `tasks/lessons.md`
- Écrire une règle qui empêche la même erreur
- Relire `tasks/lessons.md` en début de session

## Verification
- Jamais de tâche marquée terminée sans preuve
- `./gradlew :app:testDebugUnitTest` après chaque feature, **depuis le worktree de la tâche** — la suite doit être verte
- Tout ce qui touche au D-pad, au focus ou à la WebView ne se valide pleinement que sur la TV : le signaler
  dans la PR (« à vérifier sur device ») quand la QA n'a pas été lancée
- « Est-ce qu'un·e staff engineer validerait ça ? »

## Bug Fixing
- Rapport de bug → on corrige, sans hand-holding
- On pointe les logs, erreurs, tests rouges — puis on les résout

## Elegance Check
- Changement non trivial : pause, « y a-t-il plus élégant ? »
- On saute l'étape pour les corrections évidentes — pas de sur-ingénierie

## Communication
- Droit au but, pas de remplissage
