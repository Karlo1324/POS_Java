
package com.saristore;

import java.util.Optional;
import java.util.function.Consumer;

import javafx.application.Platform;

/**
 * Camera barcode scanning for Android/iOS using Gluon Attach.
 *
 * Desktop builds should use a typed-in barcode fallback.
 */
final class PhoneBarcodeScanner {

    private static final String TITLE = "Scan barcode";
    private static final String LEGEND =
            "Point the camera at the barcode";
    private static final String RESULT_TEXT = "Scanned: ";

    private static com.gluonhq.attach.barcodescan.BarcodeScanService
            wiredService;

    private static Consumer<String> pending;

    private PhoneBarcodeScanner() {
    }

    /**
     * Opens the camera scanner.
     *
     * @param onCode receives the scanned barcode on the JavaFX thread
     * @return true if a scanner service is available and a scan was started
     */
    @SuppressWarnings("deprecation")
    static boolean scan(Consumer<String> onCode) {

        if (onCode == null) {
            throw new IllegalArgumentException(
                    "Barcode callback cannot be null."
            );
        }

        Optional<com.gluonhq.attach.barcodescan.BarcodeScanService>
                optionalService =
                com.gluonhq.attach.barcodescan.BarcodeScanService
                        .create();

        if (optionalService.isEmpty()) {
            return false;
        }

        com.gluonhq.attach.barcodescan.BarcodeScanService service =
                optionalService.get();

        // Replace the previous callback for the new scan.
        pending = onCode;

        // Register the result listener only once for this service.
        if (wiredService != service) {

            service.resultProperty().addListener(
                    (observable, oldValue, newValue) -> {

                        if (newValue == null || newValue.isBlank()) {
                            return;
                        }

                        String code = newValue.trim();

                        // Capture and clear the callback for this result.
                        Consumer<String> target = pending;

                        if (target == null) {
                            return;
                        }

                        pending = null;

                        Platform.runLater(() -> target.accept(code));
                    }
            );

            wiredService = service;
        }

        try {
            service.asyncScan(TITLE, LEGEND, RESULT_TEXT);
            return true;

        } catch (Exception ex) {
            pending = null;
            ex.printStackTrace();
            return false;
        }
    }
}
