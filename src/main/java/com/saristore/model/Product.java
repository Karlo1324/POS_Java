
package com.saristore.model;

import java.math.BigDecimal;

public record Product(
        int id,
        String barcode,
        String name,
        String category,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        int stockQuantity,
        int lowStockThreshold,
        int unitsPerPack,
        String stockUnit,
        String sellUnit
) {
    public boolean isLowStock() {
        return stockQuantity <= lowStockThreshold;
    }

    @Override
    public String toString() {
        return name
                + " | ₱" + sellingPrice
                + " | Stock: " + stockQuantity;
    }
}
