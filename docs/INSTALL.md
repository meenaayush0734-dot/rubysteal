# RubySteal - Installation & Build Guide

Requirements: Paper 1.21.4 server, Java 21.

## 1. Build the JAR
This package contains the full source. The JAR has to be compiled once against the Paper API
(it is downloaded automatically by Gradle).

**Option A - on a computer** (Gradle 8.10+ and JDK 21 installed):
```
cd RubySteal
gradle build
```
Result: `build/libs/RubySteal.jar`

**Option B - without any local tools (works from a phone):**
1. Create a GitHub repository and upload the contents of this folder (the `.github` folder included).
2. Open the **Actions** tab -> *Build RubySteal* -> run it (it also runs on every push).
3. Download the `RubySteal-jar` artifact -> it contains `RubySteal.jar`.

## 2. Install
1. Put `RubySteal.jar` into the server's `plugins/` folder.
2. Start the server once - `plugins/RubySteal/config.yml` and `rubysteal.db` are created.
3. Host the resource pack and fill in the `resource-pack` section (see RESOURCEPACK.md).
4. `/ruby reload` after any config change.

SQLite works out of the box. For MySQL set `database.type: mysql` and fill the `mysql` block
(restart required). Paper already ships both JDBC drivers.

## 3. Folder layout
```
RubySteal/
  build.gradle.kts, settings.gradle.kts
  src/main/java/dev/rubysteal/...   (all source)
  src/main/resources/plugin.yml, config.yml
  tools/gen_pack.py                  (regenerates the pixel-art pack)
  docs/                              (these guides)
  pack/                              (resource pack sourc
  e) 
```
