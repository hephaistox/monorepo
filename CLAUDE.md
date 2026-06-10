# Hephaistox monorepo (orchestration root)

This directory is **not a single repo**. Each subdirectory is its
**own independent git repo**, wired together by `deps.edn` `:git/sha` refs. This root is
only the orchestration layer: `monorepo/src/tasks/{local,latest,status,projects}.clj`
driven by the root `bb.edn`. Each project has its own `CLAUDE.md` — look there for
per-project detail; keep this file about cross-repo mechanics.

## Projects & dependency DAG

All are publishable libs except `landing` (leaf app). Edges below are `deps.edn` deps
(consumed via `:git/sha`, or `:local/root` after `bb local`). `auto_build` is special:
it is consumed by **every** repo's `bb.edn`, not `deps.edn` (see Gotcha).

| repo         | role                                    | deps.edn → (hephaistox)                                          |
|--------------|-----------------------------------------|------------------------------------------------------------------|
| `auto_build` | shared lib — bb/cicd tooling            | (none)                                                           |
| `auto_core`  | shared lib — core utilities             | (none)                                                           |
| `auto_opti`  | shared lib — optimization               | auto_core                                                        |
| `auto_js`    | shared lib                              | auto_opti                                                        |
| `auto_web`   | shared lib — web (clj/cljc/cljs)        | cljc → auto_core; top → its own clj+cljc submodules (local/root) |
| `auto_sim`   | shared lib — simulation                 | auto_opti; auto_web (cljs-deps)                                  |
| `landing`    | **leaf app** — public website (uberjar) | auto_web clj/cljc/cljs                                           |

Build bottom-up: `auto_build`/`auto_core` → `auto_opti`/`auto_web` → `auto_js`/`auto_sim`
→ `landing`.

## local ↔ latest workflow

`bb <task> <project>` walks the target's monorepo deps transitively (`flatten-deps`
matches `com.github.hephaistox` coords) and rewrites **only `deps.edn`**:

- `bb local <p>`  — swaps each hephaistox `:git/sha`/`:git/url` dep to `:local/root`
  (pointing at the sibling dir). Use while developing across repos so edits are picked
  up without committing.
- `bb latest <p>` — reverse: drops `:local/root`, re-pins `:git/sha` to each sibling's
  **current local commit** (`actual-sha`), restoring `:git/url`/`:deps/root`. Use before
  pushing to publish-and-pin.

Both also rewrite the `auto-build` tooling pin in each scanned project's **`bb.edn`**
(`:deps com.github.hephaistox/auto-build`): `local` → `:local/root "../auto_build"`,
`latest` → `:git/sha <auto_build HEAD>` (see below).

**Publish→pin loop (cadence: on demand).** When a shared lib changes: commit & push it,
then re-pin its sha in every consumer (run `bb latest` on the consumer, commit & push
that). Propagate bottom-up through the DAG.

## auto-build pin lives in bb.edn, not deps.edn

`auto-build` (the bb tooling) is declared only in each repo's **`bb.edn`**
(`:deps {com.github.hephaistox/auto-build {:git/sha …}}`), so it is **not** part of the
`deps.edn` dependency graph the scan walks. `bb local`/`bb latest` therefore handle it
separately, via dedicated `bb.edn` helpers in `project/deps.clj`
(`read-bb`/`update-bb-edn`/`bb-hephaistox-deps`) — `deps-edn-filename`/`read`/`write-edn`
remain `deps.edn`-only. The `bb-deps-to-local`/`bb-deps-to-latest` steps in
`tasks.local`/`tasks.latest` run once per scanned project.

Notes:
- The **root** `bb.edn` is not a scanned project, so its `auto-build` pin
  (`:local/root "auto_build"`) is left alone — that's what lets the tasks run on local
  tooling.
- `write-edn` reflows the whole file on write; a following `bb format` (which `bp` runs
  before push anyway) collapses the diff back to just the changed pin.

## Per-repo git lifecycle

A cross-cutting change = a **separate branch / commit / push in each affected repo**;
there is no umbrella commit. **No branch-naming convention** is enforced (repos sit on
unrelated names). After pushing a lib, re-pin its sha in consumers (above).

## Root bb tasks (run from here)

- `bb projects`          — list the local git repos available as targets.
- `bb status <p>`        — for `<p>` and its transitive monorepo deps: report uncommitted
                           changes + deps whose pinned sha ≠ the sibling's actual sha, and
                           flag any `:local/root` deps. Run before pushing.
- `bb local <p>`         — point `<p>`'s hephaistox deps at local siblings (dev).
- `bb latest <p>`        — re-pin `<p>`'s hephaistox deps to siblings' latest commits (publish).

`<p>` is a project **directory name** (e.g. `auto_opti`, `landing`).

## Notes

- `bb format` is idempotent on edn — safe to run after `local`/`latest` rewrites.
