# Optional Nexo GUI textures

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
