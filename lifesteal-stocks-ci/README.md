# Lifesteal Stocks (Fabric 1.21.4, client-side)

Tracks item prices like a stock ticker: latest price, % change, high/low and a sparkline, shown on a small HUD and via `/stocks list`.

It is passive. It only reads price text the server already shows you in item tooltips (shop / auction GUIs) and never sends packets, clicks or automates anything.

## Build
Requires JDK 21.

    gradle build

The jar ends up in `build/libs/lifesteal-stocks-1.0.0.jar`. Drop it in your `mods` folder with Fabric API.

## Commands
- `/stocks add <item>` / `/stocks remove <item>`: edit the HUD watchlist (e.g. `diamond`)
- `/stocks set <item> <price>`: record a price manually
- `/stocks list`: all tracked items with change, high, low, sparkline
- `/stocks hud`: toggle the HUD
- `/stocks scan`: toggle tooltip price scanning
- `/stocks regex <pattern>`: change how prices are read from tooltip lines (group 1 = number, group 2 = optional k/m suffix)
- `/stocks clear`: wipe history

## Tuning for Lifesteal.net
I don't know how the server words its price lines. The default regex matches lines like `Price: $1,250`, `Cost: 5k`, `Sell: 12.5`. If your shop uses different wording, hover an item, read the tooltip, and set `/stocks regex` to match it. Note a listing for a stack is recorded as shown, not divided per item.

Data is saved to `config/lsstocks.json`.
