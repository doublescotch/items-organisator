<div align="center">
<h1>🗂️ Items Organisator</h1>
<h3>Rebuild your creative inventory from inside the game.</h3>
<p><b>Curate what your players can obtain — without ever opening a config file.</b></p>
<p>
<img src="https://img.shields.io/badge/Minecraft-1.20.1-brightgreen?style=for-the-badge" alt="Minecraft 1.20.1">
<img src="https://img.shields.io/badge/Loader-Forge-e04e14?style=for-the-badge" alt="Forge">
<img src="https://img.shields.io/badge/Java-17-007396?style=for-the-badge" alt="Java 17">
</p>
<p>
<img src="https://img.shields.io/badge/Side-Client%20%2B%20Server-blueviolet?style=for-the-badge" alt="Client and Server">
<img src="https://img.shields.io/badge/License-MIT-lightgrey?style=for-the-badge" alt="MIT">
</p>
<p><b>No required dependencies.</b></p>
</div>

---

<br>

## ❓ The problem

<br>

Install 150 mods and the creative inventory stops being usable.

Fifteen pages of tabs. Items grouped by *who made them* rather than *what they are*. Half of them things you never wanted in your pack to begin with.

<br>

Good tools already exist for **whole tabs** — [Creative Tab Organizer](https://www.curseforge.com/minecraft/mc-mods/creative-tab-organizer) reorders and hides them from the creative screen, [MoreCreativeTabs](https://modrinth.com/mod/morecreativetabs) composes new ones from JSON. Both are worth having.

<br>

But a pack is not curated at the granularity of a tab. It is curated **one item at a time**:

<br>

- *this gadget is buried in a mod's tab where nobody will look for it — it belongs with the tools*

- *this is the fourth copper ore in the pack — three of them should not exist at all*

<br>

Neither of those decisions is a tab-level decision. And no existing tool moves an individual item, or does anything about it outside creative mode.

<br>

That idea is not new. **[CreativeTabsTweaker](https://www.curseforge.com/minecraft/mc-mods/creative-tabs-tweaker)** did exactly it — remove tabs, add your own, move items between them — back on **1.12.2**, through a JSON file you wrote by hand. It never made the jump to modern versions, where creative tabs became generated content rather than a mutable field on each item.

<br>

**Items Organisator picks that idea back up**, brings it to 1.20.1, replaces the hand-written JSON with two mouse gestures — and makes the removals real outside creative mode.

<br>

> ### 🖱️ That is the gap Items Organisator fills.
>
> **Move any item to any tab with two mouse gestures — and make the removals actually stick, in survival, in JEI, in EMI, on your server.**

<br>

<!-- SCREENSHOT: the selector open over the creative screen -->

<br>

---

<br>

## ✨ Features

<br>

### 🖱️ Reorganize creative tabs, in game

<br>

| Gesture | Result |
|:--|:--|
| **Right-click** a tab header | *Move the contents of this tab to…* |
| **Shift + right-click** an item | *Move this item to…* |

<br>

Both open the same selector: a scrollable list of **every creative tab** — vanilla and modded — with a **live search box**.

<br>

That design is deliberate.

With fifteen pages of tabs, drag-and-drop is physically impossible: you cannot see the destination while you drag. Typing three letters takes a second.

<br>

✅ &nbsp; Changes apply **instantly** — tabs rebuild in place, you stay on the screen you were on. No reload, no rejoin.

✅ &nbsp; Rules are written to a **clean, sorted JSON** you ship with your pack.

✅ &nbsp; **Emptied tabs disappear** — move a mod's whole tab away and its empty header stops taking up space; pages recount themselves.

<br>
<br>

---

<br>

### 🙈 The Hidden tier

#### *Remove items — and keep your master key*

<br>

Send an item, or a whole tab, to **Hidden** and it leaves your pack:

<br>

- ❌ &nbsp; Gone from every creative tab, gone from **JEI** and **EMI**

- ❌ &nbsp; Recipes disabled, unobtainable in survival — inventories purged, pickups blocked, dropped items dissolve, use and attack blocked

<br>

**…except for you.**

A barrier-icon tab, visible only **in creative with permission level 2+**, still holds every hidden item — so an admin can pull one out and use it normally: testing a block, checking a tooltip, debugging a recipe.

<br>

> ### ⚠️ What an admin **cannot** do is put it back into the world.
>
>
> Thrown items dissolve &nbsp;·&nbsp; block placement is refused &nbsp;·&nbsp; containers are stripped when opened
>
>
> Hidden items live in creative-admin inventories and **nowhere else**.
>
> No leak path. No accidental gift. No duplicate slipping into survival.

<br>

<!-- SCREENSHOT: the Hidden tab, barrier icon, last position -->

<br>
<br>

---

<br>

### 🩹 Bulk-hide, then rescue the exceptions

<br>

The realistic workflow, supported end to end:

<br>

**1.** &nbsp; A mod adds a **300-item tab** and you want three of them → right-click its header → **Hidden**.

&nbsp; &nbsp; &nbsp; Every item is hidden individually, so the rule file stays explicit and readable.

<br>

**2.** &nbsp; Open the **Hidden tab** → **Shift + right-click** the three keepers → send each to the category of your choice.

<br>

**3.** &nbsp; Right-click the **Hidden tab header** → everything you have hidden, **grouped by the tab it came from**, with one-click *restore this whole group*.

<br>

Changed your mind about a tab you moved?

Right-click the **destination** tab: incoming rules are listed at the top of the selector, each with a **✕** to cancel. Rules pointing at mods you have since removed appear there too, and clean up the same way.

<br>
<br>

---

<br>

### ⛔ The blacklist

#### *Total eradication*

<br>

A second, harsher tier — with regular-expression support:

<br>

```json
"blacklisted": [
  "somemod:broken_item",
  "!somemod:.*_deprecated"
]
```

<br>

Blacklisted items are erased for **everyone, admins included**.

No tab. No recipe viewer. No recipe. No inventory. No exception.

*For the items that should simply not exist.*

<br>
<br>

---

<br>

### 🔗 Recipes: the chain reaction, handled

<br>

Hiding an item removes the recipes that **produce** it.

But the recipes that **consume** it stay — visible in JEI/EMI, and impossible to craft. Those dead branches are how a curated pack starts generating bug reports.

<br>

Two optional levels deal with it:

<br>

```json
"removeRecipesUsingHidden": false,   // also drop recipes that CONSUME a restricted item

"cascadeRecipeRemoval":     false,   // propagate: items left with no recipe become
                                     // unobtainable, so recipes using them fall too

"cascadeAbortPercent":      33       // safety valve — see below
```

<br>

An ingredient that accepts several items (a tag) only counts as broken when **every** alternative is restricted — no false positives on `#planks` because you hid one exotic wood.

<br>

> ### 📋 The audit line
>
>
> Whatever you enable, the mod writes a summary to the log: **how many recipes were removed**, and **which items ended up with no recipe at all**.
>
>
> Read it once after setting up your rules. It is the fastest way to see what your curation actually did to progression.

<br>
<br>

---

<br>

## ⚠️ Guardrails

### *Be sparing with what you remove*

<br>

Two hard limits are built in — and one rule of thumb is on you.

<br>

### 🛡️ Natural terrain and essentials cannot be hidden

<br>

Dirt, stone, sand, logs, planks, leaves, gravel, the crafting table — the world is *made* of them.

Hiding one would mean every block a player mines drops an item that instantly dissolves.

<br>

The attempt is refused in the UI with a message, and refused again if you write it into the JSON by hand.

The protected set is tag-based and editable — `protectedTags` / `protectedItems` — with an explicit `allowProtectedOverride` for authors who really mean it.

<br>
<br>

### 🚨 The cascade has a safety valve

<br>

If propagation would remove more than `cascadeAbortPercent` of *all* the recipes in the game (**33%** by default), it is **aborted entirely**.

The mod falls back to direct removal and logs a loud error naming the problem.

<br>

*One unlucky rule can no longer gut a pack silently on a live server.*

<br>
<br>

### 🌿 The rule of thumb

<br>

**Restrict the *leaves* of the crafting tree, not its *trunk*.**

<br>

A decorative variant, a duplicate ore, an overpowered gadget — safe.

A base material, a fuel, a common ingredient — expect half your recipe book to follow it out.

<br>

When in doubt: hide it, read the audit line, and undo it in one click if the number surprises you.

<br>

> ### 💚 Good news for the nervous
>
>
> Hiding an item **never touches blocks already placed in the world**.
>
> Only the item form is affected — no terrain rewritten, no chunk edited, nothing deleted from anyone's builds.

<br>
<br>

---

<br>

## 🧩 Pack-proof by design

<br>

| | |
|:--|:--|
| 🔌 &nbsp; **Absent mods** | References to mods you removed are **skipped silently** — no crash, no error spam — and reactivate if the mod ever comes back |
| ♻️ &nbsp; **Hot reload** | Prefer editing the JSON by hand? Changes apply within two seconds |
| 🌱 &nbsp; **Git-friendly** | Pretty-printed, sorted keys, stable diffs |
| 🩺 &nbsp; **Broken config** | One warning, an automatic `.broken` backup, and the game starts anyway |

<br>
<br>

---

<br>

## ⚙️ Configuration

<br>

### Rules — `config/itemsorganisator.json`

<br>

*Written for you by the in-game UI:*

<br>

```json
{
  "tabMoves":    { "somemod:somemod_tab": "minecraft:functional_blocks" },
  "itemMoves":   { "somemod:gadget": "minecraft:tools_and_utilities" },
  "hidden":      [ "somemod:op_item" ],
  "blacklisted": [ "brokenmod:ghost_item", "!brokenmod:.*_wip" ],

  "removeRecipesUsingHidden": false,
  "cascadeRecipeRemoval":     false,
  "cascadeAbortPercent":      33,
  "allowProtectedOverride":   false,

  "protectedTags":  [ "minecraft:dirt", "minecraft:planks", "minecraft:logs", "…" ],
  "protectedItems": [ "minecraft:gravel", "minecraft:crafting_table", "…" ]
}
```

<br>

**Precedence**, strongest first:

`blacklisted` &nbsp;→&nbsp; `hidden` &nbsp;→&nbsp; `itemMoves` &nbsp;→&nbsp; `tabMoves` &nbsp;→&nbsp; natural placement

<br>
<br>

### Controls — `config/itemsorganisator-controls.toml`

<br>

Mouse button, modifier key, toasts, Hidden-tab position — and a master switch, **`editingEnabled`**.

<br>

> ### 🔒 Ship your pack with `editingEnabled = false`
>
>
> Players get the curated inventory, without the ability to rewrite it.

<br>

Because it is a plain Forge config spec, [**Configured**](https://www.curseforge.com/minecraft/mc-mods/configured) renders it as an in-game settings screen with no extra setup — entirely optional, like every integration below.

<br>

Ship both files with your pack, on **both sides**.

The client UI reads them; the server enforces them. A player editing their local copy changes what they *see*, never what they can *obtain*.

<br>
<br>

---

<br>

## 🔍 Honest scope

<br>

**Testing status** — the in-game editing workflow (tab and item moves across a multi-page, 280-mod pack), the Hidden/blacklist tiers and the live JEI/EMI filtering are **validated in game on an integrated server** (single player / Open to LAN), where the server enforcement also runs. A **dedicated client/server split has not been through a full test pass yet** — the design separates sides cleanly, but treat that setup as beta and report anything odd.

<br>

Not covered in 1.x:

<br>

- Villager and wandering-trader **offers** &nbsp;— *the bought item is purged within a second, but the trade still appears*

- **`/give`** &nbsp;— *same one-second purge*

- Loot inside **generated chests** &nbsp;— *stripped when the container is opened*

- The **ender chest** &nbsp;— *stripped when opened, like any other container*

<br>

Vanilla inventories and opened container menus are fully handled.

<br>

Since **1.8.0**, so are nested and modded inventories:

<br>

- **Curios slots** &nbsp;— *swept with the player's inventory, same creative exemption*

- **Backpacks and portable containers** &nbsp;— *any item exposing the standard Forge `ITEM_HANDLER` capability, which covers Sophisticated Backpacks, TACZ loadout bags, and most others without a per-mod patch*

- **Shulker boxes** &nbsp;— *read straight from item NBT, where their contents actually live*

<br>

Nesting is followed one bag deep — a bag inside a bag. The container itself is never destroyed, only the restricted items inside it. Player-carried containers are swept every five seconds rather than every second: an item buried in a backpack is not immediately usable, and descending into containers costs more than walking a flat inventory.

<br>
<br>

---

<br>

## 🤝 Compatibility

<br>

| Mod | What it adds |
|:--|:--|
| [**JEI**](https://www.curseforge.com/minecraft/mc-mods/jei) | Hidden and blacklisted items removed from the index, live |
| [**EMI**](https://www.curseforge.com/minecraft/mc-mods/emi) | Same, through a native EMI plugin — EMI builds its own index, so this is a separate integration, not a JEI side effect. One quirk to know: EMI deliberately suppresses **all** game toasts while an inventory screen is open, so confirmation toasts (ours included) appear once you close the screen — expected behavior, not a bug |
| [**Reliable EMI**](https://www.curseforge.com/minecraft/mc-mods/reliable-emi) *(formerly EMI++)* | Its creative-tab based view inside EMI follows your reorganization, emptied tabs included. Pair the two and your curated categories become what **players** browse, not just admins in creative |
| [**ModernFix**](https://www.curseforge.com/minecraft/mc-mods/modernfix) | Fully compatible — its creative-tab memoization is handled *(see the technical note)* |
| [**Configured**](https://www.curseforge.com/minecraft/mc-mods/configured) | In-game settings screen, automatically |
| [**Creative Tab Organizer**](https://www.curseforge.com/minecraft/mc-mods/creative-tab-organizer) | Complementary, not competing — it reorders and hides *whole tabs*, this mod moves *individual items* and enforces removals. Run both if you like |
| **Nothing at all** | Everything works unchanged |

<br>

Every integration is optional and independently guarded: absent mods are never classloaded, and a failing integration degrades quietly instead of breaking your game.

<br>
<br>

---

<br>

## 🔧 Technical notes

### *For developers — skip this if you just want the mod*

<br>

### Insertion and removal

<br>

**Insertion** uses Forge's `BuildCreativeModeTabContentsEvent`.

**Removal** has no API in 1.20.1, so a mixin filters `displayItems` / `displayItemsSearchTab` at the `TAIL` of `CreativeModeTab#buildContents`, reached through an `@Accessor` interface.

<br>

There is **no `@Shadow` anywhere in this mod** — the annotation processor omits shadows from the refmap, producing mixins that work in a dev environment and crash in production. Accessors and invokers do not have that failure mode.

<br>
<br>

### Live rebuilds and ModernFix

<br>

Vanilla short-circuits `CreativeModeTabs#tryRebuildTabContents` when the `ItemDisplayParameters` are unchanged, so editing rules alone never triggers a rebuild — the static `CACHED_PARAMETERS` must be invalidated first.

<br>

That is not sufficient when ModernFix is installed.

Its `perf.memoize_creative_tab_build` module wraps `buildContents` per tab and memoizes the result in an injected `mfix$oldParameters` field. With identical parameters it returns the memoized contents, and the real build — including every mod's tab-contents event — **never runs**.

<br>

Items Organisator clears that field reflectively on each tab before rebuilding (one-shot lookup, no-op when ModernFix is absent or the module is off), then calls the vanilla rebuild synchronously so the screen re-initialises against fresh collections.

<br>

> *If you write anything that edits creative tabs at runtime on 1.20.1, this is the interaction that will cost you an afternoon.*

<br>
<br>

### Recipe-viewer refreshes are deferred

<br>

EMI reloads asynchronously and rebuilds screen widgets when it finishes; triggering that while an inventory is open mutates the widget list mid-render — `ConcurrentModificationException`.

<br>

Both the EMI reload and Reliable EMI's tab-list refresh are therefore queued and flushed on a client tick **when no screen is open** — which also batches a whole editing session into a single reload.

<br>
<br>

### Tab ordering and empty tabs

<br>

The creative screen builds its pages from Forge's `CreativeModeTabRegistry#getSortedCreativeModeTabs`, i.e. mod load order. The Hidden tab is moved to the end of that list at return (mixin, `remap = false`, configurable).

<br>

**Empty tabs** need no special handling: vanilla's `CreativeModeTab#shouldDisplay` already filters empty `CATEGORY` tabs and Forge recounts pages in `CreativeModeInventoryScreen#init`. Emptying the collections is enough.

<br>
<br>

### Server-side enforcement and performance

<br>

Enforcement is event-driven — pickup, drop (`EntityJoinLevelEvent`), interaction, attack, container open — plus a recipe filter injected at the tail of `RecipeManager#apply`, and an inventory sweep that runs **once per second**, not per tick.

<br>

Lookups are `HashSet` membership tests on registry identity. Blacklist regexes are compiled once per config load and resolved into a precomputed item set — never evaluated during play.

**Nothing runs per frame.**

<br>

**Recipe removal is load-time**, so hiding an item mid-session leaves its recipe until `/reload` or a restart. JEI and EMI removal is live; un-hiding restores them at the next reload.

<br>
<br>

### Build

<br>

**Compiled against** JEI 15.20.0.106 and EMI 1.1.24 (`compileOnly`), Forge 47.3.0, Java 17, official mappings.

<br>
<br>

---

## 🙏 Credits

<br>

The concept comes from **[CreativeTabsTweaker](https://www.curseforge.com/minecraft/mc-mods/creative-tabs-tweaker)** by **MeowSkyKung** (Forge 1.12.2) — the first mod to let a pack author remove tabs, create new ones and move items between them. Its config layout inspired the shape of this one. None of its code could be reused: the 1.12 approach relied on a mutable creative-tab field per item, which modern versions no longer have.

<br>

**[Creative Tab Organizer](https://www.curseforge.com/minecraft/mc-mods/creative-tab-organizer)** by Ziver1246 and **[MoreCreativeTabs](https://modrinth.com/mod/morecreativetabs)** cover tab-level organization on modern versions, and pair well with this mod.

<br>
<br>

---

## 📦 Source

<br>

The full source is on GitHub under the **MIT** licence — read it, build it, fork it, or open an issue.

<br>

**[github.com/doublescotch/items-organisator](https://github.com/doublescotch/items-organisator)**

<br>

Branches follow the target: **`1.20.1/forge`**. Every dependency resolves from Maven, so a clone builds with `gradlew build` alone — there is nothing to download by hand. JEI comes from the BlameJared Maven, EMI and Curios from the Modrinth Maven.

<br>
<br>

---

<div align="center">
<p><b>MIT</b> &nbsp;·&nbsp; by <b>Hell_Kaiser</b></p>
<p><i>Issues, suggestions and pull requests welcome.</i></p>
</div>
