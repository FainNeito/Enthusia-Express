# SPEAR migration tasks

## INFRA-071 — Normalize legacy preview test imports

Tag: INFRA
References: REQ-065; implementation.md Layer Dependency Rules.
Evidence: Exact-head Codacy3a4b45e reports seven notices solely for fully qualified ItemCodec/ContainerScanner names in MuseumPreviewTest. Add explicit imports of the already existing io.enthusia.express.infrastructure.util.ItemCodec and io.enthusia.express.infrastructure.util.ContainerScanner; production source/resources remain unchanged. Behavioral prove/engine steps do not apply to import-only test cleanup. Repeat the existing267 tests and compare the distributable hash to the deployed3a4b45e candidate.
Status: [x] local checks complete; exact-head hosted checks tracked separately.
Result: legacy-preview-imports.log passes267 tests with zero failures/errors/skips. EARS and twelve helper regressions pass. Distributable SHA256 c6afbcf7a4d5572834f9b8a639b833dfa3aef46fb610dfc14e7b115fa78d5197 is byte-identical to the3a4b45e TEST candidate; no additional restart is needed for this test-import-only cleanup.

## TDD-070 — Preview legacy museum cargo

Tag: TDD
References: REQ-065, REQ-064; implementation.md Read-only museum preview.
Evidence: Authorized TEST client run on c5fda79 opened The Eve with zero unique map IDs and Preview artwork rejected the page (04-legacy-manifest-gap.png). MuseumCatalog.initialize imports museum_tiles only for a non-null old single-map ID; old container donations may retain payload without a tile manifest. Existing io.enthusia.express.infrastructure.util.ItemCodec.decode(byte[]) uses Paper ItemStack.deserializeBytes and io.enthusia.express.infrastructure.util.ContainerScanner.mapartManifest(ItemStack,int) validates map-only cargo, nesting, checked copy sums and8192 traversal visits. Existing mail.max-package-payload-bytes defaults262144 and is validated at most1048576; mail.max-recursive-container-depth defaults8. MuseumPreview.show checks owner/manager and current use/combat before platform display. org.bukkit.configuration.file.FileConfiguration is already used by adapters. Existing JUnit/Mockito static mocks and MuseumPreviewTest fixtures can prove fallback, configured limits, invalid bytes and authorization before decoding. No database write, physical inventory mutation, item transfer, new dependency or renderer mutation is required. Live Java/Bedrock results remain separate.
Status: [x] source and local automated scope complete; exact-head hosted review and updated legacy TEST acceptance remain separate.
Result: legacy-preview-red.log failed two of thirteen focused cases before the fallback; legacy-preview-green.log passes all thirteen, including owner-before-decode and payload limits. The full267-test build and all eleven API compilation targets pass in legacy-preview-refine.log, with the267-test pass repeated after viewing guidance/legacy-label refinements in legacy-preview-final.log. The helper is infrastructure-only, does not write metadata or touch physical inventory, and uses the existing guarded scanner and payload-size policy. Actual c5fda79 client evidence verified two populated pages,2/1 copy counts, original inventory digest preservation and sneak restoration; its missing legacy indexes motivated this fix and are not yet claimed as live-accepted for the new source.

## INFRA-069 — Refine preview adapter ownership

Tag: INFRA
References: REQ-064, REQ-007; implementation.md Read-only museum preview.
Evidence: Exact-head Codacy3b616a5 identifies method-count limits in MuseumMenus and MuseumPreviewEvents and a broad RuntimeException catch in MuseumPreview. Move the existing view-scope predicate to MuseumDetail, split lifecycle/navigation events from hidden-item interaction cancellation, and use finally for unconditional cosmetic cleanup while letting MainThread's existing isolated callback boundary log failures. This is behavior-preserving refinement of existing org.bukkit event adapters and local preview classes, with no new dependencies, permission, persistence or API behavior. Prove/engine steps do not apply; retain the ten focused preview tests and full264-test/API suite as the oracle.
Status: [x] final local refinement complete; exact-head hosted checks tracked separately.
Evidence: Codacy applies its11-function threshold inclusively. Final refinement separates the existing filter presentation into MuseumFilters, retaining identical controls and guarded command callbacks; MuseumMenus now has10 functions. Imports are the same MuseumFilter/MuseumState, Component/ClickEvent and Player APIs already used by the moved method. The moved method body was compared byte-for-byte with its prior implementation. map-preview-final-reviewed.log passes the full264-test suite and all eleven API compilation targets after this final extraction.
Result: map-preview-reviewed.log repeats the full264-test build and all eleven Paper API compilation targets with no failures. The existing ten preview regressions remain green; lifecycle and interaction adapters each stay below the method-count threshold. Failure cleanup uses finally and preserves MainThread's existing callback logging boundary. EARS, layer and compiled call-cycle checks pass. No TEST, production or client action was performed.

## TDD-068 — Read-only museum map preview

Tag: TDD
References: REQ-064, REQ-004, REQ-007, REQ-051; implementation.md Read-only museum preview.
Evidence: MuseumDetail.kt currently offers only Full map manifest and review actions; MuseumMenus.detail scopes metadata to submitter or enthusiaexpress.mapart.manage. MuseumCatalog.tiles returns map IDs and copy counts, not spatial tile positions. Existing application.MailStore.artwork returns metadata without decoding cargo. The cached baseline Paper1.21 API javap confirms org.bukkit.entity.Player.sendEquipmentChange(LivingEntity, Map<EquipmentSlot, ItemStack>), sendMap(org.bukkit.map.MapView), updateInventory() and org.bukkit.inventory.meta.MapMeta.setMapView(MapView). org.bukkit.Bukkit.getMap(int) resolves existing maps; no createMap or MapRenderer mutation is required. org.bukkit.Material, inventory.ItemStack, inventory.EquipmentSlot, event.Listener/EventHandler/EventPriority, event.player and event.inventory events, event.entity.PlayerDeathEvent, scheduler.BukkitTask and plugin.java.JavaPlugin are baseline infrastructure APIs. net.kyori.adventure.text.Component/event.ClickEvent provide existing literal clickable command controls. java.util.UUID, Kotlin standard collections and existing org.junit.jupiter.api.Test/Assertions, org.mockito.Mockito/ArgumentCaptor fixtures suffice. Official Paper Player API documents sendEquipmentChange as cosmetic and sendMap as sending the whole rendered map. Inventory setters, persistent map generation, packet-library dependencies, physical collection and database writes are excluded. Client rendering, including Geyser translation, remains unverified until separately authorized; user forbids TEST activity for this change.
Evidence: Additional verified baseline event adapters and local test imports: org.bukkit.event.EventPriority, org.bukkit.event.entity.EntityDamageByEntityEvent, org.bukkit.event.entity.PlayerDeathEvent, org.bukkit.event.inventory.InventoryOpenEvent, org.bukkit.event.player.PlayerCommandPreprocessEvent, org.bukkit.event.player.PlayerDropItemEvent, org.bukkit.event.player.PlayerInteractAtEntityEvent, org.bukkit.event.player.PlayerInteractEntityEvent, org.bukkit.event.player.PlayerInteractEvent, org.bukkit.event.player.PlayerItemHeldEvent, org.bukkit.event.player.PlayerSwapHandItemsEvent, org.bukkit.event.player.PlayerTeleportEvent, org.bukkit.event.player.PlayerToggleSneakEvent, org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.meta.MapMeta, org.bukkit.map.MapView. All platform types remain infrastructure-only; new preview classes consume the existing immutable museum metadata.
Status: [x] source and local automated scope complete; native Java/Bedrock client rendering and exact-head hosted review remain separate. No TEST or production actions.
Result: map-preview-red.log records five genuine missing-preview failures before implementation; map-preview-green.log passes the seven initial regressions. Full build and all eleven baseline API targets pass 264 tests, including ten focused preview cases, layer and compiled no-cycle checks (map-preview-refine.log). Normal inventory and cargo are never mutated. EARS and twelve helper tests pass. Owner-private responsive simulation is published and checked at 390px width with page/copy-count and interruption controls; sample pixels are illustrative. No server action was performed.

## INFRA-067 — Default Minecraft font for Post Office labels

Tag: INFRA
References: REQ-063, REQ-062; implementation.md Optional Post Office presentation.
Evidence: tools/build_postoffice_assets.py loads the official client default bitmap providers for text(), but its tiny() helper uses a separate 3x5 alphabet. Replace the latter with the existing unscaled renderer and measure labels before drawing. Home card widths are54 pixels; the shipping control centers are36 pixels apart. Shorten helper copy and retain full action names in existing item tooltips. Assets only: no runtime engine, domain/application, persistence, permission, API or dependency changes; behavioral prove/engine steps do not apply. Check generated typography bounds, existing asset tests and visual previews, with real client acceptance recorded separately.
Status: [x] source and automated scope complete; TEST/client acceptance recorded separately.
Result: regenerated all seven backgrounds with default bitmap glyphs and per-label width/height guards. Removed the custom 3x5 alphabet. Existing254 tests and asset, architecture and runtime call-cycle checks pass in font-label-build.log. EARS and twelve state/helper checks pass. No Kotlin source, registry IDs, glyph geometry or cargo/payment behavior changes. No historical red/green is claimed for this asset-only task. Live Java client review caught the larger shipping helper overlapping recipient slot4; shorten it to Details and reserve x101..165, to the right of that icon. Home and inbox already rendered correctly; recheck shipping after the correction.

## INFRA-066 — Normalize shared-icon provenance digest

Tag: INFRA
References: REQ-062; implementation.md Optional Post Office presentation.
Evidence: Git normalizes JSON assets to LF. The new icon-sources.json was initially written with Windows CRLF, so its generated manifest digest represented local bytes rather than the committed LF bytes. Normalize before hashing and compare all declared manifest entries to the Git index blobs. This changes only delivery metadata, not engine behavior or imports; no behavioral red/green steps apply.
Status: metadata corrected; final index digest and build checks recorded with delivery evidence.

## TDD-065 — Existing pack icons and label clearance

Tag: TDD
References: REQ-062, REQ-060, REQ-061; implementation.md Optional Post Office presentation and Layer Dependency Rules.
Evidence: user screenshot32321 shows the previous centered lower icons obscuring labels and the vanilla Inventory text exceeding its painted band. Current MuseumView already anchors lower icons at36/39/42. tools/build_postoffice_assets.py base paints only y126..134 for a six-row inventory although the client font extends lower. Authoritative FainNeito/Enthusia-Pack main6ff2abd3060228e0a94fd6e25173a48158d9392e has java/nexo/items/lg_nav.yml, lg_ui.yml, lg_status.yml, lg_economy.yml and lg_enthusia_gui.yml; README/docs/LICENSES.md require licensed icon pixels to stay private. Reference registry IDs and texture hashes only in this public repository. Existing GuiTheme.item clones templates before metadata changes. org.bukkit.inventory.meta.ItemMeta.setEnchantmentGlintOverride is present on the baseline Paper API; org.junit.jupiter.api.Test/Assertions, org.mockito.Mockito, java.nio.file.Files/Path, java.util.regex.Pattern and javax.imageio.ImageIO are existing fixtures. No new dependencies, domain mutation, payment or cargo behavior is intended.
Status: [x] automated/source scope complete; TEST activation and real-client acceptance for this revision remain separate.
Result: pack-clearance-red.log proves three failures before the change: caption exceeded the cream band, profile still used replacement sprites, and selected shared icons lacked a glint. The focused tests then pass (pack-clearance-green.log). Existing pack IDs are pinned in icon-sources.json; sixteen newly drawn sprites and their item definitions are removed. Original chest backgrounds reserve the entire caption band, and selected-tab glint is applied only to a cloned item. Full clean build, all eleven API compilation targets, architecture/no-call-cycle gates and254 tests pass (pack-clearance-build.log); the actual baseline JAR also passes254 tests on final Paper26.3 / Java25 (pack-clearance-26.3.log). EARS and twelve state/helper tests pass. No domain/application dependency, cargo, payment, schema or permission changes. Read-only TEST comparison confirms every selected icon's installed bytes match the pinned private pack. The responsive owner-private preview references those same PNG bytes; existing actual captures are explicitly identified as the previous revision until new client captures are added. No production change is implied.

## TDD-064 — Resolve typed protocol overloads

Tag: TDD
References: REQ-056, REQ-060; implementation.md Thread ownership and optional dialog adapters.
Evidence: actual TEST Paper26.2.128 with ViaVersion5.12.1 and Dawn26.3 logs ClassCastException UUID-to-Player at BukkitViaAPI.getPlayerProtocolVersion, reached from ReflectiveDialogs.dialogInvoke and DialogMenus.supportsDialog. ViaAPI exposes erased generic player and UUID overloads; the first-compatible reflection search selects the wrong one. java.lang.Class/reflect.Method, java.util.UUID and existing JUnit Assertions/Test are standard existing dependencies. A minimal interface/implementation fixture reproduces the same generic bridge without bundling a companion API. Prefer exact argument types among compatible methods, retaining optional loading, primitive compatibility, fallback and callback guards.
Status: automated regression and Java native client retest complete; wider client acceptance remains bounded below.
Result: native-overload-red.log reproduced the same UUID-to-player ClassCastException. Exact-match preference passes the regression and full251-test suite, all11 older API compilation targets and architecture/no-call-cycle gates. TEST now runs the d696085 candidate: Dawn26.3 opens the native metadata, feedback, correction, detail and manifest dialogs through ViaVersion5.12.1. Corrections submitted within the three-minute session save successfully; an expired form did not mutate the record.

## INFRA-063 — Exact-head review and client presentation refinement

Tag: INFRA
References: REQ-060, REQ-061; implementation.md Optional Post Office presentation and Thread ownership.
Evidence: GitHub Actions passes all six runtime profiles at c08a09f. Codacy reports31 style/complexity annotations in the original asset generator and presentation adapter. Actual Dawn26.3 TEST screenshot02-home-review proves that the three lower home-card icons overlap their baked labels; shifting those visible icons to the first slot preserves every protected route. Existing PostOfficeLayoutTest, NexoIntegrationTest and PostOfficeAssetsTest remain the regression oracle. No domain, cargo, payment or persistence change is intended. Behavioral prove/engine steps do not apply to formatting, decomposition and icon placement; the actual client defect is recorded rather than fabricating an automated historical red test.
Status: original31 Codacy annotations and subsequent method-count/file-size findings addressed through decomposition, named constants, imports, readable generation code and removal of unused palettes. Generated artwork remains unchanged; deterministic LF output aligns the manifest with Git assets.251 automated regressions and live presentation evidence are retained. Exact final-head hosted review and native dialog client acceptance remain separate checks.

## TDD-061 — Protected Post Office navigation and viewer fallback

Tag: TDD
References: REQ-060, REQ-061, REQ-036; implementation.md Layer Dependency Rules, Thread ownership and Shipping compensation.
Evidence: Existing io.enthusia.express.infrastructure.gui.GuiTheme, NexoItemSource, MuseumView, MenuChoice, MailboxControls, MailboxService and ShippingService own optional assets and inventory lifecycles. Nexo1.29.1 actual JAR javap confirms com.nexomc.nexo.api.NexoItems registry methods and com.nexomc.nexo.glyphs.Glyph / fonts.FontManager signatures; official <https://docs.nexomc.com/configuration/glyphs> documents title glyphs, shift offsets and visual-only inventories. Existing org.bukkit.entity.Player, Bukkit, Material, inventory.ItemStack, plugin.Plugin, plugin.PluginManager, plugin.java.JavaPlugin and configuration.file.YamlConfiguration tests provide public Paper APIs. org.junit.jupiter.api.Test / Assertions, org.mockito.Mockito and MockedStatic are existing regression fixtures; java.util.List, Map, Set, UUID and IdentityHashMap are JDK-only. Existing com.nexomc loader reflection is optional infrastructure. No domain/database mutation is introduced by the skin. Font and sprite assets are original renderings from approved concept sources; no third-party menu pack is copied.
Evidence imports verified from existing JDK/Paper/JUnit/Mockito APIs and the inspected source: io.enthusia.express.infrastructure.gui.PostOfficeLayout, java.nio.file.Files, java.nio.file.Path, java.util.List, java.util.Map, java.util.regex.Pattern, java.util.stream.IntStream, javax.imageio.ImageIO, net.kyori.adventure.text.Component, org.bukkit.configuration.file.YamlConfiguration, org.bukkit.entity.Player, org.bukkit.plugin.Plugin, org.junit.jupiter.api.Assertions.*, org.junit.jupiter.api.Test.
Status: [x] automated implementation and local review complete; exact TEST activation and real client acceptance remain separate gates.
Result: missing PostOfficeLayout/forViewer APIs produced eight expected compilation errors in nexo-layout-red.log after fixing unrelated local toolchain arguments. Protected-card, curator absence, full catalog, wide-tab, accepted/declined-pack and missing-background checks pass. The asset contract caught a real sent/museum icon/background filename collision; bg-prefixed background paths correct it. The Java21 baseline build passes250 tests with zero failures/errors/skips, all11 older API compilation targets, layer/no-call-cycle checks, EARS and12 helper regressions. Source review retained native detail/input readability, all painted tab hit regions and existing public Java overloads. Final clean-head runtime/hosted checks and TEST evidence are tracked separately.

## INFRA-062 — TEST activation and real client acceptance

Tag: INFRA
References: REQ-060, REQ-061, REQ-031, REQ-038, REQ-047; implementation.md Optional Post Office presentation and Thread ownership.
Evidence: configured Bloom alias test resolves5d109214 and API identity SMP Test Server /5d109214-9243-46d6-9ef3-db2765b10fc3; DPAPI credential loader in enthusia-dev-tools is used without session scraping. TEST has Nexo1.29.1, Currency/Vault, CombatLogX and backend Floodgate;34 existing Nexo YAML files have no ee_postoffice_ ID collision. Existing update queue is empty. Windows Computer Use identified Dawn Client26.3, and a live baseline screenshot shows FainNeito on ENTHUSIA TEST with the original mailbox. Root world uses the26.3 world/players layout. No production operation is authorized. Power, backups, additive asset/config writes and client interactions must be identity-checked and recorded; a new Site page may privately deliver requested screenshots. No prove/engine step applies to operational activation; source changes found during acceptance require their own regression/refinement evidence.
Status: coordinated stopped-server backup and TEST activation completed at c08a09f; actual Dawn26.3 displays the new home and inbox skins with correct font alignment. The old JAR was deleted only after the user explicitly requested deletion and its backup was rechecked.29 extracted integration/resource entries and26 assets byte matched over the authenticated TLS panel; the full remote archive hash remains unavailable because the storage certificate expired. Currency1.4.4 movement leases and CombatLogX loaded. Live flow checks remain in progress; no complete client acceptance or production change is claimed.

Final bounded acceptance, 2026-10-19: source d696085961569ffd26683f04acf9051cbe257a78, local artifact SHA-256 48a03ede6711cd1e671572c28cefbb2ddb75dba4ad39aff2317ff68f647f9f55. The final candidate was verified in the TEST update queue through size and eleven extracted changed entries; Paper consumed it and enabled Express. The active filename retains c08a09f for Paper replacement and is not the source identifier. Actual server is Paper26.2-128 at5749698 / Java25.0.3, with Dawn26.3 via ViaVersion5.12.1; final26.3 is a separate automated baseline-JAR runtime test.

Client evidence: protected home/card and wide inbox routes, retained cursor, Sent history, lower icon placement, loose-paper rejection without charge, cancellation returning cargo, and a labelled bundle sent to the approved fainNoir account. The first postage click quoted1 currency before withdrawal; confirmation reduced the virtual balance3103 to3102, and the sent record persisted across the candidate restart. Fresh physical map18 was submitted with a separate1-currency quote, assigned, returned for changes, corrected, resubmitted, accepted and collected. Reopening showed COLLECTED and no collection action; the QA-only displayed transition and manifest18x1 worked. No actual exhibit placement is claimed. Offline name suggestions and a labelled letter to fainNoir worked; a self-only announcement opened as a readable book and retained the read icon. Mark All Text Read responded correctly with0 because that announcement had already been read; positive bulk mutation remains automated proof here.

Remaining gates: fainNoir cannot join now, so recipient login notification, letter reading and personal-package claim are pending. Fresh Bedrock, combat-tagged interaction, sound audibility, top-right arrival rendering and exhaustive client failure/recovery acceptance are not claimed. Full251-test suite, all six hosted Paper runtime jobs and Codacy with0 annotations passed at d696085; one Gradle download connection reset required a job retry. CodeRabbit's success status was an automatic-review skip, not reviewer approval. This evidence-only update changes no engine or imports, so no new behavioral red/green cycle applies. The prior unchanged-code suite remains valid; EARS and local state/helper checks are repeated. Production remains unchanged and the PR stays unmerged.

Visual evidence: [interactive source reconstruction and unedited TEST captures](https://express-museum-dialogs-preview-20261018.awareyak.chatgpt.site/test.html). Browser interactions send nothing; a mobile HTTPS publication is provided, while fresh browser/device QA was unavailable. Operational receipts, backups, fixture intents and screenshots are retained in the local express-postoffice-20261019 delivery directory; private configurations and credentials are excluded from source.

- [x] **TDD-001** — Rewrite the plugin in Kotlin while preserving behavior.

  Tag: TDD

  References: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007, REQ-008, REQ-009, REQ-010, REQ-011; implementation.md sections Layer Dependency Rules, Behavioral compatibility, Thread ownership and Verification.

  Acceptance: every production source is Kotlin; the Kotlin runtime is bundled; all 38 existing regressions plus migration and architecture tests pass; no project call cycles; 11 API compilation checks; installable JAR and matching sources/report.

  Evidence:

  - Baseline: merged PR #2 commit 45efccc1ecac113c009070ede2c0eebddfeb3671; work/pr-source byte-verified after the recursion fix.

  - io.enthusia.express.domain.MailRecord, io.enthusia.express.domain.MailStatus, io.enthusia.express.domain.MailType: original mail/MailRecord.java, MailStatus.java and MailType.java; unchanged field/status contracts.

  - io.enthusia.express.application.MailStore: signatures derived from db/MailRepository.java; Java CompletableFuture API in the installed Java 21 sources.

  - io.enthusia.express.infrastructure: relocated baseline Paper adapters; source signatures and behavior are specified by original command/, db/, gui/, hook/, mail/ and util/ Java files and the 38 existing tests.

  - org.bukkit, io.papermc, com.github.sirblobman: baseline compile/test classpaths and actual previously validated API JARs; plugin.yml; CombatLogXHookTest.java.

  - org.junit.jupiter.api and org.mockito: unchanged Java regression imports and installed test dependencies.

  - com.lemonappdev.konsist.api.Konsist, com.lemonappdev.konsist.api.architecture.Layer, com.lemonappdev.konsist.api: upstream SPEAR templates/LayerRulesTest.kt and <https://github.com/LemonAppDev/konsist/blob/main/README.md>.

  - org.jetbrains.kotlin.gradle: <https://kotlinlang.org/docs/gradle-configure-project.html> and <https://kotlinlang.org/docs/gradle-compiler-options.html>; Kotlin 2.2.21 supports this Gradle line.

  - java.*, javax.*, kotlin.*: language/JDK standard APIs, verified in installed JDK sources or baseline usage; no added external runtime framework.

  - SPEAR: pinned upstream codex-skills/spear-{using-spear,spec,prove,engine,arch,refine}/SKILL.md, hooks/lib/state.sh, hooks/lib/ears.mjs and templates/LayerRulesTest.kt.

  Progress: complete. Two migration assertions failed against Java, then all 45 tests passed on three Paper API classpaths and the final clean build. All 11 Kotlin compilation targets passed. Konsist and exact-import architecture gates passed; compiled project call graph contained no detected cycles. See VERIFICATION.md and the matching XML evidence archive.

  Exact import evidence (verified against baseline sources and resolved JAR signatures):

  - com.lemonappdev.konsist.api.architecture.Layer — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - com.lemonappdev.konsist.api.ext.list.withPackage — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - com.lemonappdev.konsist.api.Konsist — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - com.lemonappdev.konsist.api.Konsist.assertArchitecture — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - com.lemonappdev.konsist.api.verify.assertFalse — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.application.MailStore — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.domain.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.domain.MailRecord — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.domain.MailStatus — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.domain.MailType — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.command.MailCommand — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.db.MailRepository — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.gui.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.gui.GuiListener — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.gui.MailboxService — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.gui.ShippingService — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.hook.CombatLogXHook — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.mail.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.mail.BookMailService — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.mail.ExpirationService — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.util.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.util.ConfigValidation — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.util.ContainerScanner — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.util.ItemCodec — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.util.MainThread — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - io.enthusia.express.infrastructure.util.Text — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.io.File — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.net.URLClassLoader — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.nio.file.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.nio.file.Files — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.nio.file.Path — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.sql.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.sql.Connection — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.sql.DriverManager — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.sql.ResultSet — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.sql.SQLException — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.sql.Statement — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.util.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.util.ArrayDeque — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.util.concurrent.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.util.concurrent.CompletableFuture — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.util.concurrent.CompletionException — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.util.concurrent.ConcurrentLinkedQueue — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.util.concurrent.Executors — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.util.concurrent.TimeUnit — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.util.function.BiConsumer — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.util.jar.JarFile — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.util.Locale — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.util.logging.Level — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.util.logging.Logger — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.util.stream.IntStream — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - java.util.UUID — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.block.ShulkerBox — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.Bukkit — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.ChatColor — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.command.Command — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.command.CommandExecutor — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.command.CommandSender — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.command.TabCompleter — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.configuration.file.FileConfiguration — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.configuration.file.YamlConfiguration — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.entity.Player — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.event.EventHandler — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.event.inventory.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.event.inventory.ClickType — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.event.inventory.InventoryClickEvent — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.event.inventory.InventoryCloseEvent — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.event.inventory.InventoryDragEvent — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.event.Listener — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.inventory.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.inventory.Inventory — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.inventory.ItemStack — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.inventory.meta.BlockStateMeta — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.inventory.meta.BookMeta — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.inventory.meta.BundleMeta — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.inventory.meta.ItemMeta — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.Material — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.OfflinePlayer — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.plugin.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.plugin.java.JavaPlugin — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.scheduler.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.scheduler.BukkitTask — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.bukkit.Server — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.junit.jupiter.api.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.junit.jupiter.api.Assertions.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.junit.jupiter.api.Assertions.assertTrue — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.junit.jupiter.api.io.TempDir — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.junit.jupiter.api.Test — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.mockito.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.mockito.Mockito.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - org.objectweb.asm.* — baseline implementation/test API; Kotlin standard library or resolved dependency classpath. Konsist uses its 0.17.3 sources JAR; ASM uses 9.7.1 ClassReader/ClassVisitor/MethodVisitor signatures.

  - com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture — verified Konsist 0.17.3 source API and compiled architecture test.

  - org.jetbrains.kotlin.gradle.dsl.JvmTarget, org.jetbrains.kotlin.gradle.tasks.KotlinCompile — Kotlin Gradle plugin 2.2.21 DSL documentation and successful source-set compilation tasks.

  - Gradle worker limit and kotlin.daemon.jvmargs — final clean matrix exposed compiler heap pressure; two workers and a 1 GiB Kotlin heap bound concurrent compilation. Verified by the final clean build.

- [x] **TDD-002** — Add shipping guidance, optional outstanding limits, join notices and mail sounds.
  Tag: TDD
  References: REQ-012, REQ-013, REQ-014, REQ-015; implementation.md Layer Dependency Rules and Thread ownership.
  Evidence:
  - Verified imports for the reviewed Kotlin port: io.enthusia.express.application.MailStore, io.enthusia.express.domain.MailSummary, io.enthusia.express.domain.MailType, io.enthusia.express.infrastructure.mail.JoinNotificationService, io.enthusia.express.infrastructure.util.MainThread, io.enthusia.express.infrastructure.util.SoundFeedback, io.enthusia.express.infrastructure.util.Text, java.util.Locale, java.util.OptionalLong, org.bukkit.Bukkit, org.bukkit.NamespacedKey, org.bukkit.entity.Player, org.bukkit.event.EventHandler, org.bukkit.event.Listener, org.bukkit.event.player.PlayerJoinEvent, org.bukkit.persistence.PersistentDataType, org.bukkit.plugin.java.JavaPlugin, org.sqlite.SQLiteConfig. Sources: committed PR #3 source at 9358600, current MailStore and MailSummary definitions, resolved Paper 1.21 and SQLite 3.50.3.0 APIs; compiled successfully in work/kotlin-reviewed-first.log.

  - Existing ShippingService, GuiListener, BookMailService, MailboxService and MainThread implement server-thread inventory operations and completion dispatch.
  - MailRepository SQL and MailRepositoryTest verify transactions, conditional claims and independent connections using SQLite 3.50.3.0.
  - Resolved Paper 1.21 API JAR and sources; JDK 21 JDBC and CompletableFuture; existing JUnit 5/Mockito test fixtures.
  - New UI items must never escape into player inventory; limits must be tested across independent writers rather than service-only prechecks.

  Progress: TDD-002 resumed 2026-09-09; 17 draft feature tests failed (work/kotlin-features-red.log). Reviewed PR #3 head 93586004ff66ec79f2e21c98992842823cae2f53 supersedes draft key names and sender-global limits. Tests migrated from byte-verified work/codacy-fix, with Kotlin and deferred-cursor regressions retained.

  Result: 76 tests passed, including SQLite recovery, independent writer limits, deferred marker placement, compiled call-graph audit and all three SPEAR architecture checks. Evidence: work/kotlin-reviewed-green.log and work/kotlin-reviewed-refine.log. PR #3 merged as 8ab052f6662a2218686ab853a8bf97b0e587f6a9; signed-commit protection restored.

- [x] **TDD-003** — Charge combined EnthusiaCurrency balances for postage.
  Tag: TDD
  References: REQ-016, REQ-017; implementation.md Thread ownership and Layer Dependency Rules.
  Evidence:
  - <https://github.com/wsg138/EnthusiaCurrency> at f5626865f8b3f2e7ea8347c74e785c8c295b1249: TokenEconomy implements net.milkbowl.vault.economy.Economy; withdrawPlayer(OfflinePlayer,double) calls CurrencyService.withdrawTotal, which spends virtual bank funds first then physical currency. EconomyResponse.transactionSuccess determines success. depositPlayer credits the virtual bank, including offline refunds. getBalance can be cached; withdrawal performs the authoritative check.
  - VaultAPI 1.7.1 POM resolved from JitPack; net.milkbowl.vault.economy.Economy and net.milkbowl.vault.economy.EconomyResponse are provided by Vault at runtime. Paper org.bukkit.plugin.ServicesManager and org.bukkit.plugin.RegisteredServiceProvider expose provider ownership for selecting EnthusiaCurrency only.
  - Existing io.enthusia.express.infrastructure.gui.ShippingService and io.enthusia.express.infrastructure.util.MainThread retain primary-thread cargo and refund ownership. JUnit org.junit.jupiter.api.Test, Mockito org.mockito.Mockito and org.mockito.MockedStatic are already resolved and running in the 76-test suite.

  - Import evidence: io.enthusia.express.infrastructure.payment.PaymentReceipt, io.enthusia.express.infrastructure.payment.ShippingPayments, java.sql.SQLException, java.util.List, java.util.OptionalLong, java.util.concurrent.CompletableFuture, java.util.logging.Logger, net.milkbowl.vault.economy.Economy, net.milkbowl.vault.economy.EconomyResponse, org.bukkit.Bukkit, org.bukkit.Material, org.bukkit.OfflinePlayer, org.bukkit.entity.Player, org.bukkit.inventory.ItemStack, org.bukkit.plugin.*, org.bukkit.plugin.Plugin, org.bukkit.plugin.java.JavaPlugin, org.junit.jupiter.api.Assertions.*, org.junit.jupiter.api.Test, org.mockito.Mockito.*. Resolved VaultAPI 1.7.1 and Paper APIs, current payment adapter definitions, JDK 21 and existing JUnit/Mockito contracts; all compiled and exercised in work/kotlin-currency-green.log.

  Result: 87 tests passed; work/kotlin-currency-green.log and work/kotlin-currency-refine.log. Includes absent Vault class loading, bank-only and mixed payments, rejected withdrawals, original-provider and offline refunds.

- [x] **TDD-004** — Preserve the package limit during claim compensation.
  Tag: TDD
  References: REQ-018, REQ-013; implementation.md Thread ownership and Behavioral compatibility.
  Evidence:
  - outputs/PR3-LimitCompensationRaceTest.java demonstrates claim -> second limited send -> restore producing two outstanding packages. Current io.enthusia.express.infrastructure.db.MailRepository releases the allowance at claim before io.enthusia.express.infrastructure.gui.MailboxService delivers inventory.
  - Existing java.sql.Connection and org.sqlite.SQLiteConfig IMMEDIATE transaction APIs support serialized schema migration and conditional update. A delivery_pending INTEGER column can retain the reservation without renaming existing statuses or changing serialized payloads.
  - org.junit.jupiter.api.Test, org.junit.jupiter.api.io.TempDir, java.nio.file.Path, java.util.UUID, io.enthusia.express.domain.MailType and existing repository tests are compiled and exercised with real SQLite. io.enthusia.express.application.MailStore will expose delivery acknowledgment to the server-thread adapter.

  Result: 91 tests passed; clean build and all 11 Paper compilation targets passed. Claim acknowledgment prevents allowance release before delivery, and concurrent legacy schema initialization recovers from SQLite contention. Evidence: work/kotlin-claim-reservation-final.log and work/kotlin-release-matrix.log.

- [x] **INFRA-005** — Publish repeatable CI and release evidence.
  Tag: INFRA
  References: REQ-019; implementation.md Verification.
  Evidence:
  - GitHub primary sources github.com/actions/checkout, actions/setup-java and actions/upload-artifact document checkout, Temurin Java 21 and artifact upload inputs. Their v4 tag SHAs were resolved from upstream git refs. Existing Gradle tasks build and verifyPaperCompatibility passed locally; XML reports provide executed test counts.

  Result: 91 tests passed on each of Paper API 1.21, 1.21.8 and 1.21.11; eleven Kotlin compile targets passed. CI uses read-only permissions and pinned action revisions. Release SHA and test totals are recorded in VERIFICATION.md and verification-summary.json. Remote CI execution is checked separately after publication.

- [x] **TDD-006** — Remove offline disk scans from command suggestions.
  Tag: TDD
  References: REQ-020; implementation.md Thread ownership.
  Evidence:
  - Codacy PR 4 discussion_r3972711445 identifies Bukkit.getOfflinePlayers in tab completion. Resolved Paper API and current command source provide getOnlinePlayers/getOfflinePlayerIfCached; existing JUnit and Mockito fixtures verify interaction boundaries.
  - Verified imports: org.junit.jupiter.api.Assertions.assertEquals, org.mockito.Mockito.*, io.enthusia.express.infrastructure.command.MailCommand, io.enthusia.express.infrastructure.gui.MailboxService, io.enthusia.express.infrastructure.gui.ShippingService, io.enthusia.express.infrastructure.hook.CombatLogXHook, io.enthusia.express.infrastructure.mail.BookMailService, java.util.List, org.bukkit.Bukkit, org.bukkit.command.Command, org.bukkit.entity.Player, org.bukkit.plugin.java.JavaPlugin, org.junit.jupiter.api.Test. All resolve on the existing tested Java 21/Paper classpath.

  Result: disk-scan regression failed before the change; all 92 tests pass after it, including three architecture checks. Evidence: work/pr4-command-red.log and work/pr4-command-green.log.

- [x] **INFRA-007** — Refine infrastructure and documentation from PR 4 analysis.
  Tag: INFRA
  References: REQ-021; implementation.md Layer Dependency Rules and Thread ownership.
  Evidence:
  - Codacy PR 4 annotations (work/pr4-annotations.json) and the authenticated issues page identify labeled returns, repeated checks, long handlers, parameter lists and formatting. Existing 92 tests cover SQLite, shipping, mailbox, notifications and payment recovery.
  - Detekt 1.23.8 CLI documentation at <https://detekt.dev/docs/1.23.8/gettingstarted/cli/> and its Maven Central artifact reproduce the relevant rules locally. Kotlin standard annotations kotlin.jvm.JvmName preserve Java accessor names without redundant functions. Existing resolved Paper/JDBC APIs remain unchanged.

  Result: all 92 tests pass after the refactor, including three architecture checks and compiled call-cycle analysis. The focused Detekt pass is down to one claim-completion complexity finding, which is included in TDD-008's delivery recovery split. Evidence: work/pr4-refactor-final.log and work/pr4-detekt-final.xml.

- [x] **TDD-008** — Correct reviewed transaction, delivery and integration recovery paths.
  Tag: TDD
  References: REQ-022, REQ-023, REQ-024, REQ-025; implementation.md Thread ownership and Layer Dependency Rules.
  Evidence:
  - CodeRabbit PR 4 discussions r3972900440, r3972900452, r3972900468 and r3972900491 identify post-commit reset failures, delivery acknowledgment retries, non-public CombatLogX implementations and fractional currency messages.
  - java.sql.Connection, java.sql.SQLException, java.nio.file.Files, java.nio.channels.FileChannel, java.nio.ByteBuffer, java.nio.file.StandardOpenOption, java.util.concurrent.CompletableFuture, java.util.concurrent.Executors and java.util.logging.Logger are Java 21 standard APIs. Existing real SQLite tests support fault injection without new public repository methods.
  - com.github.sirblobman.combatlogx.api.ICombatLogX and com.github.sirblobman.combatlogx.api.manager.ICombatManager are resolved CombatLogX 11.7 API types already exercised in CombatLogXHookTest. The typed adapter remains isolated behind the optional dependency check.
  - Existing Paper Player, Bukkit scheduler, Vault EconomyResponse and JUnit/Mockito APIs are resolved and compiled. java.math.BigDecimal.valueOf(double).stripTrailingZeros().toPlainString() preserves fractional balances in messages.
  - Delivery retry receipts are written outside the server thread after inventory delivery, then acknowledged through the serialized mail port. Persisted receipts are replayed after restart; unknown crash-window claims are never automatically restored or redelivered.

  Result: five new runtime regressions failed before the fixes, then 97 tests passed. Final refinement adds optional-classpath, mailbox-journal ordering and malformed-receipt tests; all 100 tests and all eleven Paper compilation targets pass in work/pr4-clean-matrix.log. Focused Detekt reports zero findings.

- [x] **DOC-009** — Document reviewed method and regression contracts.
  Tag: DOC
  References: REQ-021; implementation.md Verification.
  Evidence: CodeRabbit PR 4 pre-merge report requires 80 percent function docstring coverage. Current tested sources establish lifecycle, threading, compensation and persistence contracts; tests name their regression scenarios. Document these without changing behavior or weakening the check.

  Result: documented 147 production method contracts and 100 regression tests. All 100 tests pass on Paper 1.21, 1.21.8 and 1.21.11; focused Detekt remains clear. Remote review coverage is checked independently after publication.

- [x] **INFRA-010** — Align conflicting documentation rules and refine test-only analysis findings.
  Tag: INFRA
  References: REQ-021; implementation.md Verification.
  Evidence:
  - Published head 32495f4 passes the tested-source comparison. Codacy reports 68 CommentOverPrivateFunction findings introduced by CodeRabbit-required documentation, plus six test-only findings. Preserve method contracts and use a file-scoped exception for the conflicting documentation-style pattern without modifying the shared coding standard; retain correctness, complexity and security gates.
  - Detekt documents CommentOverPrivateFunction as an optional style rule. CodeRabbit requires 80 percent docstring coverage. PMD documents AvoidAccessibilityAlteration; the isolated SQLite reset-failure test deliberately injects a failing connection without widening production visibility. Limit its suppression to that test with the reason recorded inline.
  - Existing imports and layer constants need no external dependencies. Rerun the 100-test suite after test-only refactoring.

  Result: clean build and all 100 tests pass in work/pr4-policy-clean-build.log. The expanded focused Detekt run, including private-comment checking and architecture tests, reports zero findings. The shared Codacy coding standard was not changed. Source-scoped exceptions cover only the documented style conflict and intentional test-only fault injection.

- [x] **TDD-011** — Handle serialization failure and uncached recipients.
  Tag: TDD
  References: REQ-026, REQ-027; implementation.md Verification.
  Evidence: BookMailService.prepareBook invokes ItemCodec.encode without catching serialization failures. MailCommand rejects a cache miss before looking up the UUID. Paper CraftServer 1.21.8 getPlayerUniqueId uses the profile cache and respects proxy/online identity mode; Bukkit 1.21 API exposes this method. Source: [Paper CraftServer](https://raw.githubusercontent.com/PaperMC/Paper/ver/1.21.8/paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java). Perform only UUID lookup on the scheduler worker and use existing io.enthusia.express.infrastructure.util.MainThread for callbacks. Existing org.bukkit, org.mockito, org.junit.jupiter.api, java.util.concurrent, java.util.function and java.util.logging APIs cover the regression and implementation; no new dependency.

  Verified import evidence: `com.lemonappdev.konsist.api.ext.list.withPackage`, `com.lemonappdev.konsist.api.provider.KoAnnotationProvider`, `io.enthusia.express.infrastructure.util.MainThread`, `java.util.UUID`, `java.util.concurrent.CompletableFuture`, `java.util.concurrent.TimeUnit`, `java.util.concurrent.atomic.AtomicReference`, `java.util.function.BiConsumer`, `java.util.logging.Level`, `org.bukkit.OfflinePlayer`, `org.bukkit.configuration.file.YamlConfiguration`, `org.bukkit.inventory.ItemStack`, `org.bukkit.scheduler.BukkitScheduler`, `static org.junit.jupiter.api.Assertions.assertNotNull`. Existing Paper 1.21 API, Java 21 standard library, JUnit/Mockito tests and on-disk Konsist 0.17.3 declarations verified with javap; KoAnnotationProvider and recursive KoDeclarationProvider expose fullyQualifiedName for annotation checks.

  Result: both behavior regressions failed before implementation and passed after it (pr4-second-red.log, pr4-lookup-red.log, pr4-second-green.log). The fully qualified annotation probe was rejected, then removed. Clean build and all eleven API compilation targets pass with 102 tests; focused Detekt reports zero findings using docs/detekt-focused.yml.

- [x] **TDD-012** — Address six gameplay requests from the Medal clip and PR feedback.
  Tag: TDD
  References: REQ-028 through REQ-032; implementation.md Thread ownership.
  Evidence: Medal clip nvDMBX1SU1HspKdnn shows the overflowing inbox title. MailboxService.openPage closes and reopens on navigation. Existing Paper inventory, Bukkit known-player lookup, PlayerJoinEvent, ItemStack serialization, ContainerScanner, MainThread, Text and configuration APIs are already used by this plugin. Existing JUnit and Mockito fixtures cover these exact services. Cache known names once at startup and maintain them on join, leaving completion memory-only. Preserve main-thread GUI ownership and immutable encoded-package comparisons for confirmation. Chat filtering is excluded by the user.

  Verified imports: `java.util.UUID`, `org.bukkit.Bukkit`, `org.bukkit.event.EventHandler`, `org.bukkit.event.Listener`, `org.bukkit.event.player.PlayerJoinEvent`. These existing Java 21 and Paper APIs support the infrastructure-only recipient cache; no dependency was added. Five behavior regressions failed before implementation in gameplay-red.log and gameplay-red-extra.log. Expanded tests cover cargo/price changes, virtual-currency quotes, and nonempty singular/plural notices.

  Result: clean Gradle build and all eleven Paper API compilation targets pass. All 109 tests pass on Paper API 1.21, 1.21.8 and 1.21.11; focused Detekt, Konsist and compiled call-graph checks pass. Evidence: gameplay-clean-matrix.log, gameplay-1.21.8.log, gameplay-1.21.11.log, gameplay-detekt.xml. The final baseline JAR was copied before API override test runs. No live server test was performed.

- [x] **TDD-013** — Add sent history and clearer mailbox category selection.
  Tag: TDD
  References: REQ-033, REQ-034.
  Evidence: MailRepository retains claimed/purged metadata but return-to-sender replaces recipient_name. Preserve the original recipient in an additive column, backfill only recoverable existing records, and index sender/type/date/id. History queries are sender-scoped and bounded to 45 records. Existing java.sql, java.util.UUID, java.util.concurrent.CompletableFuture, org.bukkit.Material, org.bukkit.inventory.ItemStack, org.bukkit.inventory.meta.ItemMeta, JUnit and Mockito APIs support the implementation. New internal domain type io.enthusia.express.domain.SentMailRecord contains a MailRecord and nullable original recipient name; no dependency is added. Existing MailboxServiceTest and real SQLite MailRepositoryTest fixtures verify ownership, pagination, returns, and read-only history. Shared inventory controls preserve slots 1/4/7 for categories and 0/8 for pages.

  Additional verified imports: `io.enthusia.express.domain.MailRecord`, `io.enthusia.express.domain.MailStatus`, `io.enthusia.express.domain.MailType`, `io.enthusia.express.infrastructure.util.ItemCodec`, `java.time.Instant`, `java.time.ZoneOffset`, `java.time.format.DateTimeFormatter`, `org.bukkit.entity.Player`, `org.bukkit.inventory.Inventory`. Java 21 provides UTC date formatting; existing Paper/codec boundaries provide read-only container/book previews. MailboxControls and SentMailDisplay remain infrastructure adapters. sent-red.log demonstrates both missing history and missing selected-category labeling before implementation.

  Result: clean Gradle build, all eleven API compilation targets, and 118 tests on each Paper API 1.21, 1.21.8 and 1.21.11 passed. Focused Detekt, Konsist and compiled call-graph checks passed. Evidence: sent-clean-matrix.log, sent-1.21.8.log, sent-1.21.11.log, sent-detekt.xml. No live server or client test was performed.

- [x] **TDD-014** — Persist recipient block preferences and support optional Nexo GUI assets.
  Tag: TDD
  References: REQ-035, REQ-036.
  Evidence: Existing SQLite IMMEDIATE transactions can serialize block updates with mail insertion; broadcasts already run in one transaction. java.util.UUID, java.util.concurrent.CompletableFuture, java.sql and existing MainThread callbacks preserve ownership. Blocked sends use io.enthusia.express.domain.MailBlockedException; io.enthusia.express.infrastructure.mail.MailBlockService handles command feedback. Nexo's official API documents com.nexomc.nexo.api.NexoItems.itemFromId(String).build(), asynchronous item loading, and packet formatting of glyph/shift tags. Sources: [Nexo API](https://docs.nexomc.com/community-guides/api) and [Nexo settings](https://docs.nexomc.com/configuration/plugin-settings). Resolve items when menus open, clone ItemStacks and preserve GUI control metadata. org.bukkit.plugin.Plugin, org.bukkit.plugin.java.JavaPlugin, org.bukkit.inventory.ItemStack and org.bukkit.Material support the optional adapter. A narrow reflective boundary avoids loading or bundling Nexo when absent; fallback behavior and API invocation are tested independently. Title glyph rendering requires Nexo's packet formatting and resource pack. No art assets are supplied by this plugin.

  Additional import evidence: `io.enthusia.express.application.MailStore`, `io.enthusia.express.domain.MailBlockedException`, `io.enthusia.express.infrastructure.gui.GuiTheme`, `io.enthusia.express.infrastructure.gui.NexoItemSource`, `io.enthusia.express.infrastructure.gui.ReflectiveNexoItems`, `io.enthusia.express.infrastructure.mail.MailBlockService`, `io.enthusia.express.infrastructure.util.MainThread`, `io.enthusia.express.infrastructure.util.Text`, `java.util.List`, `java.util.UUID`, `java.util.concurrent.CompletableFuture`, `java.util.function.BiConsumer`, `java.util.logging.Logger`, `org.bukkit.Bukkit`, `org.bukkit.Material`, `org.bukkit.OfflinePlayer`, `org.bukkit.configuration.file.YamlConfiguration`, `org.bukkit.entity.Player`, `org.bukkit.inventory.ItemStack`, `org.bukkit.plugin.Plugin`, `org.bukkit.plugin.PluginManager`, `org.bukkit.plugin.java.JavaPlugin`, `org.junit.jupiter.api.Test`, `org.mockito.MockedStatic`, `static org.junit.jupiter.api.Assertions.*`, `static org.mockito.Mockito.*`. These are existing Paper/Java 21, JUnit/Mockito, and internal adapter/port declarations verified by compilation and focused behavior tests; no dependency was added.

  Result: behavior regressions failed before implementation (block-red.log, nexo-red.log). Clean build and all eleven API compilation targets passed; 132 tests passed on each Paper API 1.21, 1.21.8 and 1.21.11. Focused Detekt reports zero findings; Konsist and the compiled call-graph gate passed. Evidence: block-clean-matrix.log, block-1.21.8.log, block-1.21.11.log, block-nexo-detekt.xml. Baseline JAR saved before overrides. No live Nexo/Paper server was run.

  Remote refinement: addressed Codacy check 103611195960 (Markdown heading/list spacing, linked API sources, separate test variable declarations). Production code and testing JAR are unchanged.

- [x] **TDD-015** — Resolve the five production-review findings with ownership, recovery and resource-budget regressions.
  Tag: TDD
  References: REQ-037 through REQ-041; implementation.md Thread ownership and Reviewed delivery recovery.
  Evidence: ShippingService.chargeAndSubmit currently calls external payment code before removing cargo. Current EnthusiaCurrency CurrencyService.withdrawTotal fires synchronous events (pinned f5626865f8b3f2e7ea8347c74e785c8c295b1249, review evidence). Existing Paper Player/Inventory, MainThread, ItemCodec, MailStore and SQLite/JUnit/Mockito fixtures exercise those boundaries. DeliveryAcknowledgments already forces and replays files off-thread; extend it for known-undelivered compensation with generation-conditional SQL. Java 21 provides bounded executors and atomic file moves. [SQLite official WAL advisory](https://sqlite.org/wal.html) and [Xerial release](https://github.com/xerial/sqlite-jdbc/releases/tag/3.51.3.0) identify the fixed dependency. Package preview and per-player query tests require bounded summaries and coalescing. No additional framework is introduced.

  Verified import evidence: `io.enthusia.express.application.MailStore`, `io.enthusia.express.domain.MailRecord`, `io.enthusia.express.domain.MailStatus`, `io.enthusia.express.domain.MailType`, `io.enthusia.express.infrastructure.db.DeliveryAcknowledgments`, `io.enthusia.express.infrastructure.db.MailRepository`, `java.nio.ByteBuffer`, `java.nio.channels.FileChannel`, `java.nio.file.AtomicMoveNotSupportedException`, `java.nio.file.Files`, `java.nio.file.Path`, `java.nio.file.StandardCopyOption`, `java.nio.file.StandardOpenOption`, `java.sql.DriverManager`, `java.util.UUID`, `java.util.concurrent.ArrayBlockingQueue`, `java.util.concurrent.CompletableFuture`, `java.util.concurrent.CompletionException`, `java.util.concurrent.RejectedExecutionException`, `java.util.concurrent.ThreadPoolExecutor`, `java.util.concurrent.TimeUnit`, `java.util.logging.Level`, `java.util.logging.Logger`, `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.io.TempDir`, `static org.junit.jupiter.api.Assertions.*`, `static org.mockito.Mockito.*`. Sources: existing plugin application/domain declarations; Java 21 nio, SQL and concurrency APIs already used by the journal and repository; resolved JUnit/Mockito fixtures; SQLite JDBC 3.51.3.0 artifact and native runtime verified by ShadedJarTest.

  Result: all five production-review findings addressed. Red evidence: safety-red.log and original review SQLite probes. Clean baseline build and eleven Paper API compilations passed; 146 tests passed on each API classpath 1.21, 1.21.8 and 1.21.11. Focused Detekt, Konsist and compiled call-graph checks passed. Testing JAR and evidence saved in production-safety-fixes. Live staging remains pending server access and is not represented as completed.

- [x] **TDD-016** — Close review gaps in temporary receipt recovery and ambiguous currency debits.
  Tag: TDD
  References: REQ-042, REQ-043; implementation.md Thread ownership and Reviewed delivery recovery.
  Evidence: PR 5 CodeRabbit findings 3998194048 and 3998194051; ClaimRestorations currently ignores .tmp files; VaultShippingPayments currently catches withdrawal exceptions without retaining an outcome record. Existing Java 21 FileChannel.force, Files, Path, ByteBuffer, StandardOpenOption, UUID, Logger and Level support bounded durable intent metadata. Existing JUnit TempDir, Mockito ordered verification and real SQLite fixtures cover restart and provider callback boundaries. Imports: java.nio.ByteBuffer, java.nio.channels.FileChannel, java.nio.file.Files, java.nio.file.Path, java.nio.file.StandardOpenOption, java.util.UUID, java.util.logging.Logger, java.util.logging.Level, java.io.IOException. Internal PaymentReconciliation will own intent file creation and conservative cleanup.

  Result: three regressions reproduced the gaps before implementation (safety-review-red.log). Revised clean build and eleven Paper API compile targets passed; 149 tests passed on each API classpath 1.21, 1.21.8 and 1.21.11. Focused Detekt reported zero findings; Konsist and call-graph checks passed. Complete temporary claim receipts recover; ambiguous currency withdrawals retain durable operator evidence. Live staging is still pending.

- [x] **INFRA-011** — Verify the Java 25 Paper APIs and retain the baseline distribution.
  Tag: INFRA
  References: REQ-044; implementation.md Verification.
  Evidence: Paper official Maven metadata publishes 26.2.build.123-stable and 26.3-pre-2.build.0-alpha ([official source](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml)), verified 2026-09-13. Paper project setup documents the new coordinate format and Java 25 requirement ([official source](https://docs.papermc.io/paper/dev/project-setup/)). Gradle's compatibility matrix requires 9.1.0 for Java 25 ([official source](https://docs.gradle.org/current/userguide/compatibility.html)). Kotlin 2.3.21 supports Gradle 9.1 ([official source](https://kotlinlang.org/docs/gradle-configure-project.html)); Shadow 9.2.2 supports Gradle 9 ([official source](https://gradleup.com/shadow/)). Mockito 5.20.0 uses Byte Buddy 1.17.7 for Java 25 ([official source](https://github.com/mockito/mockito/releases/tag/v5.20.0)). All four pinned artifact POMs and the Gradle SHA256 were fetched from their official repositories. Existing org.jetbrains.kotlin.gradle.dsl.JvmTarget and Gradle toolchain DSL support per-target compilation. ASM 9.8 adds Java 25 bytecode inspection ([official source](https://asm.ow2.io/versions.html)). No production API or layer change is planned; the bundled Kotlin standard library follows the compiler upgrade. Prerelease API verification does not establish final 26.3 or live integration support.

  Result: clean baseline build and eleven 1.21 API compilations passed. All 149 tests passed on 1.21 / Java 21 and on pinned 26.2 and 26.3-pre-2 API classpaths / Java 25 using the same baseline JAR. No production API changes were needed. Focused Detekt, Konsist and compiled call-cycle checks passed. Wrapper 9.1.0 bootstrap verified. Evidence: paper-26-compatibility baseline-build.log, paper-26.2-build.log, paper-26.3-pre-2-build.log and three test-result directories. Final 26.3 and live staging remain unverified.

- [x] TDD: Durable shipping compensation (REQ-045).
  References: REQ-045; docs/implementation.md shipping compensation.
  Evidence: Existing ShippingService, ShippingPayments, VaultShippingPayments, PaymentReconciliation and MainThread source; java.nio.file atomic movement and FileChannel.force as used by ClaimRestorations; org.junit.jupiter.api and org.mockito existing ShippingServiceTest fixture; org.bukkit scheduler, Player.saveData and inventory API used by current recovery code.
  Evidence: ShippingRecovery uses java.io.ByteArrayOutputStream, java.nio.ByteBuffer, java.nio.channels.FileChannel, java.nio.file.Files, java.nio.file.Path, java.nio.file.StandardCopyOption, java.nio.file.StandardOpenOption, java.util.Base64, java.util.Properties, java.util.UUID and java.util.logging.Logger from the JDK. New tests use existing org.junit.jupiter.api.Test, org.junit.jupiter.api.io.TempDir and org.junit.jupiter.api.Assertions conventions. Integration evidence: CurrencyModerationApi.java in the upstream EnthusiaCurrency repository; lease signatures match the adapter.

  Result: three regression tests first reproduced the recovery defects. All 163 tests, architecture rules, compiled call-cycle audit and shaded-JAR verification pass. Focused Detekt reports zero findings. Local verification used cached Gradle 9.1.0 and Kotlin 2.3.21; GitHub CI verifies the declared Gradle 9.7.1 and Kotlin 2.4.20 toolchain. Live staging remains pending. Verified internal import: io.enthusia.express.infrastructure.payment.ShippingRecovery.

- [x] **TDD-046** — REQ-049 (originally branch-local REQ-046): reproduce hidden-recipient letter and package rejection; use the Paper visibility contract at each recipient gate; verify visible recipients remain rejected and post-payment changes preserve compensation. Source owner: FainNeito/Enthusia-Express main af294dc. No EARS/state helpers present. Local tests are separate from live staff/client acceptance.

Evidence: both hidden-recipient regressions failed on unchanged source, then passed after the visibility adapter. Local Paper 26.2 / Java 25 clean build passed; baseline Java 21 verification could not start because its toolchain is unavailable. Existing real SQLite, architecture, packaging and compensation regressions remain part of the suite. Hosted baseline/API matrix and live acceptance are pending; no production action.
- [x] **INFRA-012** — Review pending PRs and prepare the combined staging release candidate.
  References: REQ-046, REQ-047 and REQ-048; current MailStore/MailRepository, MailboxControls/MailboxService, MapartRepositoryTest and MailToastTest contracts.
  Evidence: bulk-read integration tests cover every inbox page, idempotence and recipient/package isolation; GUI tests cover both text categories and duplicate clicks. The existing Mapart and shipping tests cover token idempotence, generation-guarded claims, configured postage and uncertain inventory saves. Native icon tests cover the stack/template adapter boundary; they do not establish actual client rendering.
  Result: 206 tests and all eleven Paper 1.21 API compilation targets passed before the additional sent-history correction below, with the declared Gradle 9.7.1/Kotlin 2.4.20 toolchain. Two bulk-read Codacy findings were refined without changing behavior. PR #5 is superseded by the safety changes already in main and the follow-up protections in PR #7. Release candidate publication retains live staging, client rendering and final 26.3 as explicit acceptance limits.

- [x] **TDD-017** — Preserve retained-content labels in payload-free sent-history pages.
  Evidence: `MailRepository.listSent` deliberately projects an empty payload to bound page reads; `MailStatus.PURGED` is the authoritative expiration status. Existing `SentMailDisplay` incorrectly interpreted that projection as expired content. `SentMailDisplayTest` uses the real SQLite page query and mocked Paper item metadata to check retained letters, announcements and packages, and a genuinely purged letter.
  Red: `sent-history-red.log` reproduced the false expired label (one failure out of two tests). Engine: card content and book action use purge status, retaining payload-free page queries and recipient isolation. Architecture: no new dependency or mail mutation is introduced.
  Green: the combined release-review suite passes 210 tests, including both sent-history regressions.

- [x] **TDD-018** — Resolve the full CodeRabbit release-review findings.
  Evidence: `EnthusiaCurrencyMovementLocks.bindIfPresent` cached disabled-provider discovery before attempting a usable binding; `MailRepository.expire` applied the museum exclusion to only one OR branch. Existing PluginManager mocks and SQLite fixtures support isolated regressions without adding a dependency.
  Red: `release-review-followup-red.log` reproduced both defects (two failing tests out of ten). Engine: cache an enabled binding attempt only, warn once while disabled, and group the full purge disjunction before excluding museum rows. Green: 210 regressions pass; generation guards, durable receipt boundaries and fail-closed incompatible Currency APIs are preserved.

- [x] **INFRA-013** — Reconcile hidden-recipient privacy with current main.
  Tag: INFRA
  References: REQ-019, REQ-044, REQ-047, REQ-049; implementation.md Layer Dependency Rules, Shipping compensation and Recipient presence privacy.
  Acceptance: PR #8 incorporates main 678292f while retaining museum admission/pause and payment-compensation guards, bulk read, sent history and cosmetic arrivals; personal mail uses sender visibility at every gate. Keep all conflicting regression tests and canonical main's requirement IDs.
  Evidence: merge of 63502af and 678292f conflicts in ShippingService, ShippingServiceTest and appended requirements/tasks. Source inspection identifies the museum bypass and pause checks at confirmation, block lookup and after payment, alongside RecipientPresence. Main already owns REQ-046 through REQ-048, so the unmerged privacy requirement moves to REQ-049. No new imports, dependency, permissions or schema changes.
  Integration evidence: existing CurrencyShippingTest Vault/Mockito callbacks exercise a recipient becoming visible during withdrawal and exactly-once cargo/fee compensation; MapartPostageTest now supplies an online museum target and verifies shared intake never requests a personal recipient session. Existing mocks and java/org.bukkit/net.milkbowl APIs are unchanged. RecipientPresence remains infrastructure-only; no domain/application imports or companion API signatures changed.
  SPEAR: infrastructure reconciliation of existing behavior; prove/engine are not applicable and no historical red/green evidence is invented. Project-local EARS/state helpers remain absent; use the pinned external validator and existing architecture/regression/packaging checks, and record their actual results here. Local mocked tests do not establish live staff visibility, client toast rendering or production acceptance.
  Result: Gradle 9.7.1 / Kotlin 2.4.20 clean builds on Java 25 pass all 214 tests on each pinned Paper 26.3-pre-2 and 26.2 API target, with zero failures/errors/skips; stable ran last. Architecture and shaded packaging tests pass. The external EARS validator passes REQ-049 after adapting the document's level-two header in memory; its full-document run flags unchanged legacy REQ-022 through REQ-025 wording, so whole-document EARS compliance is not claimed. Java 21 baseline and actual-baseline-JAR runtime matrix remain hosted checks, not these locally compiled verification artifacts. No server, configuration or permission changes.

- [x] **TDD-047** — REQ-050 event delivery API for EnthusiaHolidays Secret Santa.

  Tag: TDD

  References: REQ-050, REQ-005, REQ-013, REQ-047.

  Acceptance: token-idempotent event packages (same token → same mail id, no second row; token reused for a different recipient rejected); no postage, limits or blocks; exempt from return/expire/purge; claimable and acknowledged like ordinary packages; restart-safe; items validated (1–27 stacks, nesting limit) and wrapped in one shulker box; `EventDelivery` registered in ServicesManager with JDK/Bukkit-only signatures.

  Evidence: RED was 40 test-compilation errors (missing `EventDelivery`, `EventDeliveryRecord`, `insertEventPackage` and the packager). GREEN: `EventDeliveryRepositoryTest` (8, real SQLite: claimable system package, token idempotency incl. restart and a 6-thread race, cross-recipient token rejection, blocks ignored, never expired/returned/purged, normal claim + acknowledgment, invalid input) and `EventDeliveryServiceTest` (3: validation, single packing, packaging failures as failed futures). Full suite 225 passing (214 before); `clean build verifyPaperCompatibility` passes.

  Progress: complete for automated scope. `ShulkerEventPackager` (real shulker meta and serialization) needs live Paper acceptance together with EnthusiaHolidays.

## TDD-051 — Museum catalog and review lifecycle

Tag: TDD
References: REQ-051 through REQ-055; implementation.md Layer Dependency Rules, Reviewed delivery recovery and Shipping compensation.
Evidence: io.enthusia.express.infrastructure.db.MailRepository initialize/insertMapart/claimMapart/confirmDelivery/markMapartProcessed and MapartRepositoryTest define existing SQL, transaction and claim-generation behavior. io.enthusia.express.application.MailStore preserves asynchronous Java clients. New io.enthusia.express.domain museum records use only java.util.UUID and Kotlin/JDK value types. java.sql and org.sqlite adapters remain infrastructure. Existing org.junit.jupiter.api.Test, TempDir and Assertions fixtures use real SQLite; no new dependency is introduced. ContainerScanner.countMapartItems provides the bounded iterative traversal model. Native dialog API signatures were inspected with JDK25 javap against pinned Paper26.2.133 and official Paper dialog documentation; dialogs never own shipping cargo. Stateful additions must preserve payload-free lists and generation-guarded recovery.
Status: verified for automated scope; live acceptance pending.

TDD-051 proof: museum-red.log reproduced the absent persistent catalog before implementation. Five real-SQLite workflow tests and the eight existing museum tests pass; full build including architecture and call-cycle checks passed (museum-architecture.log). Review remains distinct from cargo delivery, legacy claims remain recoverable and listArtwork omits payload BLOBs. UI integration and live acceptance are separate tasks.

## TDD-056 — Hybrid menus and stale-callback safety

Tag: TDD
References: REQ-056 and REQ-057; implementation.md Thread ownership and Layer Dependency Rules.
Evidence: Paper26.2.133 javap confirms Dialog.create, RegistryBuilderFactory.empty, DialogBase.create, DialogAction.customClick(DialogActionCallback, ClickCallback.Options), ActionButton.create, DialogType.multiAction and TextDialogInput builder signatures. Geyser/Floodgate Cumulus official forms documentation describes builder inputs and validResultHandler. Existing MainThread and Bukkit Scheduler own callbacks; ShippingService retains physical inventories and compensation. New application DialogSessions uses java.util.UUID only; org.junit.jupiter.api tests prove one-shot, timeout and owner isolation.
Status: verified for automated scope; live acceptance pending.

TDD-056 proof: dialog-session-red.log proved the missing callback gate. DialogSessionTest now proves owner, one-shot, expiry and reopen invalidation; non-recursive bytecode and full build pass (hybrid-full-green.log). Navigation routes through existing commands between clicks, with no recursive project call cycle. Native Paper and optional Cumulus adapters require native client staging; their reflective API signatures are inspected, not claimed runtime-accepted. Physical cargo remains in ShippingService. Protocol771 capability threshold is verified against ViaVersion ProtocolVersion.v1_21_6; ViaAPI preserves older-client fallback.

## TDD-058 — Configurable cosmetic messages

Tag: TDD
References: REQ-058; implementation.md Thread ownership and cosmetic arrival contract.
Evidence: NativeMailToast.accept and NativeToastPackets.show are infrastructure-only and consume immutable io.enthusia.express.domain.MailNotification. Paper Audience.sendActionBar(Component) is already present on baseline Player; net.kyori.adventure.text.Component uses literal text. MailToastTest supplies existing org.junit.jupiter.api and org.mockito fixtures. Native advancement frame labels remain client-owned; no resource-pack translation rewrite is introduced.
Status: verified for automated scope; live acceptance pending.

TDD-058 proof: action-bar regression failed before implementation (notification-red.log), then all cosmetic tests pass. Baseline and actual-baseline-JAR tests on pinned final Paper26.3 each pass 242 tests with zero failures/errors/skips; all eleven baseline API compilations pass. Native frame headings remain explicitly client-owned. Cosmetic configuration cannot change mail records or rewards.

## INFRA-059 — Final Paper26.3 profile and delivery evidence

Tag: INFRA
References: REQ-059, REQ-019, REQ-044; implementation.md Verification and Paper26 verification.
Evidence: official Paper Maven metadata fetched directly publishes 26.3.build.205-stable. Existing baselineJar runtime test property keeps Java21 distributable classes in the Java25 runtime verification. The CI matrix adds the final target and uploads the versioned 1.3.0-SNAPSHOT baseline. Source version packaging assertion is updated to the new testing version. No behavioral red/engine steps apply to the build-profile and documentation task; the initial 26.3 run exposed only the old 1.2.1 metadata assertion, now corrected. Full-document EARS parsing recognizes level-two requirements; four legacy clauses are normalized without semantic change. Twelve local SPEAR helper tests pass.
Status: verified for automated scope; live acceptance pending.

Review refinement: dialog-cargo-red.log reproduced missing closeInventory before opening input. DialogMenus now closes through the existing inventory lifecycle before native/form/fallback input. The regression and final 243-test baseline suite pass, including all eleven older API compilation targets. Final Paper26.3 uses the same baseline JAR on Java25. The isolated Paper26.2 probe resolves actual dialog builders without a real client. Preview publication is separate from fresh browser/device QA, which was unavailable.

INFRA-059 result: final profile, artifact packaging assertion and CI matrix updated; repository EARS and twelve helper regressions pass. Canonical Tags PR26 head111154b is green (254 tests, Codacy, artifact and verify); the current credential has pull-only upstream access, so maintainer merge and subsequent fork/network reconciliation remain blocked. No production deployment/activation or claim of client acceptance.

## INFRA-060 — Refine exact-head Codacy findings

References: REQ-051 through REQ-059; implementation.md Layer Dependency Rules.
Evidence: Codacy analysis of3d93ba6 reports100 added issues. Most path warnings concern trusted local CLI/SPEAR fixtures, not plugin inputs; retain validation and narrowly document only genuine false positives. Refactor museum presentation/audit ownership, split long handlers, extract literals and retain compatibility method signatures. No new feature, schema or expected behavior is introduced; behavioral prove/engine steps do not apply to this refinement. The existing243 regressions, all API profiles, no-cycle/layer checks and isolated probe remain the correctness oracle.
Status: local verification complete; exact-head hosted checks and live acceptance tracked separately.

INFRA-060 local refinement: menu ownership/input/commands and audit SQL separated; pure review policy retained. Atomic Java-facing command signatures, stale-result lambda returns, required reflection varargs and trusted local CLI/test paths have narrow documented exceptions (static-review.md). No global rule/gate was disabled. Baseline243 tests, eleven API compilation targets, EARS and twelve helper tests pass after refactor; no compiled call cycles or layer regressions. Final refined-head26.3/probe and hosted Codacy checks are being repeated. Current Tags release-fork PR12 at16a70aa passes verify, artifact, Codacy and codacy-gate with289 local tests; no merge or network pin is implied.

INFRA-060 final local evidence: final Paper26.3 baseline-JAR run repeats243 passing tests; isolated Paper26.2 probe20261019T042649Z-3ac98eeb1c passes at19332f9 with artifact SHA256 f51db8c287afa96e9d662db9f29532247e3a36fb773987d427e472ffa257b55b. Codacy reduced100 findings to2 style-only findings; the misplaced constant comment and missing heading spacing are corrected. Final exact-head status must be verified after push. No real client, production or network-pin acceptance is inferred.
