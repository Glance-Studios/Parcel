# Parcel commands

Every command, its permission and what it does.

`/parcel help` opens the same material as a book in game, ordered as a tutorial rather than as a
list. This page is the reference: complete, alphabetical within each group, and easier to search.

`/marquee` and `/mq` are the same command. Where two words are separated by `|` either one works.

## Permissions

| Node | Grants |
|---|---|
| `parcel.view` | Looking: help, menu, list, info, show, render, mark |
| `parcel.edit` | Everything that changes a region, plus the whole marquee tool |
| `parcel.admin` | `reload` and the calibration tools |

`parcel.edit` does not imply `parcel.view`. Grant both.

## The wand

`/mq wand` gives it. It is a golden axe rather than a wooden one so it cannot collide with
WorldEdit, and it is identified by a hidden tag rather than by material or name, so a renamed item
cannot impersonate it and an ordinary axe is unaffected.

| Action | Effect |
|---|---|
| Left click a block | Mark corner 1 |
| Right click a block | Mark corner 2 |
| Sneak + left click | Add the marked box to the selection |
| Sneak + right click | Carve the marked box out of the selection |

Committing works pointing at nothing, on purpose: you are usually stood inside the box you just
outlined with no block in reach. Marking a corner does need a block, since there is nothing else to
mark.

The item's name tracks its state: `marked <region>` when a region is marked, otherwise
`selecting ...` with the corners so far or the size once both are set.

## Selection: `/marquee`, `/mq`

A selection is transient scratch space. It belongs to you, is never saved on its own, and is what
`create` and `apply` read from.

| Command | Permission | What it does |
|---|---|---|
| `/mq` | `parcel.edit` | Summary of the selection and the commands for it |
| `/mq wand`, `/mq tool` | `parcel.edit` | Gives the selection wand |
| `/mq pos1` | `parcel.edit` | Mark corner 1 at your feet |
| `/mq pos2` | `parcel.edit` | Mark corner 2 at your feet |
| `/mq mode <mode>` | `parcel.edit` | `FLAT` (footprint, full world height) or `VOLUME` (uses both corners' Y) |
| `/mq add` | `parcel.edit` | Add the marked box as a part |
| `/mq carve` | `parcel.edit` | Subtract the marked box as a part |
| `/mq undo` | `parcel.edit` | Pop the last committed part |
| `/mq info` | `parcel.edit` | Size, part count and mode |
| `/mq cancel` | `parcel.edit` | Drop the two marked corners, keeping committed parts |
| `/mq deselect`, `/mq reset` | `parcel.edit` | Throw the whole selection away |
| `/mq clear` | `parcel.edit` | Unmark the marked region, leaving the selection alone |
| `/mq create <name>`, `/mq save <name>` | `parcel.edit` | Save the selection as a new region |
| `/mq apply <name>` | `parcel.edit` | Replace an existing region's shape with the selection |
| `/mq append <name>` | `parcel.edit` | Add the selection's parts onto an existing region |
| `/mq load <name>` | `parcel.edit` | Copy a region's parts into your selection to edit |

`deselect` and `clear` are different things and the names are load bearing. `deselect` empties the
transient selection. `clear` releases the *marked region*, which is the saved region your commands
default to.

## Regions: `/parcel`

| Command | Permission | What it does |
|---|---|---|
| `/parcel` | `parcel.view` | Command summary in chat |
| `/parcel help`, `/parcel guide` | `parcel.view` | The guide, as a book |
| `/parcel help more`, `/parcel help shapes` | `parcel.view` | The second book: shapes, meshing, the API |
| `/parcel menu`, `/parcel browse` | `parcel.view` | Browse regions in a GUI |
| `/parcel list [namespace]` | `parcel.view` | Every region, or one namespace's |
| `/parcel info <name>` | `parcel.view` | Parts, bounds, world, block count |
| `/parcel create <name>`, `/parcel save <name>` | `parcel.edit` | Save the selection as a new region |
| `/parcel apply <name>` | `parcel.edit` | Replace a region's shape with the selection |
| `/parcel append <name>` | `parcel.edit` | Add the selection's parts onto a region |
| `/parcel load <name>` | `parcel.edit` | Copy a region into your selection to edit |
| `/parcel undo <name>` | `parcel.edit` | Pop a region's last part. Ten steps of history, kept on disk |
| `/parcel delete <name>` | `parcel.edit` | Asks first, and says what is using it |
| `/parcel delete <name> confirm` | `parcel.edit` | Actually deletes |
| `/parcel restore`, `/parcel undelete` | `parcel.edit` | Put the last deleted region back |
| `/parcel mark <name>` | `parcel.view` | Mark a region, so other commands default to it |
| `/parcel unmark` | `parcel.view` | Release the marked region |
| `/parcel goto <name>` | `parcel.edit` | Teleport to it |
| `/parcel show <name>` | `parcel.view` | Toggle a particle outline of its bounds |
| `/parcel render <name>` | `parcel.view` | Toggle solid panels over its surface |
| `/parcel render` | `parcel.view` | Render every region in the world you are in |
| `/parcel hide` | `parcel.view` | Hide every render, panels and wireframes alike |
| `/parcel follow [name]`, `/parcel freeze [name]` | `parcel.edit` | Toggle whether a flat region's plane rides under you |
| `/parcel style <name>` | `parcel.edit` | Colour, opacity, primitive and plane height, with sliders |
| `/parcel style` | `parcel.edit` | The same, for the default every region inherits |
| `/parcel style <name> clear` | `parcel.edit` | Drop a region's own style, so it inherits again |
| `/parcel reload` | `parcel.admin` | Reload config and regions from disk |

`restore` is one step and lives in memory only, so it does not survive a restart. It is a safety net
for the seconds after a slip, not a soft delete, and it says so when it offers itself.

### Style resolution

A region is drawn with the first of these that exists:

1. its own style, from `/parcel style <name>`
2. the saved default, from `/parcel style`
3. `panels.colour` and friends in `config.yml`

Saving the default immediately redraws everything on screen that inherits it.

### Names and namespaces

Names are namespaced keys. A bare `tavern` means `parcel:tavern`; write `myplugin:tavern` for
another namespace. Tab completion offers the full form.

## Dev tools

Off unless `dev-tools: true` in `config.yml`. With it false the commands are never registered, so
they do not appear in help or tab completion.

| Command | Permission | What it does |
|---|---|---|
| `/parcel calibrate` | `parcel.admin` | Start measuring text display panel constants |
| `/parcel calibrate step <tool> <amount>` | `parcel.admin` | Nudge one constant |
| `/parcel calibrate done` | `parcel.admin` | Finish and print the values for `config.yml` |
| `/parcel calibrate cancel` | `parcel.admin` | Abandon the session |

These exist for the version bumps that change font metrics. See the panel constants in `config.yml`
for what they measure and why the numbers land on exact eighths.
