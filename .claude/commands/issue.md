---
description: Identifie et crée une ou plusieurs tâches GitHub Issues bien formées (description non technique + critères d'acceptation + Gherkin) après validation de l'utilisateur.
argument-hint: <contexte / besoin à transformer en tâches>
allowed-tools: Bash, Read, Write, Grep, Glob, WebFetch, AskUserQuestion
---

Transforme un besoin en tâches claires et actionnables sur le projet **OpenCTV** (GitHub Issues, repo `MathiasGenibrel/openctv`).

## Contexte fourni
`$ARGUMENTS`

Si ce contexte est insuffisant pour découper proprement le travail, **pose des questions** à l'utilisateur avant d'aller plus loin (objectif, périmètre, contraintes, priorités). Appuie-toi aussi sur `CLAUDE.md`, `.claude/docs/product.md` (périmètre MVP, hors-périmètre, enjeux), `.claude/docs/architecture.md` et `.claude/docs/conventions.md` pour rester aligné avec le produit et les conventions.

## Étape 1 — Identifier et PROPOSER (sans rien créer)
1. Découpe le besoin en tâches cohérentes (une tâche = un incrément livrable et testable). Évite les tâches trop larges ou trop fines.
2. Présente à l'utilisateur une **synthèse** de chaque tâche identifiée : titre, objectif, ce qu'elle couvre, et estimation indicative.
3. Signale toute tâche qui sort du périmètre MVP du brief produit ou qui touche un enjeu sensible (vie privée, sécurité WebView).
4. **Attends la validation explicite** de l'utilisateur (ajustements possibles) avant toute création. Ne crée jamais de tâche à cette étape.

## Étape 2 — Format de chaque tâche

```markdown
## Description (lisible par un·e non-technique)
<Explication claire du besoin et de la valeur, sans jargon, pour qu'un PM/QA comprenne les points à implémenter.>

## Critères d'acceptation
- [ ] <critère 1, vérifiable>
- [ ] <critère 2, vérifiable>
- [ ] Tout le parcours se fait à la télécommande (D-pad + OK + Retour), sans perte de focus

## Scénarios (Gherkin)
\```gherkin
Fonctionnalité: <nom>
  Scénario: <comportement utilisateur>
    Étant donné <contexte>
    Quand <action à la télécommande>
    Alors <résultat attendu>
\```

## Notes techniques (optionnel)
<contraintes, dossiers de feature impactés, pattern contract, écrans Compose concernés, enjeux du brief produit touchés>
```

Les scénarios Gherkin décrivent des **comportements utilisateur** et **règles métier**, pas des détails d'implémentation. Ils serviront de base aux tests JUnit (nommage Gherkin) et à la QA sur la TV.

## Étape 3 — Créer les tâches
Après accord, crée **toutes** les tâches :
```bash
gh issue create --repo MathiasGenibrel/openctv --title "..." --body-file <fichier>
```
Écris le corps de chaque issue dans un fichier temporaire (format de l'étape 2), puis passe-le à `--body-file`.

Puis renvoie à l'utilisateur la liste des tâches créées avec leurs identifiants et liens.

## Cadre
- Ne crée rien avant la validation de l'utilisateur (l'étape 1 est obligatoire).
- Aucun code, aucun commit, aucun worktree : c'est `/dev` qui ouvre le worktree de la tâche.
- Reste cohérent avec l'architecture et les conventions du projet (feature-folders, pattern contract, `Resource`/`StateFlow`).
