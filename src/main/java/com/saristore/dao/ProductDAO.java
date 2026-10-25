
package com.saristore.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.saristore.config.DatabaseConfig;
import com.saristore.model.Product;

public class ProductDAO {

    public List<Product> search(String term) throws SQLException {
        String sql = """
            SELECT * FROM products
            WHERE LOWER(product_name) LIKE ?
               OR LOWER(COALESCE(category, '')) LIKE ?
               OR COALESCE(barcode, '') LIKE ?
            ORDER BY product_name
            """;

        try (
            Connection c = DatabaseConfig.connect();
            PreparedStatement p = c.prepareStatement(sql)
        ) {
            String searchTerm = "%" +
                    (term == null ? "" : term.toLowerCase().trim()) + "%";

            p.setString(1, searchTerm);
            p.setString(2, searchTerm);
            p.setString(3, "%" + (term == null ? "" : term.trim()) + "%");

            try (ResultSet r = p.executeQuery()) {
                List<Product> products = new ArrayList<>();

                while (r.next()) {
                    products.add(map(r));
                }

                return products;
            }
        }
    }

    public List<Product> all() throws SQLException {
        return search("");
    }

    public void save(
            String barcode,
            String name,
            String category,
            BigDecimal cost,
            BigDecimal price,
            int stock,
            int threshold,
            int unitsPerPack,
            String stockUnit,
            String sellUnit
    ) throws SQLException {

        // Validate product information.
        if (name == null || name.isBlank()) {
            throw new SQLException("Product name is required.");
        }

        if (cost == null || price == null) {
            throw new SQLException("Cost price and selling price are required.");
        }

        if (cost.compareTo(BigDecimal.ZERO) < 0) {
            throw new SQLException("Cost price cannot be negative.");
        }

        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new SQLException("Selling price cannot be negative.");
        }

        if (stock < 0) {
            throw new SQLException("Initial stock cannot be negative.");
        }

        if (threshold < 0) {
            throw new SQLException("Low stock threshold cannot be negative.");
        }

        if (unitsPerPack < 1) {
            throw new SQLException("Units per pack must be at least 1.");
        }

        String sql = """
            INSERT INTO products (
                barcode,
                product_name,
                category,
                cost_price,
                selling_price,
                stock_quantity,
                low_stock_threshold,
                units_per_pack,
                stock_unit,
                sell_unit
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (
            Connection c = DatabaseConfig.connect();
            PreparedStatement p = c.prepareStatement(sql)
        ) {
            if (barcode == null || barcode.isBlank()) {
                p.setNull(1, Types.VARCHAR);
            } else {
                p.setString(1, barcode.trim());
            }

            p.setString(2, name.trim());
            p.setString(3,
                    category == null || category.isBlank()
                            ? "Others" : category.trim());
            p.setBigDecimal(4, cost);
            p.setBigDecimal(5, price);
            p.setInt(6, stock);
            p.setInt(7, threshold);
            p.setInt(8, unitsPerPack);
            p.setString(9,
                    stockUnit == null || stockUnit.isBlank()
                            ? "piece" : stockUnit.trim());
            p.setString(10,
                    sellUnit == null || sellUnit.isBlank()
                            ? "piece" : sellUnit.trim());

            p.executeUpdate();

        } catch (SQLException ex) {
            System.err.println("PRODUCT INSERT FAILED");
            System.err.println("SQL State: " + ex.getSQLState());
            System.err.println("Error Code: " + ex.getErrorCode());
            ex.printStackTrace();

            String message = ex.getMessage();

            if (message == null || message.isBlank()) {
                message = ex.getClass().getSimpleName();
            }

            throw new SQLException(
                    "Database could not save product: " + message,
                    ex
            );
        }
    }

    public void updateStock(
            Connection c,
            int id,
            int quantity
    ) throws SQLException {

        if (quantity < 1) {
            throw new SQLException(
                    "Stock deduction must be greater than zero."
            );
        }

        String sql = """
            UPDATE products
            SET stock_quantity = stock_quantity - ?
            WHERE id = ? AND stock_quantity >= ?
            """;

        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, quantity);
            p.setInt(2, id);
            p.setInt(3, quantity);

            if (p.executeUpdate() != 1) {
                throw new SQLException(
                        "Insufficient stock for product ID " + id
                );
            }
        }
    }

    private Product map(ResultSet r) throws SQLException {
        return new Product(
                r.getInt("id"),
                r.getString("barcode"),
                r.getString("product_name"),
                r.getString("category"),
                r.getBigDecimal("cost_price"),
                r.getBigDecimal("selling_price"),
                r.getInt("stock_quantity"),
                r.getInt("low_stock_threshold"),
                r.getInt("units_per_pack"),
                r.getString("stock_unit"),
                r.getString("sell_unit")
        );
    }

    public boolean updateStock(int productId, int newQuantity) {
        String sql = "UPDATE products SET stock_quantity = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, newQuantity);
            pstmt.setInt(2, productId);

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
