# Approved mail sound profile

## Current adoption and listening status

The exact TEST migration is prepared and independently reviewed: all twelve fields match this profile, seven existing values change, and the enabled flag/unrelated bytes are preserved. It has not been applied.

The four cues were auditioned once on FainNeito at 20:46:25–20:46:35 UTC, with fresh server confirmations and an actual screenshot. The user subsequently confirmed **“The sounds work”** (Roundtable EXPRESS 556 / SHARED 557). Listening acceptance is user-confirmed; no recording is claimed. Actual Express letter send/open and package send/claim still require controlled profile activation and workflow acceptance. See the [Java acceptance ledger](roundtable-java-acceptance-20261020.md).

The later Java per-page preview check passed on the existing installed build, with unchanged inventory/database/configuration and claim 600 released in SHARED 612 at 21:25 UTC. Its silent 110-second video covers the switch and old deadline; separate checkpoints cover expiry. This preview evidence does not establish profile activation or mail-cue dispatch. Market's SHARED 641 update records the reviewed recovery host-request draft as complete and unsubmitted; recovery clearance remains outstanding.

Market 654 separately records the owner's authorization for one graceful TEST restart during 21:59–22:11 UTC, with Market as sole executor. That scope includes no Express JAR/config swap and does not technically resolve SHARED-02. Profile activation needs fresh acknowledged scope/claim; actual recipient workflows retain their specific gate disposition. Market 664 subsequently reports the single restart completed and released at 22:05:18 UTC; its fresh plugin receipt still identifies Express c6afbcf7. Staff storage is ACTIVE, but per-player restoration evidence remains unverified.

The approved profile applies to newly generated configurations and missing cue
settings. Explicit values in an existing configuration continue to take
precedence. This change does not write or migrate the running server's config.

| Cue | Sound key | Volume | Pitch |
| --- | --- | --- | --- |
| `letter-send` | `minecraft:item.book.put` | 0.6 | 1.1 |
| `letter-open` | `minecraft:item.book.page_turn` | 0.5 | 1.0 |
| `package-send` | `minecraft:block.wooden_button.click_on` | 0.6 | 1.0 |
| `package-claim` | `minecraft:entity.item.pickup` | 0.5 | 1.0 |

## Applying the profile to an existing installation

For an approved deployment, back up the current `config.yml` and merge only the
twelve leaf settings below into its existing `sounds` section. Preserve
`sounds.enabled` and every unrelated setting. Do not delete the config to obtain
new defaults. Existing custom values should remain unless the operator intends
to adopt this profile.

```yaml
sounds:
  letter-send:
    sound: "minecraft:item.book.put"
    volume: 0.6
    pitch: 1.1
  letter-open:
    sound: "minecraft:item.book.page_turn"
    volume: 0.5
    pitch: 1.0
  package-send:
    sound: "minecraft:block.wooden_button.click_on"
    volume: 0.6
    pitch: 1.0
  package-claim:
    sound: "minecraft:entity.item.pickup"
    volume: 0.5
    pitch: 1.0
```

Activate configuration changes through the normal coordinated server restart.
If these settings need reverting, restore the twelve backed-up values. No mail
database or inventory migration is involved.

## Scope and verification

REQ-015 retains success-only feedback, per-cue validation and configurable
disable/override behavior. The coordinator's REQ-066 / INFRA-072 records the
approved profile. `SoundFeedback.Cue` now supplies each cue's volume and pitch
fallback alongside its sound key; explicit configured values still use the
existing lookup and validation path. The call sites that decide whether mail
succeeded are unchanged.

This is an INFRA configuration-profile task under the pinned SPEAR workflow.
Behavioral prove/engine phases do not apply to selecting new defaults; no
historical failing test is asserted. The only adjusted existing assertion is
the shipped package-send key in `CombatLogXHookTest`. The explicit pling override
in `NotificationAndSoundTest` remains intact, as do the existing success/failure
dispatch checks in the shipping and mailbox suites.

Source review confirms the sound-profile change remains in the infrastructure
adapter and configuration, introduces no imports or companion API dependency,
and does not change inventory, payments, claims or persistence. The coordinator's
earlier 267-test combined build and hosted run 39476819886 apply to sound
candidate `ddb27dc69a9efd5eaa1fa3c5c6e9a256411bd08f`. The final native combined
build, including the delivery recovery below, passed 285 tests with zero
failures/errors/skips at 21:55:22.406465 UTC; EARS and all 12 SPEAR helper tests
passed. Published source commit: `e7bc6109e66a89c08f40c9e3aa6bbe48bb1322f1`.
The [native evidence](evidence/event-delivery-recovery-20261020.json) records
the verified inputs, suites and artifacts. At the coordinator's 22:05:42 UTC verification, run
39505007021 had completed all six jobs successfully, including all eleven API
compilation targets; the baseline clean-build step passed (the exact 285-test XML count above is from the native run). Exact-commit
Codacy check 118912063119 passed with zero annotations. This worker has not run a build or deployed the profile. The new JAR
remains uninstalled and the twelve-field sound migration unapplied. The earlier
recorded sound captures used the previous settings. The later direct audition is
user-confirmed as recorded above; actual configured workflow dispatch on the
activated profile remains separate live acceptance.

## Separate conditional delivery recovery work

[TDD-073](tasks.md) / [REQ-067](requirements.md) addresses a conditional retry
exposure and the missing event-package payload bound. The seven-file source/test
patch is independently reviewed and locally verified: RED on unchanged
`ddb27dc` production source executed 20 tests with 10 behavioral failures and
zero errors/skips; focused GREEN passed 30 tests with zero failures/errors/skips,
followed by the 285-test clean build. The older sound candidate's 267-test result
remains historical, and completion of the earlier TDD-047 scope did not close
this new condition. Publication and new-commit hosted
verification are confirmed. Holidays PR 35 at
`ba2fcf1471e5674e377b0df697f5dd87b63d20a6` is source-compatible according to
independent review, draft and uninstalled. Holiday 657 reports canonical CI
passing 327 tests in each of three Paper jobs and eleven API compilation
targets; independent XML-count verification is pending. Canonical integration
and joint actual-provider acceptance remain pending. See the
[Java acceptance ledger](roundtable-java-acceptance-20261020.md) for the source
condition, reconciliation behavior and remaining shared gates.

