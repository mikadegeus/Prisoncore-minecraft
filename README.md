# PrisonCore

![Java](https://img.shields.io/badge/Java-21-orange)
![Paper](https://img.shields.io/badge/Paper-1.21+-blue)
![License](https://img.shields.io/badge/License-MIT-green)

A complete prison-server core for Paper 1.21+. Players mine in resetting mines,
sell their blocks for money, rank up from A to Z, prestige, earn tokens and
upgrade a custom pickaxe with token-priced enchants, all driven by one tight,
fully asynchronous block-break loop.

---

## Features

- **Resetting mines** with weighted block palettes, timer and percentage resets,
  imminent-reset warnings and safe evacuation. Native batched resets keep the
  main thread smooth; FastAsyncWorldEdit is an optional accelerator.
- **Ranks A to Z** with a formula or explicit cost curve, `/rankup`, `/rankupmax`
  and silent autorankup.
- **Prestige** with per-level sell and token multipliers, including a
  `prestige max` that climbs and prestiges repeatedly.
- **Selling** through `/sell hand`, `/sell all`, a drop-and-sell GUI, and an
  autosell toggle. Prices are config-driven and stacked with prestige and
  permission multipliers.
- **Tokens and a custom pickaxe** with enchant levels stored in persistent data,
  rendered into the pickaxe lore.
- **Custom enchants:** Efficiency, Haste, Speed, Jump (passive), Fortune, Token
  Greed, Money Greed (greed), and Explosive, Laser, Jackhammer (proc).
- **Temporary boosters** for sell or token income, per-player or server-wide,
  stacked into one effective multiplier.
- **In-game admin tooling:** a selection wand, plus create/edit/delete of mines,
  palettes and reset rules without touching config files.
- **Central hub menu** with a stats screen and a live leaderboard.
- **Performance-first block-break loop:** no synchronous database I/O, in-memory
  player data with periodic async flushing, and a hard cap on proc blocks per
  break.
- **MySQL or SQLite** out of the box (HikariCP pool, automatic schema creation).

---

## Requirements

- Paper 1.21+
- Java 21
- [Vault](https://www.spigotmc.org/resources/vault.34315/) plus an economy
  plugin (e.g. EssentialsX Economy)
- *(optional)* FastAsyncWorldEdit for faster mine resets

---

## Installation

1. Drop `PrisonCore-1.0.0.jar` into your server's `plugins/` folder.
2. Ensure Vault and an economy plugin are installed.
3. Start the server once to generate the configuration files.
4. Edit `mines.yml` (or use the database) to define your mines, then
   `/prison reload`.

---

## Configuration

| File | Purpose |
|------|---------|
| `config.yml` | Database, economy symbol, pickaxe, mining, ranks, prestige and messages |
| `ranks.yml` | The rank ladder and (in explicit mode) per-rank costs |
| `prestige.yml`* | Per-prestige multipliers (also configurable in `config.yml`) |
| `sellprices.yml` | `MATERIAL: price` map; anything not listed is unsellable |
| `enchants.yml` | Per-enchant max level, costs and proc/bonus tuning |
| `mines.yml` | Mine definitions, seeded into the database on first run |

\* prestige tuning currently lives under `config.yml -> prestige`.

### Rank costs

```yaml
ranks:
  mode: formula        # formula | explicit
  base-cost: 1000
  multiplier: 1.5      # cost(index) = base-cost * multiplier^index
```

### A mine (mines.yml)

```yaml
mines:
  A:
    world: world
    corner1: { x: 100, y: 60, z: 100 }
    corner2: { x: 130, y: 70, z: 130 }
    teleport: { x: 115, y: 72, z: 115, yaw: 0, pitch: 0 }
    required-rank: 0
    reset-seconds: 300
    reset-percentage: 25
    palette:
      - { material: STONE, weight: 70 }
      - { material: COAL_ORE, weight: 20 }
      - { material: IRON_ORE, weight: 10 }
```

---

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/prison` (`/pc`) | `prisoncore.use` | Show your prison profile |
| `/prison reload` | `prisoncore.admin` | Reload all configuration |
| `/mine [name]` (`/mines`) | `prisoncore.use` | Open the mine list or teleport to a mine |
| `/mine reset <name>` | `prisoncore.admin` | Force a mine reset |
| `/rankup [max]` (`/ru`) | `prisoncore.use` | Open the rankup menu or rank up to max |
| `/rankupmax` (`/rum`) | `prisoncore.use` | Rank up as far as you can afford |
| `/prestige [max]` (`/pres`) | `prisoncore.use` | Open the prestige menu or prestige to max |
| `/sell [hand\|all\|auto]` | `prisoncore.use` | Open the sell menu, sell, or toggle autosell |
| `/sellall` | `prisoncore.use` | Sell everything sellable |
| `/enchant` (`/pickaxe`) | `prisoncore.use` | Open the pickaxe enchant menu |
| `/tokens [pay\|give]` (`/token`) | `prisoncore.use` | View, pay or grant tokens |
| `/booster [give]` (`/boosters`) | `prisoncore.use` | View active boosters; `give` is admin-only |
| `/pa <...>` (`/prisonadmin`) | `prisoncore.admin` | Mine wand, create/edit/delete mines, reload |

### Admin mine workflow

```
/pa wand                       # get the selection wand
left-click + right-click       # set the two corners
/pa create <name>              # create the mine from your selection
/pa addblock <name> STONE 70   # add weighted blocks to the palette
/pa addblock <name> COAL_ORE 30
/pa settp <name>               # set the teleport to where you stand
/pa setrank <name> 2           # require rank index 2 (rank C)
/pa setreset <name> 300 25     # reset every 300s or at 25% remaining
```

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `prisoncore.use` | `true` | Basic access to all player commands |
| `prisoncore.admin` | `op` | Admin tools, mine management and reload |
| `prisoncore.autosell` | `true` | Allows toggling autosell |
| `prisoncore.multiplier.<n>` | `false` | A permission-based sell multiplier (highest held wins) |

---

## How mining works

Every block broken inside a mine with the prison pickaxe runs through one loop:

```
break block in mine
  -> require the prison pickaxe
  -> roll proc enchants (Explosive / Laser / Jackhammer), bounded by a cap
  -> award tokens  (base x count x Token Greed x prestige token multiplier)
  -> autosell ? deposit money (sell price x sell multiplier x Fortune x Money Greed)
              : give the drops (Fortune multiplies the amount)
  -> update stats, then autorankup if enabled
```

The loop does **no** database work. Player data lives in memory and is flushed
asynchronously on a timer and on quit, so the hot path stays fast under heavy
mining.

### Enchants

| Enchant | Type | Effect |
|---------|------|--------|
| Efficiency | passive | Vanilla mining speed on the pickaxe |
| Haste / Speed / Jump | passive | Potion effects while holding the pickaxe |
| Fortune | greed | Multiplies sell value (or drop amount) |
| Token Greed | greed | Bonus tokens per block |
| Money Greed | greed | Bonus money per block (with autosell) |
| Explosive | proc | Breaks a cube around the block |
| Laser | proc | Breaks a line in your facing direction |
| Jackhammer | proc | Breaks the whole layer (rare) |

---

## Architecture

- **Paper 1.21 / Java 21**, built with Maven (shaded HikariCP + JDBC drivers).
- Modular managers: database, economy, player data, ranks, prestige, selling,
  mines and enchants, each with a single responsibility.
- GUIs share one `InventoryHolder`-based menu interface and a single click
  dispatcher (no fragile title matching).
- All money flows through Vault; tokens and prison state live in the plugin's
  own database.

---

## Built by Mika
