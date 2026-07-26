---
name: spur-change-workflow
description: Manage and quality-check code changes in the Spur project with one isolated Codex worktree, branch, and draft pull request per thread. Use whenever implementing, fixing, refactoring, reviewing, committing, pushing, or preparing a pull request for Spur, when parallel threads may touch the repository, or when deciding which thread's version to deploy to the phone.
---

# Spur Change Workflow

Keep every change attributable to one thread and prevent parallel work from
overwriting another thread.

## Invariants

- Use one Codex worktree, one `codex/<short-name>` branch, and one draft pull
  request per code-changing thread.
- Keep `main` limited to finished, merged changes. Never develop directly on
  `main`.
- Never place multiple threads in the same worktree or automatically combine
  active branches.
- Treat the branch and pull request as the durable record. Replacing the app on
  the phone never removes another thread's code.

## Starting a change

1. Start the thread in a Codex worktree based on `main`.
2. Create a named `codex/<short-name>` branch as soon as the scope is clear.
3. Make the first coherent change and commit it.
4. Push the branch and open a draft pull request immediately after that first
   meaningful commit. Do not add placeholder files solely to manufacture a
   pull-request diff.
5. Keep subsequent work in the same worktree and branch. Push meaningful
   checkpoints so the draft pull request remains a useful remote record.

If the repository has no initial commit or remote yet, establish that baseline
before starting parallel change threads.

## Showing a thread on the phone

For “Zeig's mir”, “Spiel das raus”, “Spiel diese Version aufs Handy”, and
equivalent requests, also use `spur-control` and run its `start` operation from
the current worktree.

- Build exactly the current worktree, including its uncommitted files.
- Require neither a commit nor a push merely to preview the current work.
- Install the same application ID, `app.spur`, so the newly requested thread
  replaces whichever Spur build is currently installed.
- Do not merge or include changes from other branches for a phone preview.
- Explain only when useful that the previous build disappears from the phone,
  while its worktree, branch, and draft pull request remain intact.

## Mandatory completion quality gate

Before declaring any code-changing feature request complete, inspect the final
diff and verify every applicable item below. Use evidence proportionate to the
risk; do not turn irrelevant items into ceremony.

### Safety and security

- Check permissions, exported components, intents, file/URI handling, input
  boundaries, secrets, logs, and storage touched by the change.
- Keep access least-privileged. Do not expose private location, photo, tour, or
  user data through logs, screenshots, temporary files, shares, or backups.
- Clean up diagnostic artifacts and temporary captures. Never retain unrelated
  content encountered during device testing.

### Navigation and lifecycle

- Exercise every changed entry and exit path: visible back/up controls, Android
  system back, dismiss gestures, cancellation, and repeated open/close.
- Confirm routing still reaches the intended screen, has no dead ends, does not
  create duplicate destinations, and restores the expected prior state.
- Check configuration/lifecycle-sensitive work for stale callbacks, leaked
  resources, duplicate jobs, and state loss.

### Design and maintainability

- Reuse existing components, tokens, icons, helpers, and domain logic. Apply
  DRY where duplication would create multiple sources of truth; do not add an
  abstraction merely to avoid a harmless repeated line.
- Keep the change scoped, remove obsolete code and dependencies, preserve
  naming and architecture conventions, and inspect adjacent callers for the
  same root cause.

### Product quality

- Check relevant loading, empty, error, offline, permission-denied, retry,
  cancellation, and rapid-interaction states.
- Verify accessibility basics: meaningful semantics, touch targets, readable
  contrast, and no essential information conveyed only by color or motion.
- Check likely performance risks: main-thread I/O, unnecessary recomposition,
  repeated decoding/allocation, map-layer churn, unbounded work, and leaks.
- Check compatibility for the Android versions and device behavior the changed
  APIs or permissions affect.

### Evidence and completion

- Run the smallest relevant automated tests plus a build; add a focused
  regression test for non-trivial logic when practical.
- Visually inspect changed UI on the best available surface. For navigation or
  lifecycle changes, exercise the real device when reachable.
- Review `git diff`, `git diff --check`, and repository status so only intended
  files remain.
- Report any skipped or blocked check explicitly. A successful build, push, or
  deployment alone is not proof that the feature is complete.

## Finishing a change

Pass the mandatory completion quality gate. When the user declares the change
finished, also update it against current `main`, push the final branch, and move
the draft pull request to review-ready state. Merge only when the user requests
or approves the merge. After merge, remove the obsolete branch and worktree
through the normal recoverable cleanup flow.
