#!/usr/bin/env bash
# Worktrees « une tâche = un worktree » — boucle agentique de OpenCTV.
#
#   worktree.sh setup <ref> <branche>   crée (ou réutilise) le worktree de la tâche, imprime son chemin
#   worktree.sh path  <ref>             imprime le chemin du worktree de la tâche (vide s'il n'existe pas)
#   worktree.sh list                    worktrees de tâche : branche + état de la PR
#   worktree.sh clean [--dry-run]       supprime les worktrees dont la PR est fusionnée/fermée
#
# Les worktrees vivent dans .claude/worktrees/<ref> (gitignoré) et partent de main.
set -euo pipefail

# Toujours la racine du dépôt principal, même si le script est appelé depuis un worktree :
# --git-common-dir pointe sur le .git du dépôt principal dans les deux cas.
REPO_ROOT="$(cd "$(dirname "$(cd "$(git rev-parse --git-common-dir)" && pwd)")" && pwd)"
WORKTREE_ROOT="$REPO_ROOT/.claude/worktrees"
BASE_BRANCH="${BASE_BRANCH:-main}"
TASK_PREFIX="#"
[ "$TASK_PREFIX" = "#" ] && TASK_PREFIX=""

# Fichiers non versionnés indispensables au build et aux tests, reliés dans chaque worktree.
# Sans eux, Gradle ne trouve pas le SDK et toute la suite échoue pour une raison sans rapport
# avec la tâche. Ce sont des liens symboliques : les secrets restent à un seul endroit.
LINKED_PATHS=(
  "local.properties"
)

die() { echo "worktree.sh: $*" >&2; exit 1; }

# Issues GitHub : « #42 » → « 42 » ; toute autre référence est assainie.
normalize_ref() {
  local raw="${1:-}"
  [ -n "$raw" ] || die "référence de tâche manquante (ex: 42)"
  raw="${raw#\#}"
  raw="$(printf '%s' "$raw" | tr -cs '[:alnum:]._-' '-' | sed 's/-*$//')"
  if [ -n "$TASK_PREFIX" ]; then
    raw="$(printf '%s' "$raw" | tr '[:lower:]' '[:upper:]')"
    case "$raw" in
      "$TASK_PREFIX"-*) : ;;
      [0-9]*)           raw="$TASK_PREFIX-$raw" ;;
    esac
  fi
  [ -n "$raw" ] || die "référence de tâche inexploitable"
  printf '%s' "$raw"
}

link_local_files() {
  local target="$1" rel src
  for rel in "${LINKED_PATHS[@]}"; do
    src="$REPO_ROOT/$rel"
    [ -e "$src" ] || continue
    [ -e "$target/$rel" ] && continue
    mkdir -p "$(dirname "$target/$rel")"
    ln -s "$src" "$target/$rel"
  done
}

cmd_setup() {
  local ref branch dir
  ref="$(normalize_ref "${1:-}")"
  branch="${2:-}"
  [ -n "$branch" ] || die "branche manquante (ex: feat/<num>-slug)"
  dir="$WORKTREE_ROOT/$ref"

  # Idempotent : une nouvelle itération sur la même tâche réutilise le worktree existant.
  if [ -d "$dir" ]; then
    link_local_files "$dir"
    printf '%s\n' "$dir"
    return 0
  fi

  mkdir -p "$WORKTREE_ROOT"
  git -C "$REPO_ROOT" fetch origin "$BASE_BRANCH" --quiet 2>/dev/null || true

  if git -C "$REPO_ROOT" show-ref --verify --quiet "refs/heads/$branch"; then
    git -C "$REPO_ROOT" worktree add "$dir" "$branch" >&2
  elif git -C "$REPO_ROOT" show-ref --verify --quiet "refs/remotes/origin/$BASE_BRANCH"; then
    git -C "$REPO_ROOT" worktree add -b "$branch" "$dir" "origin/$BASE_BRANCH" >&2
  else
    git -C "$REPO_ROOT" worktree add -b "$branch" "$dir" "$BASE_BRANCH" >&2
  fi

  link_local_files "$dir"
  printf '%s\n' "$dir"
}

cmd_path() {
  local dir
  dir="$WORKTREE_ROOT/$(normalize_ref "${1:-}")"
  [ -d "$dir" ] && printf '%s\n' "$dir"
}

# Imprime « merged », « closed » ou « open » pour la branche donnée.
request_state() {
  local branch="$1" state=""
  if command -v gh >/dev/null 2>&1; then
    state="$(gh pr list --head "$branch" --state all --limit 1 --json state \
      --jq '.[0].state' 2>/dev/null | tr '[:upper:]' '[:lower:]')" || true
  fi
  if [ -z "$state" ]; then
    # Pas de CLI de forge, ou aucune PR pour cette branche : on retombe sur git.
    # « merged » exige que la branche soit contenue dans la base **et** qu'elle en diffère : une
    # branche tout juste créée pointe encore sur la base, elle n'a rien produit — la déclarer
    # fusionnée ferait proposer le nettoyage d'un worktree qu'on vient d'ouvrir. Dans le doute,
    # on conserve.
    local base tip base_tip
    if git -C "$REPO_ROOT" rev-parse --verify --quiet "origin/$BASE_BRANCH" >/dev/null 2>&1; then
      base="origin/$BASE_BRANCH"
    else
      base="$BASE_BRANCH"
    fi
    tip="$(git -C "$REPO_ROOT" rev-parse --verify --quiet "$branch" || true)"
    base_tip="$(git -C "$REPO_ROOT" rev-parse --verify --quiet "$base" || true)"
    if [ -n "$tip" ] && [ "$tip" != "$base_tip" ] \
      && git -C "$REPO_ROOT" merge-base --is-ancestor "$branch" "$base" 2>/dev/null; then
      state="merged"
    else
      state="open"
    fi
  fi
  printf '%s' "$state"
}

branch_of_worktree() {
  git -C "$1" rev-parse --abbrev-ref HEAD 2>/dev/null || printf 'HEAD'
}

# Les worktrees `agent-*` appartiennent au harnais Claude Code : ce script n'y touche pas.
task_worktrees() {
  [ -d "$WORKTREE_ROOT" ] || return 0
  find "$WORKTREE_ROOT" -mindepth 1 -maxdepth 1 -type d ! -name 'agent-*' | sort
}

cmd_list() {
  local dir branch state
  for dir in $(task_worktrees); do
    branch="$(branch_of_worktree "$dir")"
    state="$(request_state "$branch")"
    printf '%s\t%s\tPR:%s\n' "$(basename "$dir")" "$branch" "$state"
  done
}

cmd_clean() {
  local dry_run=0 dir branch state
  [ "${1:-}" = "--dry-run" ] && dry_run=1

  git -C "$REPO_ROOT" worktree prune
  for dir in $(task_worktrees); do
    branch="$(branch_of_worktree "$dir")"
    state="$(request_state "$branch")"

    if [ "$state" != "merged" ] && [ "$state" != "closed" ]; then
      echo "conservé   $(basename "$dir") [$branch] PR:$state"
      continue
    fi

    if [ -n "$(git -C "$dir" status --porcelain)" ]; then
      echo "conservé   $(basename "$dir") [$branch] PR:$state — modifications non commitées"
      continue
    fi

    if [ "$dry_run" = 1 ]; then
      echo "à nettoyer $(basename "$dir") [$branch] PR:$state"
      continue
    fi

    git -C "$REPO_ROOT" worktree remove "$dir"
    git -C "$REPO_ROOT" branch -D "$branch" >/dev/null 2>&1 || true
    echo "nettoyé    $(basename "$dir") [$branch] PR:$state"
  done
  git -C "$REPO_ROOT" worktree prune
}

case "${1:-}" in
  setup) shift; cmd_setup "$@" ;;
  path)  shift; cmd_path  "$@" ;;
  list)  shift; cmd_list  "$@" ;;
  clean) shift; cmd_clean "$@" ;;
  *) die "usage: worktree.sh {setup <ref> <branche>|path <ref>|list|clean [--dry-run]}" ;;
esac
