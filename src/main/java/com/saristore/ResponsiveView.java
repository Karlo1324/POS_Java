package com.saristore;

import java.util.Optional;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ResponsiveView extends VBox {

    private ListView<String> itemList;

    public ResponsiveView() {
        this.setSpacing(10);
        this.setPadding(new Insets(15));
        this.setAlignment(Pos.CENTER);

        itemList = new ListView<>();
        // Make the list view grow to take up all available vertical space
        VBox.setVgrow(itemList, Priority.ALWAYS);

        Button editStockBtn = new Button("Edit Stock");
        editStockBtn.setMaxWidth(Double.MAX_VALUE);
        editStockBtn.setPrefHeight(40);
        editStockBtn.setOnAction(e -> handleEditStock());

        Button checkoutBtn = new Button("Checkout");
        // Make button expand full width of the screen
        checkoutBtn.setMaxWidth(Double.MAX_VALUE);
        checkoutBtn.setPrefHeight(50); // Touch-friendly height

        this.getChildren().addAll(itemList, editStockBtn, checkoutBtn);
    }

    private void handleEditStock() {
        String selectedItem = itemList.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please select an item from the list first.");
            alert.showAndWait();
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Edit Stock");
        dialog.setHeaderText("Updating stock for: " + selectedItem);
        dialog.setContentText("Enter new stock quantity:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(input -> {
            try {
                int newStock = Integer.parseInt(input);
                if (newStock < 0) {
                    throw new NumberFormatException();
                }

                // TODO: Call ProductDAO to persist change in DB
                // productDAO.updateStock(productId, newStock);

                Alert success = new Alert(Alert.AlertType.INFORMATION, "Stock updated to " + newStock);
                success.showAndWait();
            } catch (NumberFormatException ex) {
                Alert error = new Alert(Alert.AlertType.ERROR, "Please enter a valid non-negative integer.");
                error.showAndWait();
            }
        });
    }
}