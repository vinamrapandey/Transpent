# Transpent v1.1.0 Release Notes

We are excited to release **Transpent v1.1.0**! This version modernizes the app's user interface with Material Design 3 guidelines and completely decouples Customer and Supplier ledgers for a smoother bookkeeping experience.

## What's New

### 📊 Decoupled Customer & Supplier Ledgers
- **Independent Balance Tracks**: Separated the outstanding amounts so customer pending (They Owe You) and supplier pending (You Owe) balances are tracked completely independently.
- **Dynamic Context Styling**:
  - **Customer Section**: Transitions the home section card to a premium **Emerald Green** gradient.
  - **Supplier Section**: Transitions the home section card to a sleek **Sapphire Blue** gradient.
- **Pill-Style Switcher**: Added an intuitive, toggleable switcher directly on the Home Screen card to jump between Customer and Supplier contexts.

### ⚡ Context-Aware Quick Actions & Pay Button
- **Dynamic Action Dialogs**: The **Add** and **Pay** buttons on the Home card now dynamically detect whether you are viewing Customers or Suppliers and show context-relevant parties and fields.
- **Record Payments**: Fixed and enabled recording payments received from customers and payments made to suppliers directly from the Home Screen.

### 🧭 Navigation & History Enhancements
- **History Switcher**: Added a Customer & Supplier switcher pill to the History screen, enabling you to inspect transactions based on the selected context.
- **Clean Feature Grid**: Moved the **Bills** button out of the History page and onto the main Features Grid on the Home Screen.

### 🧹 Project Housekeeping & Optimization
- **Folder Restructuring**: Organized all current and historical build artifacts inside the `app_releases` directory.
- **Cleanup**: Cleaned up temporary XML dumps, logs, and screenshot files from the root of the project to ensure a production-ready workspace.

---
### Artifacts Included
- **Release APK**: `app_releases/v1.1.0/Transpent-v1.1.0-release.apk`
- **Debug APK**: `app_releases/v1.1.0/Transpent-v1.1.0-debug.apk`
