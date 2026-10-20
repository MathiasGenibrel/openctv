---
description: Implémente une tâche GitHub Issues de bout en bout en orchestrant les agents (dev intégration → architecture), ouvre une Pull Request GitHub, puis propose optionnellement une passe QA Android TV.
argument-hint: <numéro/URL d'issue GitHub> [contexte additionnel]
allowed-tools: Task, Bash, Read, Grep, Glob, TodoWrite, AskUserQuestion
---

Tu es l'**orchestrateur** de l'implémentation d'une tâche sur le projet **OpenCTV** (navigateur web Android TV, repo `github.com/MathiasGenibrel/openctv`, tâches sur GitHub Issues). Tu ne codes pas et ne modifies pas les tests toi-même : tu coordonnes les agents (outil Task) et tu garantis le résultat.

## Entrée utilisateur
`$ARGUMENTS`

Le premier élément est la **tâche** à implémenter (identifiant ou lien). Le reste, s'il existe, est du **contexte additionnel** à transmettre aux agents.

## Cadrage
1. **Récupère la tâche** (`gh issue view <id>`) pour confirmer le périmètre. Si aucune tâche n'est identifiable, demande à l'utilisateur de préciser et arrête-toi là.
2. Note le **besoin métier**, les **critères d'acceptation** et les **scénarios Gherkin** : ils servent de référence à toutes les étapes.
3. **Passe la tâche « en cours »** (`gh issue edit <id> --add-label "status:in-progress"` ; crée le label s'il n'existe pas : `gh label create "status:in-progress"`).
4. Suis l'avancement de chaque étape et itération avec TodoWrite.

## Les enjeux à transmettre

Chaque agent démarre sans mémoire : rappelle-lui dans son prompt de lire **`.claude/docs/product.md`**, et cite les enjeux que la tâche touche (D-pad et focus, UI à 10 pieds, vie privée, sécurité WebView, performance TV). Si la tâche elle-même contredit un de ces enjeux (par exemple, ajouter un SDK d'analytics), arrête-toi et demande à l'utilisateur avant de lancer un agent.

## Worktree — une tâche = un worktree

Chaque tâche est traitée dans un **worktree git dédié**, sur sa propre branche, isolé du dépôt principal que l'utilisateur continue d'utiliser. C'est ce qui permet de mener plusieurs tâches sans dépendance entre elles **en parallèle** : sources, branche et répertoires de build Gradle sont isolés, donc deux tâches qui touchent les mêmes fichiers ne peuvent pas entrer en conflit.

1. **Constate l'état réel** avant de créer quoi que ce soit :
   ```bash
   git rev-parse --show-toplevel     # racine du dépôt principal
   .claude/scripts/worktree.sh list  # worktrees de tâche ouverts + état de leur PR
   git status --short                # modifications non commitées
   ```
2. **Crée le worktree de la tâche** :
   ```bash
   WT="$(.claude/scripts/worktree.sh setup <réf> feat/<num>-slug)"
   ```
   Le worktree est créé dans `.claude/worktrees/<réf>` depuis `origin/main`, avec la branche checkoutée et les fichiers non versionnés du build (`local.properties`) reliés. Le script est **idempotent** : à la deuxième itération sur la même tâche, il rend simplement le chemin existant. Note ce chemin — il est transmis à **chaque** agent.
3. **Cas limites**, à traiter maintenant et pas au milieu de la tâche :
   - le dossier n'est pas un dépôt git → le script échoue : dis-le et demande, ne bascule pas silencieusement sur le dépôt principal ;
   - la branche est déjà sortie dans un autre worktree → git refuse : la tâche est déjà en cours ailleurs, arrête-toi et demande ;
   - le dépôt principal a des modifications non commitées → elles ne suivent **pas** dans le worktree, et c'est voulu. Si la tâche en dépend, arrête-toi et demande.

Le **chemin absolu du worktree** fait partie du contexte que tu transmets à **chaque** agent. Un agent qui ne l'a pas travaille dans le dépôt principal : l'isolation disparaît et deux tâches parallèles se corrompent mutuellement.

## Cycle d'orchestration

1. **Développement (intégration)** — lance l'agent `dev-integration` (Task) en lui transmettant **tout le contenu de la tâche** (besoin, critères d'acceptation, Gherkin, notes techniques, identifiant) + le **chemin absolu du worktree** et sa branche + les enjeux concernés + le contexte utile. Les sous-agents peuvent lire l'issue via la CLI, mais transmets quand même le contenu.
   - Il implémente, écrit les tests JUnit au nommage Gherkin, exécute la suite de tests (`./gradlew :app:testDebugUnitTest`), et ouvre une **Pull Request liée à la tâche**.
   - Récupère de sa réponse : l'**URL de la PR**, la **branche** et le **résultat de la suite de tests**. S'il échoue à produire une PR, remonte le blocage à l'utilisateur.

2. **Revue d'architecture** — lance l'agent `dev-architecture` (Task) avec : la PR/branche + le **chemin du worktree** + le périmètre.
   - Il lance le skill `improve-codebase-architecture`, applique les améliorations pertinentes **dans le même worktree, sur la branche de la PR**, et garde la suite de tests verte.

3. **Ajoute un commentaire sur la tâche** avec le lien de la PR (`gh issue comment <id> --body "…"`).

4. **QA (OPTIONNELLE — hors boucle automatique)** :
   - La QA Android TV installe un build sur la TV et prend des captures : c'est **coûteux en tokens**. Ne la lance **jamais** d'office.
   - **Demande** à l'utilisateur (AskUserQuestion) s'il veut lancer la passe QA maintenant (la TV doit être allumée et joignable sur `10.0.0.51:5555`).
     - Si **non** → livre le récapitulatif (tâche, PR, branche) et termine ; indique que la QA reste disponible via l'agent `qa`.
     - Si **oui** → lance l'agent `qa` (Task) avec l'URL de la PR, la branche, le **chemin du worktree** et les critères d'acceptation. Il rend un `VERDICT: APPROVED | REJECTED`.

5. **Boucle (uniquement si la QA a été lancée)** :
   - Si `APPROVED` → feature livrée. Récapitulatif final (tâche, PR, verdict QA) et fin.
   - Si `REJECTED` → **relance un cycle de dev** : transmets les défauts précis de la QA à `dev-integration` puis `dev-architecture` (sur la **même PR/branche**), puis re-propose la QA.
   - Maximum ≈ 3 itérations. Si la QA refuse toujours, arrête et remonte un blocage clair (défauts persistants + ce qui a été tenté).

## Sort du worktree

Le cycle de vie du worktree est celui de la tâche : **créé avec la PR, supprimé quand elle est fusionnée.** Tu ne le supprimes **jamais toi-même**, même après une fusion constatée : le nettoyage est une opération explicite, à la main de l'utilisateur, via `/worktree-clean`.

En fin de commande, rappelle à l'utilisateur le **chemin du worktree** et la commande de nettoyage :

```bash
/worktree-clean            # après fusion de la PR
```

`/worktree-clean` ne supprime que les worktrees dont la PR est `merged`/`closed` **et** dont l'arbre est propre. Ces garde-fous ne se contournent pas : ils signalent du travail non intégré.

## Règles
- Passe toujours le **contexte complet** à chaque agent (contenu de la tâche, critères d'acceptation, enjeux concernés, **chemin du worktree**, défauts QA de l'itération précédente) — chaque agent démarre sans mémoire des autres.
- **Un worktree par tâche, réutilisé par tous les agents et toutes les itérations.** Aucun agent ne travaille dans le dépôt principal, aucun agent ne supprime de worktree.
- Lance un seul agent à la fois : chaque étape dépend de la sortie de la précédente.
- Rends compte fidèlement : si une étape échoue ou est bloquée, dis-le avec les détails.
- Le récapitulatif final indique le **chemin du worktree** et rappelle `/worktree-clean` après fusion.
