# Deterministic Workload v1

`eoc-deterministic-workload-v1` defines the reproducible source dataset used by the first Evidence
Lab baseline campaign.

The contract has two independent implementations:

- `tools/mock-erp/mock_erp/dataset.py` generates records served by the external Mock ERP.
- `tools/eoc-lab/eoc_lab/baseline_dataset.py` generates the expected state used by the oracle.

Neither implementation imports the other. Golden-vector tests detect drift.

## Inputs

- `seed`: integer in `[0, 2147483647]`
- `records`: invoice count in `[1, 100000]`
- customer count: `min(25, max(1, records))`
- currency: `USD`
- specification version: `eoc-deterministic-workload-v1`

For invoice sequence `i`, compute SHA-256 over UTF-8:

```text
eoc-deterministic-workload-v1|<seed>|invoice|<i>
```

Interpret bytes `0..3` and `4..7` as unsigned big-endian integers.

- customer index = `bytes[0..3] % customerCount + 1`
- total cents = `10000 + bytes[4..7] % 490001`
- state = `byte[8] % 3`
- issue-day offset = `byte[9] % 120` from `2026-01-01`
- due date = issue date + 30 days

State mapping:

| value | source status | open amount |
| ---: | --- | --- |
| 0 | `OPEN` | total amount |
| 1 | `PARTIALLY_PAID` | `max(1, totalCents // 2)` |
| 2 | `PAID` | `0.00` |

Identifiers and timestamps are deterministic functions of seed and sequence. Monetary values are
serialized with exactly two decimal places. The dataset fingerprint is SHA-256 over canonical JSON
containing the spec version, seed, customers, invoices, and derived payment records, with keys sorted
and separators `,` and `:`.

The Evidence Lab business date is `2026-05-15` so the same workload includes current and overdue
open receivables.
