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

The API is published to GitHub Packages. Only the API - the plugin jar is a server artifact, and
publishing it would invite people to depend on internals that are deliberately `internal`.

```kotlin
repositories {
    maven("https://maven.pkg.github.com/Glance-Studios/Parcel") {
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GPR_USER")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GPR_TOKEN")
        }
    }
}

dependencies {
    compileOnly("com.glance.parcel:parcel-api:0.4.0")
}
```

`compileOnly` - the API classes ship inside the Parcel plugin jar, so shading them into yours would
put two copies on the classpath. Declare Parcel as a dependency in your plugin descriptor so load
order is guaranteed.

The API is **Java**, so you inherit no kotlin-stdlib requirement even though the plugin is Kotlin.

```java

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

### Regions are shared, not copied

A region is geometry, not a feature. Several plugins reference the **same region by key** and each
attach their own meaning to it - the tavern is one shape that happens to be both an ambience zone and
a PvP zone. Edit it once and every consumer sees the change.

So a consumer's config holds a **key**, not a shape:

```yaml
ambience:
  tavern_hum:
    region: parcel:tavern
```

If a feature needs different geometry, make a different region. Divergence is opt-in.

Two consequences you have to handle:

- **Listen for `RegionModifyEvent`** and drop any derived state - cached meshes, spawned visualisers,
  per-player bookkeeping. Someone else's edit is your edit. Membership fixes itself; the tracker
  re-evaluates and fires the enter/exit events for you.
- **`RegionDeleteEvent` is cancellable.** Cancel if you genuinely cannot function without the region;
  otherwise just drop your reference. Before deleting, `RegionManager.usagesOf(region)` asks every
  plugin what it is using the region for, so a confirmation prompt can show real consequences. It is
  a question rather than a registry, so it cannot go stale.

### The selection handoff

A builder draws a shape in game with the marquee tool, and it becomes a region without any plugin
implementing selection itself.

```java
// create a new region and clear the selection
Region region = Parcel.api().selections().promote(player, new NamespacedKey(this, "tavern"));

// or reshape one that already exists - every consumer bound to it sees the new shape
Parcel.api().selections().load(player, region);   // ... builder edits ...
selection.applyTo(region);
```

`promote` clears the selection deliberately: a Parcel selection accumulates parts, so leaving it
populated means the next region a builder draws silently inherits the last one's shape.

### Showing a region

`renders()` shows and hides a region's visual - the panels or wireframe it was styled with.

```java
if (!Parcel.api().renders().isRendering(key)) {
    Parcel.api().renders().render(region, player);
}
Parcel.api().renders().hide(key);
```

It exists because the only alternative was dispatching `/parcel render`, which is a **toggle** -
and with no way to ask what is currently rendering, a consumer could only flip. Any flip it had not
made itself put the two out of step. This lets callers set the state they want.

Rendering is **per region, not per player**. Panels are real display entities, so a region is
either rendered or it is not; the `viewer` argument decides who sees it and where a cross-section
plane sits, not whether a second caller gets their own copy.

Added in 0.2.0 - feature-detect with `apiVersion()` if you need to support 0.1.0 as well.

### What a builder is working on

Creating a region **marks** it for that player, and `marked()` exposes which:

```java
NamespacedKey key = Parcel.api().regions().marked(player);
```

Called marking rather than selection on purpose: the marquee already owns "selection" for the
transient corners a builder is assembling, and two meanings of selected is a collision waiting to be
misread.

A pointer and nothing more - no lock, no ownership. Two builders can have the same region marked, and
it does not stop anyone editing it.

It is there so a consumer can narrow its own tab completion the way Parcel narrows its own: if
someone has a region marked, that is overwhelmingly the one they mean next. Motif uses it for
exactly that.

Added in 0.3.0.

### Flat regions are drawn as a cross-section

A flat region spans the world's full height, so its true outline includes four verticals running
from bedrock to the build limit. Correct, useless to look at, and they bury whatever you were
trying to see.

Both particle visualisers therefore draw flat regions as a **footprint at the viewer's own height**,
following them as they move - the selection outline and the wireframe alike. Membership is
untouched: a player at Y 200 is still inside a region whose outline is drawn at their feet. Only the
picture changes.

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

Java 21. The finished plugin is copied to **`target/parcel-paper-<version>.jar`** at the repo root -
one predictable path to deploy from, rather than reaching into `parcel-paper/build/libs`.

`target/` is generated and gitignored; the build overwrites it every time.

Drop that jar into a Paper 1.21.11 server's `plugins/` folder. Nothing else is required - the
runtime libraries (kotlin-stdlib, Cloud) are resolved on first start by the plugin's bootstrap
loader rather than being shaded in.

### First run

`config.yml` is written on first start and **kept up to date on later ones**: if a version adds
options, the file is rewritten from the shipped default with your values re-applied, and the
previous copy saved as `config.yml.bak`. New options therefore arrive with their documentation
intact. The trade is that the file is re-ordered to match the default and any comments you added
yourself are lost - hence the backup.

Everything lives under `plugins/Parcel/`:

| | |
|---|---|
| `config.yml` | options, heavily commented |
| `regions/<namespace>/<key>.yml` | the regions themselves, purely geometric |
| `history/<namespace>/<key>.yml` | previous shapes, for `/parcel undo` |
| `styles.yml` | per-region colour and render settings |

Regions, history and styles are separate on purpose: a region's file stays current-state only, so a
corrupt or deleted style or history can never damage the geometry it describes.

## Learning it

`/parcel help` opens an in-game guide. It leads with the concepts rather than the command list,
because the commands are the easy part - a region being a *list of parts* rather than a shape, and
the difference between `apply` and `append`, are what actually catch people out.
