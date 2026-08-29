# V0.4 — Batch 1 checkpoint

Head before this checkpoint: `b7ea92f2cf37122e6fb0734472a0c996ca8171cb`.

Implemented in batch 1:

- Home currency overview hides currencies whose receivable and payable balances are both zero.
- Home account cards are compact and no longer show an oversized one-letter avatar placeholder.
- Normal phone widths prefer compact horizontal layouts; stacking remains for very narrow widths and large font scale.
- Group-expense direction/currency choices use the compact responsive path on standard phones; participant choices wrap as chips and text fields are shorter.
- Account PDF quick action moved to the upper-left area.
- Pending payment-promise actions are grouped in one action bar.
- Documents hub is wired to the real application attachment store.
- Attachment picker launch is guarded and user-facing copy no longer exposes SHA-256/storage implementation details.

Gate: Android CI on this checkpoint must pass before batch 2 is considered stable.
