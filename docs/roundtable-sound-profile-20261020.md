# Approved mail sound profile

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

Source review confirms the change remains in the infrastructure adapter and
configuration, introduces no imports or companion API dependency, and does not
change inventory, payments, claims or persistence. The coordinator runs one combined full suite, including these regressions,
before closing refinement and records its actual results in the global task
evidence. This worker has not
run a build or deployed the profile. The earlier recorded sound captures used
the previous settings; listening to the new profile on the final candidate
remains separate live acceptance.
