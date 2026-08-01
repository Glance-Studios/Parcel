# Parcel

Multi-region custom shape system for Paper, with an in-game editor.

A region is not a shape. It is an **ordered list of parts**, each one a shape plus `ADD` or
`SUBTRACT`, evaluated in order with last writer wins. That single decision buys a lot:

- **Custom shapes** come from composing boxes rather than from a bespoke polygon format.
- **Undo is exact and free** - pop the last part. Nothing was ever baked into a block set.
- **Editing stays non-destructive** - any part can be removed later, not just the newest.
- **Subtraction needs no special geometry code.** A carved hole is just blocks that evaluated
  false, and the walls of the hole fall out of the mesher as ordinary exposed faces.

## Modules

| Module | Language | What it is |
|---|---|---|
| `parcel-api` | Java 21 | Pure interfaces. `paper-api` compileOnly and nothing else. |
| `parcel-paper` | Kotlin | The plugin. |

The API is Java so consumers inherit no kotlin-stdlib requirement. The implementation is Kotlin and
gets the stdlib at runtime through Paper's library loader, not by shading.

## Using it from another plugin

```java
// compileOnly com.glance.parcel:parcel-api

Region region = Parcel.api().regions().get(new NamespacedKey(this, "tavern"));
boolean inside = region.contains(player.getLocation());
```

Region membership is tracked centrally, once per tick for every player, and published as events -
so consumers never write their own containment loop:

```java
@EventHandler
public void onEnter(RegionEnterEvent event) { ... }
```

Saved regions load asynchronously, so wait for `ParcelReadyEvent` before querying on startup.

### The selection handoff

The point of the split: a builder draws a shape in game with the marquee tool, and your plugin
turns it into one of its own regions without implementing selection at all.

```java
Selection selection = Parcel.api().selections().of(player);
Region region = selection.toRegion(new NamespacedKey(this, "tavern"));
```

Regions are keyed by `NamespacedKey`, so two consumers can never collide.

## Meshing

`Region.mesh()` returns the region's exposed surface as merged rectangles. Interior faces between
touching parts are removed, and coplanar neighbours are merged - two flush boxes with identical
cross-sections mesh to 6 quads, not 12.

This is exact rather than approximate, because a selection is always whole blocks, so there is no
sub-block geometry to lose. Cost is proportional to volume, so it is an edit-time operation and is
cached until the region changes.

The output is renderer-agnostic. Parcel's own visualiser turns each quad into a display panel, but
consumers are free to draw them however they like.

## Building

```
./gradlew build
```

Java 21. The plugin jar lands in `parcel-paper/build/libs/`.
