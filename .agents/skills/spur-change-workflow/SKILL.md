---
name: spur-change-workflow
description: Manage code changes in the Spur project with one isolated Codex worktree, branch, and draft pull request per thread. Use whenever implementing, fixing, refactoring, reviewing, committing, pushing, or preparing a pull request for Spur, when parallel threads may touch the repository, or when deciding which thread's version to deploy to the phone.
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

## Finishing a change

When the user declares the change finished, update it against current `main`,
run the relevant checks, push the final branch, and move the draft pull request
to review-ready state. Merge only when the user requests or approves the merge.
After merge, remove the obsolete branch and worktree through the normal
recoverable cleanup flow.
