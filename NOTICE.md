# Notices

## The plugin
The code of CA Planner, and every table in it except the one named below, is under the BSD 2-Clause
License (see `LICENSE`).

## Text from the Old School RuneScape Wiki
One file, `src/main/resources/com/caplanner/howto-wiki.tsv`, is **not** under the plugin's licence. It holds
text about each Combat Achievement task taken from the task's page on the
[Old School RuneScape Wiki](https://oldschool.runescape.wiki), written by the wiki's contributors, and is
used under the [Creative Commons Attribution-NonCommercial-ShareAlike 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/)
licence (CC BY-NC-SA 3.0).

- **Changes made:** the text was shortened and stripped of wiki markup. Nothing was added to it.
- **Source of each line:** the page of the task of that name, `https://oldschool.runescape.wiki/w/<task name>`.
  The plugin shows the credit and a link to that page wherever it shows the text.
- **That file stays under CC BY-NC-SA 3.0**, changed or not, and may not be used commercially. It is kept
  apart from the code and from the plugin's own notes (`howto-own.tsv`) so that it can be taken out, or
  swapped, without touching anything else.

## Facts read from the wiki
Other tables hold facts read from the wiki's pages (monster statistics, level requirements, how many of a
supply an inventory carries, where a boss is). They hold numbers and names, not the wiki's writing. Some of the
places a boss is at are the ones the GPS plugin lists under the boss's name. The tiles the plugin can mark for the
Phantom Muspah are the ones the wiki's tile markers give for that fight.

## Tiles
The tiles the plugin can mark (`tiles.tsv`) are positions in the game. Most were placed by the plugin's authors. Those of three
fights are tile sets widely shared among players, used and in part changed by the authors:

- **Duke Sucellus:** the "Awakened Duke" set of the [Tile Packs](https://github.com/TrevorMDev/tile-packs) plugin (BSD 2-Clause),
  from Red Eye Jedi's awakened Duke Sucellus guide; the numbered tiles are also in a picture on the OSRS Wiki.
- **Whisperer:** a set of tile markers shared by players (rentry.co/ud7mb7mr).
- **Leviathan:** the authors' own tiles for the stun-and-run method of Red Eye Jedi's awakened Leviathan guide.
- **Phantom Muspah:** the tiles the OSRS Wiki's tile markers give for that fight.

## Not affiliated
This plugin is not affiliated with or endorsed by Jagex or the Old School RuneScape Wiki.
