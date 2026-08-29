# Wasl v0.4 — Banner core checkpoint

Core implementation checkpoint after `cebc6b3da69fa0ce49fb444944f23c1e0a044b7e`.

Implemented:

- document issue commands can carry an immutable `DocumentBannerAsset`;
- Room identity updates persist banner path + SHA-256 rather than dropping v12 metadata;
- payment/debt/statement snapshots freeze the selected banner and include it in replay validation;
- app-private content-addressed banner vault validates image decoding, path safety, maximum size and SHA-256;
- payment receipt and account-document renderers verify the historical banner asset before drawing it on page one;
- payment receipt identity state preserves a previously stored banner.

Gate correction after CI #1171:

- the payment-receipt test fake now implements the banner import contract;
- the banner vault test runs as Android instrumentation because the vault intentionally depends on an Android `Context` and image decoder.

A clean Android CI run on the current head must pass before UI import/preview/remove and encrypted backup/restore integration are added.
