# Transpent

A lightweight Android finance monitoring app for a general store ledger.

## What is implemented

- Google account is the only entry screen.
- Local JSON ledger storage on the phone.
- Customers: tap a customer, add purchased products, and see the latest four items on the card.
- Products: prerecorded product names and prices for quick entry.
- Suppliers: store supplier purchase items and attach a bill image by camera or file picker.
- Payments: item-wise paid amounts, miscellaneous part payment, and paid-in-full clearing.
- Export: one Excel-compatible CSV or a ZIP with separate customers, suppliers, and products CSVs.
- Drive backup/restore: uploads and restores the full local ledger JSON using the signed-in Google account token.

## Open in Android Studio

1. Open this folder: `C:\Users\Lenovo\Downloads\Transpent App`.
2. Let Android Studio sync Gradle.
3. Run the `app` configuration on an emulator or Android phone.

This project intentionally uses plain Java Android views and local JSON files. The only AndroidX dependency is Core, used for safe camera bill image capture through FileProvider.
