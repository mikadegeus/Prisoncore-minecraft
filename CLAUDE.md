# Plugin 4: PrisonCore

## Doel
Bouw een volledige, productierijpe **Prison-server core** in Java voor Paper 1.21+.
Een prison core is de spil van een prison-server: spelers minen in resettende mines,
verkopen hun blokken voor geld, rankuppen door de letters A tot Z, prestigen daarna,
verdienen tokens en upgraden een custom pickaxe met token-enchants. Alles draait om
één strak, performant block-break-loop en een schone, modulaire architectuur.

Portfoliokwaliteit: schone code, modulair opgezet, nette README, werkende demo,
geen deprecated API, alles async waar het kan, en een hot path die niet lagt onder
duizenden blokken per seconde.

> Deze plugin hergebruikt bewust de gedeelde infrastructuur uit `CustomShop`,
> `ContractBoard` en `PixelForge` (zie sectie "Herbruikbare code").

---

## Tech stack
- **Platform:** Paper 1.21.x (Paper API, niet plain Spigot)
- **Java:** 21
- **Build tool:** Maven (`pom.xml`), shade-plugin voor het bundelen van drivers
- **Database:** MySQL via HikariCP, met SQLite fallback (zelfde patroon als ContractBoard)
- **Economy:** Vault (geld). Tokens en prestige-data zitten in onze eigen database.
- **Optioneel (soft-depend):** FastAsyncWorldEdit (FAWE) voor supersnelle mine-resets,
  met een native gechunkte fallback als FAWE niet aanwezig is.
- **Dependencies:**
  - `io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT` (provided)
  - `com.zaxxer:HikariCP:5.1.0` (shaded + relocated)
  - `com.mysql:mysql-connector-j:8.0.33` (shaded)
  - `org.xerial:sqlite-jdbc:3.46.1.3` (shaded)
  - `com.github.MilkBowl:VaultAPI:1.7.1` (provided, soft-depend)
  - `com.google.code.gson:gson:2.10.1` (provided, voor mine-palette/typedata JSON)
  - `org.jetbrains:annotations:24.1.0` (provided)

---

## Identiteit
- **groupId:** `dev.mika`
- **artifactId:** `PrisonCore`
- **version:** `1.0.0`
- **package:** `dev.mika.prisoncore`
- **main class:** `dev.mika.prisoncore.PrisonCore`
- **finalName:** `PrisonCore-1.0.0`
- HikariCP relocate naar `dev.mika.prisoncore.libs.hikari`

---

## Scope en milestones

Een prison core is te groot voor één keer "alles tegelijk". We bouwen in
**milestones** die elk los te demonstreren zijn. v1.0 = milestone 1 t/m 6.

| Milestone | Inhoud | Status |
|---|---|---|
| **M1. Fundament** | pom, plugin.yml, configs, `PrisonCore` main, `DatabaseManager`, `PlayerData` + `PlayerDataManager`, currencies (geld via Vault, tokens via DB) | core |
| **M2. Mines** | `Mine` model, weighted palette, `MineManager`, region reset (FAWE + native fallback), reset-trigger (timer + percentage), veilige teleport, mine-GUI | core |
| **M3. Ranks + Prestige** | `RankManager` (A..Z), rankup-kostencurve, `/rankup`, `/rankupmax`, autorankup, `PrestigeManager`, prestige-multiplier, rankup- en prestige-GUI | core |
| **M4. Selling** | config-gedreven `sellprices.yml`, `SellManager`, `/sell hand`, `/sell all`, sell-GUI, autosell-toggle, multipliers (prestige + booster + permissie) | core |
| **M5. Tokens + Pickaxe + Enchants** | `EnchantManager`, custom enchants in pickaxe-PDC, enchant-GUI, lore-rebuild, token-economie, enchant-procs (Explosive/Laser/Jackhammer/Greed) | core |
| **M6. Block-break pipeline** | de hot path: `BlockBreakListener`, mine-membership-check, token/geld-uitkering, autosell, enchant-procs, statistieken, performance-batching | core |
| **M7. Boosters + Multipliers** | tijdelijke sell-boosters, `/booster`, broadcast, expiry-sweep | uitbreiding |
| **M8. Admin tooling** | `/prisonadmin`, mine-wand (pos1/pos2 selecteren), mine create/edit/delete GUI, reload, give-tokens | uitbreiding |
| **M9. Polish** | placeholders (scoreboard-velden), join-pickaxe, statistieken-GUI, leaderboard (top blocks/tokens/prestige) | uitbreiding |

> **Buiten v1.0-scope (bewust):** gangs, plots/cells, PvP-mines, crates, battlepass,
> pets. Architectuur moet ze later toelaten, maar we bouwen ze nu niet.

---

## Projectstructuur

```
Prison Core/
├── pom.xml
├── CLAUDE.md
├── README.md
└── src/main/
    ├── java/dev/mika/prisoncore/
    │   ├── PrisonCore.java                  (main: wiring, scheduling, getters)
    │   │
    │   ├── commands/
    │   │   ├── PrisonCommand.java           (/prison hub-menu + reload)
    │   │   ├── RankupCommand.java           (/rankup, /rankupmax)
    │   │   ├── PrestigeCommand.java         (/prestige, /prestige max)
    │   │   ├── MineCommand.java             (/mine, /mine <naam> tp)
    │   │   ├── SellCommand.java             (/sell hand|all, /sellall)
    │   │   ├── TokensCommand.java           (/tokens [pay|give])
    │   │   ├── EnchantCommand.java          (/enchant -> enchant-GUI)
    │   │   ├── BoosterCommand.java          (/booster)               [M7]
    │   │   └── PrisonAdminCommand.java      (/pa: mine-tools, give, reload) [M8]
    │   │
    │   ├── model/
    │   │   ├── PlayerData.java              (mutable: balance-cache, tokens, rank, prestige, multipliers, stats)
    │   │   ├── Rank.java                    (record: id, displayName, index, cost)
    │   │   ├── Prestige.java                (record: level, sellMultiplier, tokenMultiplier)
    │   │   ├── Mine.java                    (record/class: naam, wereld, hoeken, palette, resetregels, rankvereiste, tp-locatie)
    │   │   ├── MinePalette.java             (gewogen blokverdeling: List<PaletteEntry>)
    │   │   ├── PaletteEntry.java            (record: Material, double weight)
    │   │   ├── CustomEnchant.java           (enum: id, displayName, maxLevel, basiskosten, kostencurve, type)
    │   │   ├── EnchantType.java             (enum: PASSIVE, PROC, GREED)
    │   │   └── Booster.java                 (record: type, multiplier, expiresAt, source)   [M7]
    │   │
    │   ├── managers/
    │   │   ├── DatabaseManager.java         (HikariCP pool + DDL + CRUD; zelfde patroon als ContractBoard)
    │   │   ├── EconomyManager.java          (Vault-wrapper: has/withdraw/deposit/format)
    │   │   ├── PlayerDataManager.java       (in-memory cache + async load/save, periodieke flush)
    │   │   ├── RankManager.java             (rank-lijst, rankup-logica, kostencurve, autorankup)
    │   │   ├── PrestigeManager.java         (prestige-logica + multipliers)
    │   │   ├── MineManager.java             (mines laden/opslaan, reset-orchestratie, membership-lookup)
    │   │   ├── MineResetService.java        (FAWE-strategie + native gechunkte fallback)
    │   │   ├── SellManager.java             (sellprices, totale waarde, multiplier-stack, sell hand/all/inventory)
    │   │   ├── EnchantManager.java          (enchant-levels lezen/schrijven via PDC, kosten, lore-rebuild)
    │   │   └── MultiplierService.java       (effectieve sell/token-multiplier per speler: prestige+booster+perm) [M7]
    │   │
    │   ├── pickaxe/
    │   │   ├── PickaxeFactory.java          (bouwt de starters-pickaxe, PDC-tag "prisoncore-pickaxe")
    │   │   ├── PickaxeKeys.java             (NamespacedKey-constanten voor enchants + identiteit)
    │   │   └── PickaxeLore.java             (rendert lore uit enchant-levels)
    │   │
    │   ├── enchants/
    │   │   ├── EnchantHandler.java          (interface: onBlockBreak(context))
    │   │   ├── ExplosiveEnchant.java        (3x3 area break binnen mine)
    │   │   ├── LaserEnchant.java            (rechte lijn break)
    │   │   ├── JackhammerEnchant.java       (hele laag, rare)
    │   │   ├── TokenGreedEnchant.java       (bonus tokens)
    │   │   ├── MoneyGreedEnchant.java       (bonus geld)
    │   │   ├── FortuneEnchant.java          (vermenigvuldigt sell-waarde van de drop)
    │   │   └── HasteEnchant.java            (passive potion-effect bij vasthouden)
    │   │
    │   ├── gui/
    │   │   ├── Menu.java                     (InventoryHolder-interface; zelfde patroon als ContractMenu)
    │   │   ├── GuiLayout.java                (gedeelde slot-constanten; overgenomen)
    │   │   ├── PrisonHubGUI.java             (hub: Mines | Rankup | Prestige | Sell | Enchants | Stats)
    │   │   ├── MineListGUI.java              (alle mines, rank-gated, klik = teleport)
    │   │   ├── RankupGUI.java                (huidige rank, kosten, voortgang, knop rankup/max)
    │   │   ├── PrestigeGUI.java              (prestige-info + multipliers + bevestiging)
    │   │   ├── SellGUI.java                  (leg items neer om te verkopen, toont totaal live)
    │   │   ├── EnchantGUI.java               (enchants kopen/upgraden met tokens)
    │   │   ├── StatsGUI.java                 (blokken gebroken, tokens verdiend, prestige, rank)  [M9]
    │   │   └── admin/
    │   │       ├── MineAdminGUI.java         (mines beheren)                                       [M8]
    │   │       └── MineEditGUI.java          (palette + resetregels bewerken)                      [M8]
    │   │
    │   ├── listeners/
    │   │   ├── BlockBreakListener.java       (DE hot path: mine-check, uitkering, procs, stats)
    │   │   ├── GUIListener.java              (één centrale dispatcher; zelfde patroon)
    │   │   ├── PlayerConnectionListener.java (join: data laden + pickaxe geven; quit: data flushen)
    │   │   └── PickaxeProtectionListener.java(voorkomt droppen/verliezen van de prison-pickaxe)
    │   │
    │   └── util/
    │       ├── ItemBuilder.java              (fluent builder; overgenomen uit CustomShop)
    │       ├── MessageUtil.java              (& kleurcodes + replace(); overgenomen)
    │       ├── NumberFormat.java             (compacte geldweergave: 1.2K, 3.4M, 5.6B)
    │       ├── Cuboid.java                   (twee hoeken: iterate, contains, volume, randomLocation)
    │       └── Weighted.java                 (gewogen random selectie voor de palette)
    │
    └── resources/
        ├── plugin.yml
        ├── config.yml
        ├── mines.yml                         (mine-definities, of in DB; zie data model)
        ├── ranks.yml                         (A..Z + kosten of formule)
        ├── prestige.yml                      (prestige-multipliers)
        ├── enchants.yml                      (enchant-kosten, maxlevels, proc-kansen)
        └── sellprices.yml                    (material -> prijs; zelfde stijl als CustomShop shops)
```

---

## Kernconcepten

### Currencies
| Currency | Opslag | Verdiend met | Besteed aan |
|---|---|---|---|
| **Geld** | Vault | blokken verkopen | rankup, prestige-vereisten |
| **Tokens** | onze DB (`player_data.tokens`) | blokken minen + Greed-enchants | pickaxe-enchants |

> Geld nooit zelf opslaan: altijd via Vault. Tokens en alle prison-state via onze DB.

### Mines
- Een mine is een **cuboid** (twee hoeken in een wereld) met een **gewogen palette**.
- **Reset-triggers** (config per mine):
  - `interval`: elke N seconden resetten, met waarschuwingsbroadcasts (60s/30s/10s/5s).
  - `percentage`: reset zodra <= X% blokken over is.
  - Beide mogen tegelijk aanstaan; eerste die afgaat reset.
- **Veilige reset:** spelers binnen de mine worden vooraf naar de mine-tp of omhoog
  geteleporteerd zodat ze niet stikken of vallen.
- **Reset-strategie:** `MineResetService` gebruikt FAWE als die er is (async edit-session),
  anders een native fallback die blokken in batches per tick zet met `setBlockData(data, false)`
  (physics uit) zodat grote mines de main thread niet blokkeren.
- **Rank-gating:** elke mine heeft een minimale rank; `MineListGUI` toont gesloten mines grijs.

### Ranks (A..Z)
- Configureerbare lijst rank-ids (default `A` t/m `Z`).
- **Kostencurve:** `cost(index) = baseCost * (multiplier ^ index)` (config: `baseCost`, `multiplier`),
  of expliciete kosten per rank in `ranks.yml`. Beide ondersteunen.
- `/rankup`: één rank omhoog als saldo genoeg is.
- `/rankupmax`: zoveel ranks omhoog als het saldo toelaat in één keer.
- **Autorankup** (toggle per speler, opgeslagen in PlayerData): probeert na elke sell/break
  automatisch te rankuppen.
- Na rank `Z` is rankup geblokkeerd tot de speler prestiget.

### Prestige
- Beschikbaar zodra de speler op de laatste rank (`Z`) staat.
- Prestigen reset de rank terug naar `A`, verhoogt `prestige` met 1.
- Per prestige-level: `sellMultiplier += perPrestigeSell` en optioneel `tokenMultiplier += ...`.
- `/prestige` (één keer) en `/prestige max` (zoveel mogelijk).

### Selling en multipliers
- `sellprices.yml`: `MATERIAL: prijs` (zelfde config-stijl als CustomShop-shops).
- **Effectieve multiplier** = `prestigeMultiplier * boosterMultiplier * permissionMultiplier`.
  Permissie-multipliers via `prisoncore.multiplier.<x>` (hoogste wint), berekend in `MultiplierService`.
- `/sell hand`: verkoopt het item in de hand.
- `/sell all` (`/sellall`): verkoopt alle verkoopbare items in de inventory.
- **SellGUI:** speler legt items neer, totaal wordt live berekend, knop "Verkoop alles".
- **Autosell:** toggle in PlayerData; bij block-break wordt de drop direct omgezet in geld
  in plaats van naar de inventory (skip de drop, tel waarde direct op).

### Tokens, pickaxe en enchants
- Iedere speler krijgt bij eerste join een **prison-pickaxe** met een PDC-tag.
  De pickaxe is uniek (mag niet gedropt/verloren worden, zie `PickaxeProtectionListener`).
- **Enchant-levels** worden opgeslagen in de PDC van het pickaxe-item onder
  `PickaxeKeys` (namespaced). De lore wordt herbouwd door `PickaxeLore` bij elke wijziging.
- **EnchantManager** leest/schrijft levels, berekent upgradekosten (`enchants.yml`) en
  trekt tokens af.
- **Enchant-procs** (in de block-break-loop):
  | Enchant | Type | Effect |
  |---|---|---|
  | Efficiency | passive | digsnelheid (Haste-attribuut bij vasthouden) |
  | Haste | passive | Haste potion-effect bij vasthouden |
  | Speed / Jump | passive | bewegingseffecten bij vasthouden |
  | Fortune | greed | vermenigvuldigt sell-waarde / drop-aantal |
  | TokenGreed | greed | kans op bonus-tokens per break |
  | MoneyGreed | greed | kans op bonus-geld per break |
  | Explosive | proc | breekt 3x3 rond het blok (alleen binnen de mine) |
  | Laser | proc | breekt een rechte lijn blokken |
  | Jackhammer | proc | breekt de hele laag (rare proc) |
- **Belangrijk:** procs breken alleen blokken **binnen dezelfde mine** en tellen die
  blokken mee voor tokens/sell/stats, zodat de hot path consistent blijft.

---

## Data model

### Tabel: `player_data`
```sql
CREATE TABLE IF NOT EXISTS player_data (
    uuid             VARCHAR(36) PRIMARY KEY,
    name             VARCHAR(16) NOT NULL,
    rank_index       INT         NOT NULL DEFAULT 0,
    prestige         INT         NOT NULL DEFAULT 0,
    tokens           BIGINT      NOT NULL DEFAULT 0,
    blocks_mined     BIGINT      NOT NULL DEFAULT 0,
    autosell         TINYINT     NOT NULL DEFAULT 0,
    autorankup       TINYINT     NOT NULL DEFAULT 0,
    sell_multiplier  DOUBLE      NOT NULL DEFAULT 1.0,  -- afgeleide cache, herberekend bij load
    last_seen        BIGINT      NOT NULL
);
```

### Tabel: `mines` (of als `mines.yml`; kies DB voor admin-GUI-bewerkbaarheid)
```sql
CREATE TABLE IF NOT EXISTS mines (
    name             VARCHAR(32) PRIMARY KEY,
    world            VARCHAR(64) NOT NULL,
    min_x INT, min_y INT, min_z INT,
    max_x INT, max_y INT, max_z INT,
    tp_x DOUBLE, tp_y DOUBLE, tp_z DOUBLE, tp_yaw FLOAT, tp_pitch FLOAT,
    required_rank    INT         NOT NULL DEFAULT 0,
    palette_json     TEXT        NOT NULL,            -- [{"material":"STONE","weight":70}, ...]
    reset_seconds    INT         NOT NULL DEFAULT 300,
    reset_percentage INT         NOT NULL DEFAULT 25
);
```

### Tabel: `boosters` (M7)
```sql
CREATE TABLE IF NOT EXISTS boosters (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,   -- BIGINT AUTO_INCREMENT voor MySQL
    uuid         VARCHAR(36) DEFAULT NULL,            -- NULL = serverbrede booster
    type         VARCHAR(16) NOT NULL,                -- SELL | TOKEN
    multiplier   DOUBLE      NOT NULL,
    expires_at   BIGINT      NOT NULL
);
```

> Gebruik exact het ContractBoard-patroon: MySQL `BIGINT AUTO_INCREMENT` vs SQLite
> `INTEGER PRIMARY KEY AUTOINCREMENT`, alle queries async via `runTaskAsynchronously`,
> callbacks terug op de main thread via `runTask`.

---

## Block-break pipeline (de hot path, performance-kritisch)

`BlockBreakListener.onBlockBreak` is het hart van de plugin en draait potentieel
duizenden keren per seconde. Houd het strak en allocatie-arm.

```
1. Is de speler in een geregistreerde mine? (MineManager.mineAt(location), O(1)-achtige lookup)
   - Nee  -> niets doen, laat vanilla het afhandelen.
   - Ja   -> ga door.
2. Houdt de speler de prison-pickaxe vast? (PDC-check)
   - Nee  -> cancel break (alleen met de pickaxe mag je minen) of laat toe; configureerbaar.
3. event.setDropItems(false)  -> wij beheren drops zelf.
4. Verzamel de te breken blokken:
   - basisblok + proc-enchant-blokken (Explosive/Laser/Jackhammer), GEFILTERD op
     "binnen dezelfde mine" en "niet-lucht".
5. Voor elk gebroken blok:
   - tel +1 blocks_mined (in-memory PlayerData, niet per blok naar DB)
   - bereken tokens (basis + TokenGreed) en geld (autosell ? sellwaarde * multiplier : 0)
   - als autosell uit: geef drop in de inventory (of dropNaturally als vol)
6. Batch de DB-write: PlayerData is dirty -> wordt periodiek geflusht, niet per break.
7. Trigger autorankup-check als die aanstaat (debounced).
8. Update mine-resetteller (percentage-trigger).
```

**Performance-regels:**
- Geen database-call in de listener zelf. Alleen in-memory mutatie van `PlayerData`.
- `PlayerDataManager` flusht dirty data async elke N seconden en bij quit.
- `MineManager` houdt een snelle lookup: per wereld een lijst cuboids, of een chunk-index.
- Proc-enchants hebben een harde cap op aantal blokken per break (config) tegen lag-spikes.
- Geen `Material.values()`-scans in de loop; gebruik vooraf gebouwde maps.

---

## GUI-overzicht

Alle GUIs implementeren `Menu extends InventoryHolder` (zelfde patroon als
`ContractMenu`), zodat de centrale `GUIListener` de juiste GUI herkent zonder
fragiele titel-matching. Layout-constanten in `GuiLayout` (overgenomen).

| GUI | Rijen | Inhoud |
|---|---|---|
| `PrisonHubGUI` | 3 | Mines, Rankup, Prestige, Sell, Enchants, Stats, Sluiten |
| `MineListGUI` | 6 | alle mines (rank-gated, klik = teleport), voortgangsbalk per mine |
| `RankupGUI` | 3 | huidige rank, volgende rank + kosten, saldo, `[Rankup]` `[Rankup Max]` |
| `PrestigeGUI` | 3 | prestige-level, huidige + volgende multiplier, `[Prestige]` `[Prestige Max]` |
| `SellGUI` | 6 | dropslots voor items, live totaal, `[Verkoop alles]` |
| `EnchantGUI` | 6 | per enchant een item: level, kosten, `[Upgrade]`, token-saldo bovenaan |
| `StatsGUI` | 3 | blokken, tokens verdiend, rank, prestige, multiplier |
| `MineAdminGUI` / `MineEditGUI` | 6 | mines beheren, palette + resetregels bewerken (M8) |

---

## Commando's

| Commando | Permissie | Beschrijving |
|---|---|---|
| `/prison` of `/pc` | `prisoncore.use` | Opent de hub-GUI |
| `/rankup` | `prisoncore.use` | Eén rank omhoog |
| `/rankupmax` | `prisoncore.use` | Zoveel ranks als het saldo toelaat |
| `/prestige [max]` | `prisoncore.use` | Prestigen (op rank Z) |
| `/mine [naam]` | `prisoncore.use` | Mine-lijst of teleport naar een mine |
| `/sell hand\|all` | `prisoncore.use` | Verkoop hand of hele inventory |
| `/sellall` | `prisoncore.use` | Alias voor `/sell all` |
| `/tokens [pay <speler> <n>]` | `prisoncore.use` | Saldo tonen of tokens overmaken |
| `/enchant` | `prisoncore.use` | Opent de enchant-GUI |
| `/booster` | `prisoncore.use` | Actieve boosters tonen (M7) |
| `/pa mine ...` | `prisoncore.admin` | Mine-tools: wand, create, reset, delete (M8) |
| `/pa give tokens <speler> <n>` | `prisoncore.admin` | Tokens uitdelen |
| `/pa reload` | `prisoncore.admin` | Configs herladen |

Tab-completion voor alle sub-commands, mine-namen en online spelers.

---

## Permissies

| Permissie | Default | Beschrijving |
|---|---|---|
| `prisoncore.use` | `true` | Basisgebruik (alle speler-commando's) |
| `prisoncore.admin` | `op` | Admin-tools, mine-beheer, reload |
| `prisoncore.multiplier.<n>` | `false` | Permissie-gebaseerde sell-multiplier (bijv. rank-perks) |
| `prisoncore.autosell` | `true` | Mag autosell aanzetten |

---

## Configuratie

### `config.yml`
```yaml
database:
  type: sqlite            # of mysql
  host: localhost
  port: 3306
  database: prisoncore
  username: root
  password: ""
  pool-size: 10
  connection-timeout-ms: 30000

economy:
  currency-symbol: "$"

pickaxe:
  give-on-first-join: true
  required-to-mine: true        # alleen met de prison-pickaxe mag je in mines breken
  unbreakable: true

mining:
  flush-interval-seconds: 30    # hoe vaak PlayerData async naar DB geflusht wordt
  max-proc-blocks-per-break: 256 # harde cap tegen lag-spikes
  base-tokens-per-block: 1

ranks:
  mode: formula                 # formula | explicit
  base-cost: 1000
  multiplier: 1.5               # cost(index) = base-cost * multiplier^index
  # bij mode: explicit -> zie ranks.yml

prestige:
  per-prestige-sell-multiplier: 0.1   # +10% sell per prestige
  per-prestige-token-multiplier: 0.05

messages:
  prefix: "&8[&bPrison&8] "
  no-permission: "&cJe hebt geen toestemming."
  player-only: "&cAlleen spelers kunnen dit commando gebruiken."
  rankup-success: "&aJe bent gerankt naar &f{rank}&a!"
  rankup-max-rank: "&eJe staat op de hoogste rank. Prestige om verder te gaan."
  not-enough-money: "&cNiet genoeg geld. Nodig: &f{cost}&c."
  prestige-success: "&dPrestige &f{prestige}&d! Je multiplier is nu &f{multiplier}x&d."
  prestige-need-max-rank: "&cJe moet op de hoogste rank staan om te prestigen."
  sold: "&aVerkocht voor &f{amount}&a."
  nothing-to-sell: "&eJe hebt niets om te verkopen."
  not-enough-tokens: "&cNiet genoeg tokens. Nodig: &f{cost}&c."
  enchant-upgraded: "&aEnchant &f{enchant} &anaar level &f{level}&a!"
  mine-locked: "&cDeze mine vereist rank &f{rank}&c."
  mine-resetting: "&eMine &f{mine} &ewordt over &f{seconds}s &egereset."
```

### `ranks.yml` (mode: explicit, optioneel)
```yaml
ranks:
  A: 1000
  B: 2500
  C: 5000
  # ... t/m Z
```

### `sellprices.yml` (zelfde stijl als CustomShop)
```yaml
prices:
  COBBLESTONE: 2.0
  STONE: 3.0
  COAL: 8.0
  IRON_ORE: 15.0
  GOLD_ORE: 25.0
  DIAMOND_ORE: 80.0
  EMERALD_ORE: 120.0
```

### `enchants.yml`
```yaml
enchants:
  EFFICIENCY:
    max-level: 100
    base-cost: 100
    cost-multiplier: 1.2
  EXPLOSIVE:
    max-level: 50
    base-cost: 500
    cost-multiplier: 1.3
    proc-chance-per-level: 0.01   # 1% per level
  TOKEN_GREED:
    max-level: 25
    base-cost: 250
    cost-multiplier: 1.25
    bonus-per-level: 0.05
  # ...
```

### `mines.yml` (alleen als je voor file-opslag kiest i.p.v. DB)
```yaml
mines:
  A:
    world: prison
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

> **Beslis bij M2:** mines in DB (admin-GUI kan ze live bewerken) of in `mines.yml`
> (simpeler, git-vriendelijk). Default-aanbeveling: **DB**, met `mines.yml` als seed
> die bij eerste start geïmporteerd wordt indien de tabel leeg is.

---

## Herbruikbare code

Lift deze bestanden over en pas alleen package + naamruimte aan. Niet opnieuw schrijven.

| Bron | Bestand | Hergebruik in PrisonCore |
|---|---|---|
| ContractBoard | `managers/DatabaseManager.java` | Basis voor onze `DatabaseManager` (HikariCP, MySQL/SQLite, async-patroon) |
| ContractBoard | `managers/EscrowManager.java` | Vereenvoudigen tot `EconomyManager` (has/withdraw/deposit/format) |
| ContractBoard | `gui/ContractMenu.java` | Wordt onze `gui/Menu.java` |
| ContractBoard | `gui/GuiLayout.java` | Direct overnemen |
| ContractBoard | `listeners/GUIListener.java` | Direct patroon overnemen (dispatch op InventoryHolder) |
| ContractBoard | `PrisonCore`-wiring in main | Zelfde onEnable/onDisable/scheduleTasks-structuur |
| CustomShop | `utils/ItemBuilder.java` | Direct overnemen |
| CustomShop | `utils/MessageUtil.java` | Direct overnemen |
| CustomShop | `managers/EconomyManager.java` | Vergelijken met ContractBoard-versie, beste nemen |
| CustomShop | `resources/shops/*.yml` + loader | Prijs-config-patroon voor `sellprices.yml` |
| PixelForge | `utils/ColorUtil.java`, async-patronen | Inspiratie voor `Weighted`/`NumberFormat` |

---

## Codekwaliteitseisen (zelfde lat als de andere plugins)
- Geen deprecated API-calls (Adventure API voor tekst/sounds, geen `ChatColor`-deprecated paden).
- Alle database-I/O asynchroon; Bukkit-API alleen op de main thread.
- De block-break-listener doet **nul** synchrone I/O.
- Geld nooit zelf bijhouden: altijd via Vault. Tokens/state via onze DB, transactioneel veilig.
- `@NotNull` / `@Nullable` overal; geen magic numbers (constanten of config).
- Elke class een korte Javadoc; status-/rank-/prestige-overgangen alleen via hun manager.
- Immutable waar logisch (`record` voor `Rank`, `Prestige`, `Mine`, `PaletteEntry`).
  `PlayerData` is bewust mutable (hot path), maar alleen muteerbaar via duidelijke methods.
- Veel kleine bestanden (200-400 regels, max 800), hoge cohesie.
- Validatie op alle command-input en config-waarden; faal met een nette melding.

---

## Volgorde van implementatie (bouw in deze volgorde)

### M1. Fundament
1. `pom.xml` (kopieer ContractBoard, hernoem artifact/relocatie) en `plugin.yml`
2. `config.yml`, `sellprices.yml`, `ranks.yml`, `enchants.yml`
3. `util/`: `ItemBuilder`, `MessageUtil`, `NumberFormat`, `Cuboid`, `Weighted`
4. `model/PlayerData`, `model/Rank`, `model/Prestige`
5. `managers/DatabaseManager` (player_data DDL + CRUD, ContractBoard-patroon)
6. `managers/EconomyManager` (Vault-wrapper)
7. `managers/PlayerDataManager` (cache, async load/save, flush-timer)
8. `listeners/PlayerConnectionListener` (load/save)
9. `PrisonCore` main: wiring + scheduling (skelet, daarna per milestone uitbreiden)

### M2. Mines
10. `model/Mine`, `model/MinePalette`, `model/PaletteEntry`
11. `managers/MineManager` (laden, lookup `mineAt(location)`)
12. `managers/MineResetService` (native fallback eerst, FAWE-pad daarna)
13. `gui/Menu`, `gui/GuiLayout`, `listeners/GUIListener`, `gui/MineListGUI`
14. `commands/MineCommand` (+ tab-complete)

### M3. Ranks + Prestige
15. `managers/RankManager` (kostencurve, rankup, rankupmax, autorankup)
16. `managers/PrestigeManager`
17. `gui/RankupGUI`, `gui/PrestigeGUI`
18. `commands/RankupCommand`, `commands/PrestigeCommand`

### M4. Selling
19. `managers/SellManager` (sellprices laden, waardeberekening, multiplier-stack)
20. `gui/SellGUI`
21. `commands/SellCommand` (+ `/sellall`)

### M5. Tokens + Pickaxe + Enchants
22. `pickaxe/PickaxeKeys`, `pickaxe/PickaxeFactory`, `pickaxe/PickaxeLore`
23. `model/CustomEnchant`, `model/EnchantType`, `managers/EnchantManager`
24. `enchants/EnchantHandler` + implementaties (passive + greed eerst, procs daarna)
25. `gui/EnchantGUI`, `commands/EnchantCommand`, `commands/TokensCommand`
26. `listeners/PickaxeProtectionListener`

### M6. Block-break pipeline
27. `listeners/BlockBreakListener` (alles samengebracht: check, uitkering, procs, stats)
28. Performance-pass: batching, caps, profiling met een testmine

### M7-M9 (uitbreidingen)
29. Boosters + `MultiplierService` + `BoosterCommand`
30. Admin-tooling (`PrisonAdminCommand`, mine-wand, `MineAdminGUI`, `MineEditGUI`)
31. `StatsGUI`, join-pickaxe-polish, leaderboard, README afronden

### Afsluiting
32. `README.md` volledig invullen
33. `mvn clean package` en alle warnings wegwerken
34. Test op een lokale Paper 1.21 met Vault + EssentialsX (economy)

---

## README.md (genereer dit bestand ook)
Moet bevatten:
- Badges: Java 21, Paper 1.21+, License MIT
- Korte beschrijving (2 zinnen)
- Features-lijst met checkmarks per systeem (mines, ranks, prestige, sell, enchants, tokens)
- Installatie-instructies (Vault + economy-plugin vereist)
- Configuratie-uitleg per config-bestand
- Commando-tabel en permissie-tabel
- Uitleg van het block-break-/enchant-systeem
- GUI-flowdiagram (ASCII)
- Korte architectuur-sectie (modulair, async, hot path)
- Sectie "Built by Mika"

---

## Definition of Done (v1.0 = M1 t/m M6)
- [ ] `mvn clean package` slaagt zonder warnings
- [ ] Plugin laadt op Paper 1.21 met Vault + economy zonder errors
- [ ] PlayerData laadt/saved correct (async), geen dataverlies bij quit/crash
- [ ] Mines resetten betrouwbaar (timer + percentage) zonder spelers te laten stikken
- [ ] Native reset-fallback werkt zonder FAWE; FAWE-pad werkt als FAWE aanwezig is
- [ ] Rankup A..Z + rankupmax + autorankup werken met de kostencurve
- [ ] Prestige werkt vanaf rank Z en past multipliers correct toe
- [ ] Sell hand/all/GUI + autosell werken met de juiste multiplier-stack
- [ ] Tokens worden verdiend met minen en besteed aan enchants
- [ ] Custom enchants (passive + greed + minstens één proc) werken in de mine
- [ ] Block-break-loop doet geen synchrone I/O en lagt niet onder zware load
- [ ] Geld gaat nooit verloren of dupliceert (Vault-transacties altijd gecheckt)
- [ ] Alle GUIs werken via de centrale `GUIListener` (geen titel-matching)
- [ ] `/pa reload` herlaadt configs zonder restart
- [ ] README.md volledig ingevuld
- [ ] Geen deprecated API-calls
```
