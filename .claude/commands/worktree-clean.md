---
description: Nettoie les worktrees de tâche dont la Pull Request a été fusionnée (ou fermée) — une tâche = un worktree, supprimé une fois la PR validée.
argument-hint: [<réf de tâche> ...] — sans argument, passe en revue tous les worktrees de tâche
allowed-tools: Bash, Read, AskUserQuestion
---

Tu nettoies les worktrees de tâche du projet **OpenCTV**. Convention : **une tâche = un worktree**, dans `.claude/worktrees/<réf>`, supprimé **après fusion/validation de la PR**.

## Entrée utilisateur
`$ARGUMENTS` — une ou plusieurs références de tâche (`42`), ou rien pour passer en revue tous les worktrees de tâche.

## Marche à suivre

1. **Inventaire** — pour chaque worktree de tâche : sa branche et l'état de sa PR.
   ```bash
   .claude/scripts/worktree.sh list
   ```
2. **Simulation** — montre ce qui serait supprimé, sans rien toucher :
   ```bash
   .claude/scripts/worktree.sh clean --dry-run
   ```
3. **Présente le résultat** à l'utilisateur : ce qui sera nettoyé (PR `merged` / `closed`) et ce qui est conservé, avec la raison (PR encore ouverte, aucune PR, ou modifications non commitées).
4. **Demande confirmation** (AskUserQuestion) avant toute suppression.
5. **Nettoie** — supprime les worktrees éligibles et leur branche locale :
   ```bash
   .claude/scripts/worktree.sh clean
   ```
6. **Récapitule** : worktrees supprimés, worktrees conservés et pourquoi.

## Règles
- Le script ne supprime **jamais** un worktree dont la PR est encore ouverte, ni un worktree contenant des **modifications non commitées** — ne contourne pas ce garde-fou.
- Ne touche pas aux worktrees `agent-*` : ils appartiennent au harnais Claude Code, pas à la boucle de tâches.
- Ne supprime aucune branche distante : la fusion côté GitHub s'en charge.
- Si un worktree doit être supprimé malgré du travail non commité, c'est une décision explicite de l'utilisateur : montre-lui d'abord `git -C <worktree> status --porcelain`.
- Ne cible que `.claude/worktrees/` : les worktrees ouverts ailleurs par l'utilisateur ne te regardent pas.
