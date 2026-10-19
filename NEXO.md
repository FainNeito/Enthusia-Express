# Optional Nexo GUI textures

## Approved Post Office profile

`assets/nexo-postoffice` contains original red/gold/cream artwork, seven glyph definitions, seventeen item textures and `express-profile.json`. Install its `glyphs`, `items` and `pack` contents additively under Nexo after checking IDs for collisions. Nexo allocates item-model identifiers; this profile does not hard-code another pack's model data. Never replace the full existing pack with this subset.

Apply the flat paths in `express-profile.json` to the existing Express configuration without replacing unrelated settings. `gui.nexo.post-office` is opt-in and defaults to false. Build/reload the TEST pack and activate the tested Express JAR before acceptance. GUI title glyphs use a dedicated font and the existing Nexo inventory-title formatting. Seven glyphs and seventeen item models must resolve; the layout additionally checks all six inventory backgrounds before selecting custom navigation. Java viewers must have an accepted server pack. Backend Floodgate viewers retain supported forms/vanilla controls.

Home cards route every underlying slot to the same action. Curator access is still permission-gated; absent curator controls have no action. My submissions and curator queue remain separate. Museum catalogs reserve 27 entry slots and independent previous/next/filter/home controls. The skin never receives package ownership. Shipping retains destination4, cancel11, cargo13 and confirm15, the tagged placement marker, unchanged-payload quote and second-click payment semantics. There are no decorative cargo filler items.

With this profile, Java navigation uses themed chest menus; metadata entry still uses native dialog inputs, including a 42-pixel postal banner when available. Native buttons retain client styling. Missing banner resolution preserves ordinary input text. Individual missing item assets retain vanilla controls. Declined packs or missing critical backgrounds retain the standard presentation. Test actual pack acceptance and font alignment; a generated PNG is not proof of client behavior.

Regenerate original assets with `tools/build_postoffice_assets.py` using Pillow and an official client JAR selected by `MINECRAFT_CLIENT_JAR`. Asset generation is a developer tool, not a plugin build/runtime dependency. All generated PNGs/configuration are committed; `PostOfficeAssetsTest` validates registry/texture/font bindings in the normal Gradle build. Minecraft bitmap lettering is rendered into the original artwork; no client binaries or full third-party Nexo pack are distributed.

Current presentation references remain owner-private: [mockups](https://express-museum-dialogs-preview-20261018.awareyak.chatgpt.site/nexo.html). Live TEST screenshots and exact deployment evidence must be recorded separately after activation. Production is unchanged.

Install Nexo and its server resource pack, then set `gui.nexo.enabled: true` in EnthusiaExpress's config. Restart EnthusiaExpress after changing its configuration. Blank IDs and unavailable items use the normal Minecraft controls. No Nexo dependency or texture assets are bundled.

Example (replace these illustrative IDs with assets you have created in Nexo):

```yaml
gui:
  nexo:
    enabled: true
    titles:
      mailbox: '<shift:-8><glyph:mail_background>'
      shipping: '<shift:-8><glyph:shipping_background>'
    icons:
      mailbox:
        packages: mail_packages
        packages-selected: mail_packages_selected
        letters: mail_letters
        letters-selected: mail_letters_selected
        announcements: mail_announcements
        announcements-selected: mail_announcements_selected
      shipping:
        placeholder: mail_package_slot
        quote: mail_postage
        confirm: mail_send
```

The shipped config lists every control, including page navigation and Inbox/Sent buttons. Titles require Nexo's inventory-title packet formatting and a client with the resource pack. Align your background to the existing 54-slot mailbox and 27-slot shipping layout; this integration does not move slots. Use the selected category icons to retain a clear active category. One stable mailbox title is used across pages and categories to preserve cursor position. Long custom titles and glyph offsets are the resource-pack author's responsibility.

Icons resolve on each menu render so Nexo's asynchronous loading and item reloads are respected. The plugin clones the item and supplies its own functional name, lore and control tags. Unknown IDs or incompatible item APIs log a warning once per ID and fall back to vanilla controls. The shipping marker is decorative and cannot be taken or shipped.

Reference: [Nexo API](https://docs.nexomc.com/community-guides/api), [Nexo packet formatting settings](https://docs.nexomc.com/configuration/plugin-settings).

Automated tests exercise the optional API boundary and fallbacks. Actual textures, glyph alignment and the installed Nexo version still require a server/client test.
