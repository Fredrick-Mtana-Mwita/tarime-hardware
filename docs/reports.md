# Reports

Covers FR-21, E1 and OD-14 (defaults chosen until the shop names its essential reports).
In the staff menu: **Reports**.

## Who sees what

| Report | Owner | Sales | Inventory | Delivery |
|---|---|---|---|---|
| Sales & money | ✓ | ✓ | – | – |
| Balances | ✓ | ✓ | – | – |
| Stock | ✓ | – | ✓ | – |

The server enforces this for each endpoint and each CSV download, not just the menu.

## Each figure keeps its own date basis (E1)

| Figure | Counted by |
|---|---|
| Requests sent, requested materials value | Day the customer sent the request (value is before delivery fees) |
| Confirmed / rejected / cancelled | Day of the staff decision |
| Completed sales (gross) | Day the goods were **delivered or collected** (OD-10, approved) |
| Refunds, cancelled receipts | Day the Owner **approved** them |
| Money received (cash, mobile money) | Day the receipt was saved |
| Net sales | Gross sales − refunds in the period |
| Net money in | Received − cancelled receipts − refunds in the period |
| Balances | Now, from approved records only (pending requests never change them) |
| Stock levels | Now. Received, dispatched and released movements count within the chosen dates. |
| Stock value | On hand × **selling** price. Cost prices are not recorded. |

Days follow Tanzania time (EAT). Ranges include both ends and can be up to 366 days.
The presets are Today, Last 7 days, Last 30 days, This month and Last month.

## API and CSV

- `GET /api/staff/reports/sales?from=YYYY-MM-DD&to=YYYY-MM-DD`, `/balances`, `/stock?from&to`
- CSV: `/sales.csv` (one row per day plus a total row), `/products.csv` (top products), `/balances.csv`, `/stock.csv`

CSV files are UTF-8 with a byte-order mark, so Excel shows Kiswahili correctly. Text cells that start with
`= + - @` are prefixed with `'`, so a product name can never run as an Excel formula.

## Limits

- Top products shows the 10 best by value.
- Balances lists up to 500 orders. Stock lists up to 2,000 product options.
- Stock is for Tarime, the only open branch.
