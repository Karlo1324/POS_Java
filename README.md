# Sari-Sari Store POS (JavaFX + H2)

Offline-first desktop MVP using Java 25, JavaFX, Maven and an embedded H2 file database.

## Requirements
- JDK 25
- Maven 3.9+

## Run
```bash
mvn clean javafx:run
```
The database is created automatically at `./data/saristore.mv.db` (H2 uses the `.mv.db` suffix). Keep the `data` folder beside the app when moving it to another computer.

## Current features
- POS product search, cart quantities, cash and credit checkout
- Barcode search with a camera scan button on supported Android/iOS builds; desktop users can type or use a USB barcode scanner
- Transactional checkout with stock validation/deduction
- Product creation and low-stock warnings
- Customer creation, current balances, full/partial repayments
- Daily/weekly/monthly cash and gross-profit summaries
- Local persistent H2 database

## Notes / next improvements
- Stock is recorded in the smallest sellable unit. For example, enter 24 stock units for one pack of 24 sachets. `units_per_pack` is saved for future pack receiving/conversion workflows; the current UI does not yet provide a separate receiving-by-pack screen.
- Top-selling products, customer credit-history screens, product editing/deletion, receipt printing, and full native installer automation are not yet implemented in this MVP.
- For a native installer, build and test with `mvn clean package`, then use JDK `jpackage` on each target operating system. A Windows `.exe` must be packaged on Windows; a macOS `.app` should be packaged on macOS.
- Back up the entire `data` folder regularly. Do not delete the H2 database while the app is running.
