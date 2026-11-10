# RubySteal - Resource Pack Guide

File: `RubySteal-ResourcePack.zip` (Minecraft 1.21.4, pack_format 46).
It uses the 1.21.4 `item_model` system: each Ruby has `item_model = rubysteal:ruby_level_N`.
Contents: `assets/rubysteal/items/*.json`, `models/item/*.json`, `textures/item/*.png` (13 items:
ruby_level_1..10, ruby_heart, ruby_fragment, enchanted_ruby), `pack.mcmeta`, `pack.png`.

## Test locally
Copy the ZIP into `.minecraft/resourcepacks/`, enable it, join the server. Rubies show their custom icons.
Without the pack players see a normal Echo Shard with the correct name and lore.

## Host it for the server
No URL is bundled. You need a **direct download link** that ends in the file (not a web page):
* GitHub: attach the ZIP to a Release and copy the asset link, or use a raw file link.
* Any web host / CDN / Discord-independent file host that serves the file directly.
* Hosting panels often offer a "resource pack" file host.

## Configure
1. Get the SHA-1 of the exact ZIP you uploaded:
   * Linux/macOS: `sha1sum RubySteal-ResourcePack.zip`
   * Windows: `certutil -hashfile RubySteal-ResourcePack.zip SHA1`
   Reference value for the ZIP shipped with this package: `77556043d85a9cdd7e0c0062f49fea98592b37cc`
   (it changes if you rebuild or edit the ZIP - always hash the file you host).
2. Edit `config.yml`:
```
resource-pack:
  enabled: true
  url: "https://your-host/RubySteal-ResourcePack.zip"
  sha1: "<40 hex chars>"
  prompt: "<red>RubySteal</red> needs its resource pack."
  required: false      # true = refuse/failed download -> kicked
```
3. `/ruby reload`, rejoin. If you update the pack, change the file **and** the SHA-1; clients re-download automatically.

Do not put the pack into `server.properties` as well - RubySteal sends it itself.

## Editing the art
`tools/gen_pack.py` redraws all textures (16x16, nearest-neighbour friendly). Run `python3 tools/gen_pack.py pack`
(needs Pillow), then re-zip the `pack` folder contents (pack.mcmeta must be at the ZIP r
oot).
