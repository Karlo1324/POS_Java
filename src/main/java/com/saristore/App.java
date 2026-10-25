package com.saristore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.saristore.config.DatabaseConfig;
import com.saristore.dao.CustomerDAO;
import com.saristore.dao.ProductDAO;
import com.saristore.model.Customer;
import com.saristore.model.Product;
import com.saristore.service.POSService;
import com.saristore.service.ReportService;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import java.util.function.Consumer;
public class App extends Application {

    /** Where the stylesheet lives on the classpath. */
    private static final String STYLESHEET_PATH = "/styles/app.css";

    private final ProductDAO products = new ProductDAO();
    private final CustomerDAO customers = new CustomerDAO();
    private final POSService pos = new POSService();
    private final ReportService reports = new ReportService();

    // productId -> quantity
    private final Map<Integer, Integer> cart = new LinkedHashMap<>();

    /** One display row in the cart list. Carries the product id so
     *  "remove selected" always removes exactly the row the user picked. */
    private record CartLine(
            int productId,
            String name,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) { }

    private final ObservableList<CartLine> cartRows =
            FXCollections.observableArrayList();

    // Current cart total, observable so the "change due" hint can react.
    private final ObjectProperty<BigDecimal> cartSum =
            new SimpleObjectProperty<>(BigDecimal.ZERO);

    private final Label totalLabel = new Label("₱0.00");
    private final Label itemCountLabel = new Label("0 items");

    // Each tab registers a refresh action so its data is reloaded
    // whenever the user switches to it (new customers, new stock, etc.).
    private Runnable refreshPos = () -> { };
    private Runnable refreshInventory = () -> { };
    private Runnable refreshCredit = () -> { };
    private Runnable refreshReports = () -> { };


        
    @Override
    public void start(Stage stage) {

        try {
            DatabaseConfig.initialize();
        } catch (Exception ex) {
            showError("Could not open the database", ex);
            Platform.exit();
            return;
        }

        Tab posTab = tab("POS", posView());
        Tab inventoryTab = tab("Inventory", inventoryView());
        Tab creditTab = tab("Mga utang", creditView());
        Tab reportTab = tab("Reports", reportView());

        TabPane tabs = new TabPane(
                posTab, inventoryTab, creditTab, reportTab
        );

        tabs.getStyleClass().add("main-tabs");
        tabs.setTabClosingPolicy(
                TabPane.TabClosingPolicy.UNAVAILABLE
        );

        tabs.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldTab, newTab) -> {
                    if (newTab == posTab) {
                        refreshPos.run();
                    } else if (newTab == inventoryTab) {
                        refreshInventory.run();
                    } else if (newTab == creditTab) {
                        refreshCredit.run();
                    } else if (newTab == reportTab) {
                        refreshReports.run();
                    }
                }
        );

        VBox root = new VBox(appHeader(), tabs);
        root.getStyleClass().add("app-root");
        VBox.setVgrow(tabs, Priority.ALWAYS);

        Scene scene = new Scene(root, 1180, 690);

        String stylesheet = stylesheet();

        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet);
        } else {
            System.err.println(
                    "Stylesheet " + STYLESHEET_PATH + " was not found. "
                    + "Put app.css in src/main/resources/styles/."
            );
        }

        stage.setTitle("Sari-Sari Store POS");
        stage.setMinWidth(980);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.show();
    }

    // =========================
    // SHELL: HEADER, TABS, STYLESHEET
    // =========================

    private static String stylesheet() {
        URL url = App.class.getResource(STYLESHEET_PATH);
        return url == null ? null : url.toExternalForm();
    }

    /** Dialogs live in their own window, so they need the stylesheet too. */
    private static void styleDialog(Dialog<?> dialog) {
        String stylesheet = stylesheet();

        if (stylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(stylesheet);
        }
    }

    private Node appHeader() {

        Label mark = new Label("₱");
        mark.getStyleClass().add("brand-mark-text");

        StackPane logo = new StackPane(mark);
        logo.getStyleClass().add("brand-mark");

        Label title = new Label("Sari-Sari Store POS");
        title.getStyleClass().add("brand-title");

        Label tagline = new Label("Sales, stock and utang in one place");
        tagline.getStyleClass().add("brand-tagline");

        VBox brand = new VBox(1, title, tagline);
        brand.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label clock = new Label();
        clock.getStyleClass().add("header-clock");

        DateTimeFormatter format = DateTimeFormatter.ofPattern(
                "EEEE, MMM d, yyyy   h:mm a", Locale.ENGLISH
        );

        Timeline ticker = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        e -> clock.setText(
                                LocalDateTime.now().format(format)
                        )
                ),
                new KeyFrame(Duration.seconds(1))
        );

        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();

        HBox header = new HBox(12, logo, brand, spacer, clock);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("app-header");

        return header;
    }

    private Tab tab(String title, Node node) {
        Tab tab = new Tab(title, node);
        tab.setClosable(false);
        return tab;
    }

    // =========================
    // LAYOUT BUILDING BLOCKS
    // =========================

    private static VBox page(String title, String subtitle) {

        Label heading = new Label(title);
        heading.getStyleClass().add("page-title");

        Label sub = new Label(subtitle);
        sub.getStyleClass().add("page-subtitle");

        VBox head = new VBox(2, heading, sub);

        VBox page = new VBox(16, head);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(20, 28, 24, 28));
        page.setFillWidth(true);

        return page;
    }

    private static VBox card(Node... children) {
        VBox box = new VBox(12, children);
        box.getStyleClass().add("card");
        return box;
    }

    private static HBox cardHeader(String title, Node... trailing) {

        Label label = new Label(title);
        label.getStyleClass().add("card-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, label, spacer);
        header.getChildren().addAll(trailing);
        header.setAlignment(Pos.CENTER_LEFT);

        return header;
    }

    private static Label cardHint(String text) {
        Label hint = new Label(text);
        hint.getStyleClass().add("card-hint");
        return hint;
    }

    /** A small label above an input. */
    private static VBox field(String label, Node input) {

        Label text = new Label(label);
        text.getStyleClass().add("field-label");

        if (input instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }

        VBox box = new VBox(5, text, input);
        box.setMaxWidth(Double.MAX_VALUE);

        return box;
    }

    private static VBox emptyState(String title, String hint) {

        Label heading = new Label(title);
        heading.getStyleClass().add("empty-title");

        Label detail = new Label(hint);
        detail.getStyleClass().add("empty-hint");

        VBox box = new VBox(4, heading, detail);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));

        return box;
    }

    /** Applies (or swaps) a colour variant on a badge-style label. */
    private static void setBadge(Label badge, String text, String variant) {

        badge.setText(text);

        badge.getStyleClass().removeAll(
                "badge-success", "badge-warning",
                "badge-danger", "badge-neutral"
        );

        badge.getStyleClass().add(variant);

        if (!badge.getStyleClass().contains("badge")) {
            badge.getStyleClass().add("badge");
        }
    }

    private static Label chip(String text, String variant) {
        Label chip = new Label();
        chip.getStyleClass().add("badge-lg");
        setBadge(chip, text, variant);
        return chip;
    }

    private static void setStockBadge(
            Label badge, Product product, boolean compact) {

        int stock = product.stockQuantity();

        if (stock <= 0) {
            setBadge(badge, "Out of stock", "badge-danger");
        } else if (product.isLowStock()) {
            setBadge(
                    badge,
                    compact ? stock + " (low)" : "Only " + stock + " left",
                    "badge-warning"
            );
        } else {
            setBadge(
                    badge,
                    compact ? String.valueOf(stock) : stock + " in stock",
                    "badge-success"
            );
        }
    }

    private static ToggleButton segment(
            String text, String value, ToggleGroup group) {

        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        button.setUserData(value);
        button.getStyleClass().add("segment");

        return button;
    }

    private static HBox segmented(boolean stretch, ToggleButton... buttons) {

        HBox bar = new HBox(2, buttons);
        bar.getStyleClass().add("segmented");
        bar.setAlignment(Pos.CENTER_LEFT);

        if (stretch) {
            for (ToggleButton button : buttons) {
                button.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(button, Priority.ALWAYS);
            }
        }

        return bar;
    }

    /** A segmented control must always have one option selected. */
    private static void keepOneSelected(ToggleGroup group) {
        group.selectedToggleProperty().addListener(
                (observable, oldToggle, newToggle) -> {
                    if (newToggle == null && oldToggle != null) {
                        oldToggle.setSelected(true);
                    }
                }
        );
    }

    // =========================
    // POINT OF SALE
    // =========================

    private Node posView() {

        VBox page = page(
                "Point of sale",
                "Find a product, build the cart, then take payment."
        );

        // ---- Left column: search + results ----

        TextField search = new TextField();
        search.setPromptText(
                "Search product name, category, or scan barcode"
        );

        Button scanButton = new Button("Scan barcode");
        scanButton.setOnAction(event -> {
            boolean scannerAvailable = PhoneBarcodeScanner.scan(code -> {
                search.setText(code);
                search.requestFocus();
                search.positionCaret(search.getText().length());
            });

            if (!scannerAvailable) {
                warn(
                        "Camera scanning is unavailable on this device. "
                        + "Enter or scan the barcode into the search field."
                );
            }
        });

        HBox searchRow = new HBox(8, search, scanButton);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);

        ListView<Product> results = new ListView<>();
        results.getStyleClass().add("item-list");
        results.setPlaceholder(emptyState(
                "No matching products",
                "Check the spelling, or scan the barcode again."
        ));
        results.setCellFactory(list -> new ProductCell());

        Runnable reloadResults = () -> {
            try {
                String query = search.getText() == null
                        ? ""
                        : search.getText().trim();

                if (query.isEmpty()) {
                    results.getItems().setAll(products.all());
                } else {
                    results.getItems().setAll(products.search(query));
                }
            } catch (Exception ex) {
                showError("Product search failed", ex);
            }
        };

        Button addButton = new Button("Add to cart");
        addButton.getStyleClass().add("primary");
        addButton.setMaxWidth(Double.MAX_VALUE);

        addButton.setOnAction(event -> {

            Product product =
                    results.getSelectionModel().getSelectedItem();

            if (product == null) {
                warn("Please select a product first.");
                return;
            }

            if (product.stockQuantity() <= 0) {
                warn("This product is out of stock.");
                return;
            }

            TextInputDialog dialog = new TextInputDialog("1");
            styleDialog(dialog);
            dialog.setTitle("Add to Cart");
            dialog.setHeaderText(
                    "Enter quantity for " + product.name()
            );
            dialog.setContentText(
                    "Quantity (" + product.sellUnit() + "):"
            );

            dialog.showAndWait().ifPresent(value -> {
                try {
                    int quantity = parseInt(value, "Quantity");

                    if (quantity < 1) {
                        throw new IllegalArgumentException(
                                "Quantity must be at least 1."
                        );
                    }

                    int inCart = cart.getOrDefault(product.id(), 0);

                    if (inCart + quantity > product.stockQuantity()) {
                        throw new IllegalArgumentException(
                                "Insufficient stock. Available: "
                                + product.stockQuantity()
                                + (inCart > 0
                                    ? " (" + inCart + " already in cart)"
                                    : "")
                        );
                    }

                    cart.merge(product.id(), quantity, Integer::sum);
                    refreshCart();

                } catch (Exception ex) {
                    showError("Could not add item to cart", ex);
                }
            });
        });

        search.textProperty().addListener(
                (observable, oldValue, newValue) -> reloadResults.run()
        );

        // Barcode scanners "type" the code and press Enter:
        // if exactly one product matches, go straight to the quantity box.
        search.setOnAction(event -> {
            if (results.getItems().size() == 1) {
                results.getSelectionModel().select(0);
                addButton.fire();
            }
        });

        results.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2
                    && results.getSelectionModel()
                              .getSelectedItem() != null) {
                addButton.fire();
            }
        });

        VBox left = card(
                cardHeader("Products"),
                searchRow,
                results,
                addButton
        );

        VBox.setVgrow(results, Priority.ALWAYS);

        // ---- Right column: cart + payment ----

        ListView<CartLine> cartList = new ListView<>(cartRows);
        cartList.getStyleClass().add("item-list");
        cartList.setMinHeight(80);
        cartList.setPlaceholder(emptyState(
                "The cart is empty",
                "Add a product from the list to start a sale."
        ));
        cartList.setCellFactory(list -> new CartCell());

        Button removeButton = new Button("Remove selected");
        removeButton.getStyleClass().addAll("danger-outline", "small");

        removeButton.setOnAction(event -> {

            CartLine selected =
                    cartList.getSelectionModel().getSelectedItem();

            if (selected == null) {
                warn("Select a cart item to remove.");
                return;
            }

            cart.remove(selected.productId());
            refreshCart();
        });

        // The one place the marigold accent is used for content:
        // the amount the customer owes right now.
        Label totalCaption = new Label("Total due");
        totalCaption.getStyleClass().add("total-caption");

        itemCountLabel.getStyleClass().add("total-caption");
        totalLabel.getStyleClass().add("total-amount");

        VBox totalText = new VBox(2, totalCaption, itemCountLabel);
        totalText.setAlignment(Pos.CENTER_LEFT);

        Region totalSpacer = new Region();
        HBox.setHgrow(totalSpacer, Priority.ALWAYS);

        HBox totalBoard = new HBox(totalText, totalSpacer, totalLabel);
        totalBoard.setAlignment(Pos.CENTER_LEFT);
        totalBoard.getStyleClass().add("total-board");

        // Payment type: a segmented control instead of a drop-down.
        ToggleGroup paymentGroup = new ToggleGroup();
        ToggleButton cashToggle = segment("Cash", "CASH", paymentGroup);
        ToggleButton creditToggle =
                segment("Credit (utang)", "CREDIT", paymentGroup);

        cashToggle.setSelected(true);
        keepOneSelected(paymentGroup);

        HBox paymentSwitch = segmented(true, cashToggle, creditToggle);

        ComboBox<Customer> customer = new ComboBox<>();
        customer.setPromptText("Select customer for utang");
        customer.setMaxWidth(Double.MAX_VALUE);

        // A converter (instead of custom cells) keeps the prompt text
        // visible when nothing is selected.
        customer.setConverter(new StringConverter<Customer>() {
            @Override
            public String toString(Customer c) {
                return c == null ? "" : c.name();
            }

            @Override
            public Customer fromString(String text) {
                return null;
            }
        });

        Runnable reloadCustomers = () -> {
            try {
                Customer previous = customer.getValue();

                customer.getItems().setAll(customers.all());

                if (previous != null) {
                    for (Customer c : customer.getItems()) {
                        if (Objects.equals(c.id(), previous.id())) {
                            customer.setValue(c);
                            break;
                        }
                    }
                }
            } catch (Exception ex) {
                showError("Could not load customers", ex);
            }
        };

        TextField tendered = new TextField();
        tendered.setPromptText("0.00");

        // Live hint under the amount box: change due, or how much is short.
        Label changeLabel = new Label("Change due: —");
        changeLabel.getStyleClass().add("change-label");

        Runnable updateChange = () -> {

            changeLabel.getStyleClass().removeAll(
                    "change-ok", "change-short"
            );

            String raw = tendered.getText();

            if (raw == null || raw.isBlank() || cartRows.isEmpty()) {
                changeLabel.setText("Change due: —");
                return;
            }

            try {
                BigDecimal paid = parseMoney(raw, "Amount tendered");
                BigDecimal difference = paid.subtract(cartSum.get());

                if (difference.signum() < 0) {
                    changeLabel.setText(
                            "Short by " + peso(difference.negate())
                    );
                    changeLabel.getStyleClass().add("change-short");
                } else {
                    changeLabel.setText(
                            "Change due: " + peso(difference)
                    );
                    changeLabel.getStyleClass().add("change-ok");
                }
            } catch (IllegalArgumentException ex) {
                changeLabel.setText("Change due: —");
            }
        };

        tendered.textProperty().addListener(
                (observable, oldValue, newValue) -> updateChange.run()
        );

        cartSum.addListener(
                (observable, oldValue, newValue) -> updateChange.run()
        );

        // Only show the controls that matter for the chosen payment type.
        VBox cashBox = new VBox(
                8,
                field("Amount tendered (₱)", tendered),
                changeLabel
        );

        cashBox.visibleProperty().bind(cashToggle.selectedProperty());
        cashBox.managedProperty().bind(cashToggle.selectedProperty());

        VBox creditBox = new VBox(
                8,
                field("Customer", customer)
        );

        creditBox.visibleProperty().bind(creditToggle.selectedProperty());
        creditBox.managedProperty().bind(creditToggle.selectedProperty());

        Button checkoutButton = new Button("Complete sale");
        checkoutButton.getStyleClass().addAll("primary", "large");
        checkoutButton.setMaxWidth(Double.MAX_VALUE);

        checkoutButton.setOnAction(event -> {

            try {
                if (cart.isEmpty()) {
                    warn("Your cart is empty.");
                    return;
                }

                boolean credit = creditToggle.isSelected();

                Customer selectedCustomer = customer.getValue();

                if (credit && selectedCustomer == null) {
                    warn("Select a customer for credit sales.");
                    return;
                }

                // Uses fresh prices from the database and throws on
                // failure, so a DB error can never look like a ₱0 total.
                BigDecimal total = cartTotal();

                BigDecimal amountTendered = BigDecimal.ZERO;

                if (!credit) {
                    if (tendered.getText().isBlank()) {
                        warn("Enter the amount tendered.");
                        return;
                    }

                    amountTendered =
                            parseMoney(tendered.getText(), "Amount tendered");

                    if (amountTendered.compareTo(total) < 0) {
                        warn(
                                "Amount tendered (" + peso(amountTendered)
                                + ") is less than the total ("
                                + peso(total) + ")."
                        );
                        return;
                    }
                }

                int saleId = pos.checkout(
                        new LinkedHashMap<>(cart),
                        credit ? selectedCustomer.id() : null,
                        credit,
                        amountTendered
                );

                BigDecimal change = amountTendered.subtract(total);

                cart.clear();
                refreshCart();

                tendered.clear();
                search.clear();
                cashToggle.setSelected(true);
                customer.setValue(null);

                // Stock levels and customer debt just changed.
                reloadResults.run();
                reloadCustomers.run();

                alert(
                        "Sale #" + saleId
                        + " completed successfully.\n\n"
                        + "Total: " + peso(total)
                        + (credit
                            ? "\nAdded to customer utang."
                            : "\nChange: " + peso(change))
                );

            } catch (Exception ex) {
                showError("Checkout failed", ex);
            }
        });

        // Press Enter in the amount box to finish the sale.
        tendered.setOnAction(event -> checkoutButton.fire());

        VBox right = card(
                cardHeader("Current sale", removeButton),
                cartList,
                totalBoard,
                paymentSwitch,
                cashBox,
                creditBox,
                checkoutButton
        );

        VBox.setVgrow(cartList, Priority.ALWAYS);

        // ---- Two-column layout ----

        HBox columns = new HBox(16, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(520);
        right.setPrefWidth(440);
        right.setMinWidth(400);

        page.getChildren().add(columns);
        VBox.setVgrow(columns, Priority.ALWAYS);

        refreshPos = () -> {
            reloadResults.run();
            reloadCustomers.run();
            refreshCart();
        };

        reloadResults.run();
        reloadCustomers.run();

        return page;
    }

    /** Product row: name + category on the left, price + stock badge right. */
    private static final class ProductCell extends ListCell<Product> {

        private final Label name = new Label();
        private final Label meta = new Label();
        private final Label price = new Label();
        private final Label stock = new Label();
        private final HBox row;

        ProductCell() {
            name.getStyleClass().add("row-title");
            meta.getStyleClass().add("row-meta");
            price.getStyleClass().add("row-price");

            VBox info = new VBox(2, name, meta);
            info.setAlignment(Pos.CENTER_LEFT);

            VBox figures = new VBox(4, price, stock);
            figures.setAlignment(Pos.CENTER_RIGHT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            row = new HBox(12, info, spacer, figures);
            row.setAlignment(Pos.CENTER_LEFT);

            // Make the row fill the cell so the price sits at the right edge.
            row.prefWidthProperty().bind(widthProperty().subtract(28));
        }

        @Override
        protected void updateItem(Product product, boolean empty) {
            super.updateItem(product, empty);

            if (empty || product == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            name.getStyleClass().remove("row-title-dim");

            if (product.stockQuantity() <= 0) {
                name.getStyleClass().add("row-title-dim");
            }

            name.setText(product.name());
            meta.setText(Objects.toString(product.category(), "Others"));
            price.setText(peso(product.sellingPrice()));
            setStockBadge(stock, product, false);

            setText(null);
            setGraphic(row);
        }
    }

    /** Cart row: quantity pill, name + unit price, line total. */
    private static final class CartCell extends ListCell<CartLine> {

        private final Label quantity = new Label();
        private final Label name = new Label();
        private final Label detail = new Label();
        private final Label total = new Label();
        private final HBox row;

        CartCell() {
            quantity.getStyleClass().add("qty-pill");
            name.getStyleClass().add("row-title");
            detail.getStyleClass().add("row-meta");
            total.getStyleClass().add("row-price");

            VBox info = new VBox(2, name, detail);
            info.setAlignment(Pos.CENTER_LEFT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            row = new HBox(12, quantity, info, spacer, total);
            row.setAlignment(Pos.CENTER_LEFT);

            row.prefWidthProperty().bind(widthProperty().subtract(28));
        }

        @Override
        protected void updateItem(CartLine line, boolean empty) {
            super.updateItem(line, empty);

            if (empty || line == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            quantity.setText(line.quantity() + "×");
            name.setText(line.name());
            detail.setText(peso(line.unitPrice()) + " each");
            total.setText(peso(line.lineTotal()));

            setText(null);
            setGraphic(row);
        }
    }

    // =========================
    // CART CALCULATIONS
    // =========================

    private Map<Integer, Product> productIndex() throws Exception {

        Map<Integer, Product> index = new LinkedHashMap<>();

        for (Product product : products.all()) {
            index.put(product.id(), product);
        }

        return index;
    }

    /**
     * Total using current database prices. Throws if anything goes wrong
     * so the caller never proceeds with a wrong (e.g. zero) total.
     */
    private BigDecimal cartTotal() throws Exception {

        Map<Integer, Product> index = productIndex();
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<Integer, Integer> entry : cart.entrySet()) {
            Product product = index.get(entry.getKey());

            if (product == null) {
                throw new IllegalStateException(
                        "A product in the cart no longer exists. "
                        + "Remove it from the cart and try again."
                );
            }

            total = total.add(
                    product.sellingPrice().multiply(
                            BigDecimal.valueOf(entry.getValue())
                    )
            );
        }

        return total;
    }

    private void refreshCart() {

        List<CartLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int units = 0;

        try {
            // One database read for the whole refresh.
            Map<Integer, Product> index = productIndex();

            // Drop cart entries whose product was deleted.
            cart.keySet().removeIf(id -> !index.containsKey(id));

            for (Map.Entry<Integer, Integer> entry : cart.entrySet()) {
                Product product = index.get(entry.getKey());
                int quantity = entry.getValue();

                BigDecimal lineTotal =
                        product.sellingPrice().multiply(
                                BigDecimal.valueOf(quantity)
                        );

                total = total.add(lineTotal);
                units += quantity;

                lines.add(new CartLine(
                        product.id(),
                        product.name(),
                        quantity,
                        product.sellingPrice(),
                        lineTotal
                ));
            }

        } catch (Exception ex) {
            showError("Could not refresh cart", ex);
        }

        cartRows.setAll(lines);
        cartSum.set(total);
        totalLabel.setText(peso(total));
        itemCountLabel.setText(units + (units == 1 ? " item" : " items"));
    }

    // =========================
    // INVENTORY MANAGEMENT
    // =========================

    private Node inventoryView() {

        VBox page = page(
                "Inventory",
                "Add new products and see what is running low."
        );

        TextField name = new TextField();
        name.setPromptText("e.g. Pancit Canton");

        TextField barcode = new TextField();
        barcode.setPromptText("Optional");

        ComboBox<String> category = new ComboBox<>(
                FXCollections.observableArrayList(
                        "Beverages",
                        "Snacks",
                        "Canned Goods",
                        "Toiletries",
                        "Cigarettes",
                        "Others"
                )
        );
        category.setPromptText("Choose a category");
        category.setMaxWidth(Double.MAX_VALUE);

        TextField cost = new TextField("0.00");
        TextField price = new TextField();
        price.setPromptText("0.00");
        TextField stock = new TextField("0");
        TextField threshold = new TextField("5");
        TextField pack = new TextField("1");

        Button save = new Button("Add product");
        save.getStyleClass().add("primary");

        // Two rows of four labelled fields.
        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(12);

        for (int i = 0; i < 4; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(25);
            column.setHgrow(Priority.ALWAYS);
            form.getColumnConstraints().add(column);
        }

        form.add(field("Product name", name), 0, 0);
        form.add(field("Barcode", barcode), 1, 0);
        form.add(field("Category", category), 2, 0);
        form.add(field("Units per pack", pack), 3, 0);
        form.add(field("Selling price (₱)", price), 0, 1);
        form.add(field("Cost price (₱)", cost), 1, 1);
        form.add(field("Initial stock", stock), 2, 1);
        form.add(field("Low-stock alert at", threshold), 3, 1);

        HBox actions = new HBox(save);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox formCard = card(
                cardHeader("New product"),
                cardHint(
                        "Prices are per sellable item, and stock is "
                        + "counted in individual items."
                ),
                form,
                actions
        );

        formCard.setSpacing(10);

        // ---- Product table ----

        TableView<Product> table = new TableView<>();
        table.setPlaceholder(emptyState(
                "No products yet",
                "Add your first product using the form above."
        ));

        table.getColumns().add(col("Name", Product::name));
        table.getColumns().add(col("Category", Product::category));
        table.getColumns().add(stockColumn());

        table.getColumns().add(
                colNum("Selling price", p -> peso(p.sellingPrice()))
        );

        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        Label countChip = chip("0 products", "badge-neutral");
        Label lowChip = chip("0 low on stock", "badge-neutral");

        Runnable load = () -> {
            try {
                var all = products.all();

                table.getItems().setAll(all);

                long low = all.stream()
                        .filter(Product::isLowStock)
                        .count();

                setBadge(
                        countChip,
                        all.size() + (all.size() == 1
                                ? " product"
                                : " products"),
                        "badge-neutral"
                );

                setBadge(
                        lowChip,
                        low + " low on stock",
                        low > 0 ? "badge-warning" : "badge-neutral"
                );
            } catch (Exception ex) {
                showError("Could not load inventory", ex);
            }
        };

        save.setOnAction(event -> {

            try {
                String productName = name.getText().trim();
                String barcodeValue = barcode.getText().trim();

                if (productName.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Please enter a product name."
                    );
                }

                BigDecimal costPrice =
                        parseMoney(cost.getText(), "Cost price");

                BigDecimal sellingPrice =
                        parseMoney(price.getText(), "Selling price");

                int quantity =
                        parseInt(stock.getText(), "Initial stock");

                int lowStock =
                        parseInt(threshold.getText(), "Low-stock alert");

                int unitsPerPack =
                        parseInt(pack.getText(), "Units per pack");

                if (costPrice.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException(
                            "Cost price cannot be negative."
                    );
                }

                if (sellingPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException(
                            "Selling price must be greater than zero."
                    );
                }

                if (quantity < 0 || lowStock < 0) {
                    throw new IllegalArgumentException(
                            "Stock and low-stock alert cannot be negative."
                    );
                }

                if (unitsPerPack < 1) {
                    throw new IllegalArgumentException(
                            "Units per pack must be at least 1."
                    );
                }

                String selectedCategory = category.getValue() == null
                        ? "Others"
                        : category.getValue();

                products.save(
                        barcodeValue,
                        productName,
                        selectedCategory,
                        costPrice,
                        sellingPrice,
                        quantity,
                        lowStock,
                        unitsPerPack,
                        "piece/pack",
                        "piece"
                );

                // Clear only after the database save succeeds.
                name.clear();
                barcode.clear();
                cost.setText("0.00");
                price.clear();
                stock.setText("0");
                threshold.setText("5");
                pack.setText("1");
                category.setValue(null);

                load.run();

                alert("Product added successfully.");

            } catch (Exception ex) {
                showError("Could not add product", ex);
            }
        });

        load.run();
        refreshInventory = load;

        VBox tableCard = card(
                cardHeader("Products", countChip, lowChip),
                table
        );

        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        page.getChildren().addAll(formCard, tableCard);

        return page;
    }

    // =========================
    // TABLE COLUMN HELPERS
    // =========================

    private <T> TableColumn<T, String> col(
            String title,
            Function<T, String> formatter) {

        TableColumn<T, String> column = new TableColumn<>(title);

        column.setCellValueFactory(data ->
                new SimpleStringProperty(
                        formatter.apply(data.getValue())
                )
        );

        return column;
    }

    /** Same as col(), but right-aligned for money and quantities. */
    private <T> TableColumn<T, String> colNum(
            String title,
            Function<T, String> formatter) {

        TableColumn<T, String> column = col(title, formatter);
        column.getStyleClass().add("numeric");

        return column;
    }

    /** Stock quantity shown as a coloured badge (ok / low / out). */
    private TableColumn<Product, Product> stockColumn() {

        TableColumn<Product, Product> column = new TableColumn<>("Stock");

        column.setCellValueFactory(data ->
                new ReadOnlyObjectWrapper<>(data.getValue())
        );

        column.setCellFactory(c -> new TableCell<>() {

            private final Label badge = new Label();

            @Override
            protected void updateItem(Product product, boolean empty) {
                super.updateItem(product, empty);

                if (empty || product == null) {
                    setGraphic(null);
                    return;
                }

                setStockBadge(badge, product, true);
                setGraphic(badge);
            }
        });

        return column;
    }

    // =========================
    // CUSTOMER CREDIT LEDGER
    // =========================

    private Node creditView() {

        VBox page = page(
                "Listahan sa utang",
                "Track what customers owe and record their repayments."
        );

        TextField name = new TextField();
        name.setPromptText("Customer name");

        TextField phone = new TextField();
        phone.setPromptText("Phone");

        TextField address = new TextField();
        address.setPromptText("Address / notes");

        Button create = new Button("Add customer");
        create.getStyleClass().add("primary");

        VBox nameField = field("Customer name", name);
        VBox phoneField = field("Phone", phone);
        VBox addressField = field("Address / notes", address);

        HBox.setHgrow(nameField, Priority.ALWAYS);
        HBox.setHgrow(phoneField, Priority.ALWAYS);
        HBox.setHgrow(addressField, Priority.ALWAYS);

        HBox formRow = new HBox(
                12, nameField, phoneField, addressField, create
        );
        formRow.setAlignment(Pos.BOTTOM_LEFT);

        VBox formCard = card(cardHeader("New customer"), formRow);

        // ---- Customer table ----

        TableView<Customer> table = new TableView<>();
        table.setPlaceholder(emptyState(
                "No customers yet",
                "Add a customer above to start tracking utang."
        ));

        table.getColumns().add(col("Customer", Customer::name));

        table.getColumns().add(
                col("Phone", c -> Objects.toString(c.phone(), ""))
        );

        TableColumn<Customer, String> owed =
                colNum("Outstanding", c -> peso(c.currentDebt()));

        // Anything still owed is shown in red so it stands out.
        owed.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);

                if (empty || text == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(text);

                boolean hasDebt = !text.equals(peso(BigDecimal.ZERO));

                setStyle(
                        hasDebt
                                ? "-fx-text-fill: #b83a2b;"
                                  + "-fx-font-weight: bold;"
                                : ""
                );
            }
        });

        table.getColumns().add(owed);

        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        Label owedChip = chip("Total owed: " + peso(BigDecimal.ZERO),
                "badge-neutral");

        Runnable load = () -> {
            try {
                var all = customers.all();

                table.getItems().setAll(all);

                BigDecimal totalOwed = BigDecimal.ZERO;

                for (Customer c : all) {
                    if (c.currentDebt() != null) {
                        totalOwed = totalOwed.add(c.currentDebt());
                    }
                }

                setBadge(
                        owedChip,
                        "Total owed: " + peso(totalOwed),
                        totalOwed.signum() > 0
                                ? "badge-danger"
                                : "badge-neutral"
                );
            } catch (Exception ex) {
                showError("Could not load customers", ex);
            }
        };

        create.setOnAction(event -> {

            try {
                if (name.getText().isBlank()) {
                    warn("Enter a customer name.");
                    return;
                }

                customers.create(
                        name.getText().trim(),
                        phone.getText().trim(),
                        address.getText().trim()
                );

                load.run();

                name.clear();
                phone.clear();
                address.clear();

                alert("Customer added successfully.");

            } catch (Exception ex) {
                showError("Could not add customer", ex);
            }
        });

        Button repay = new Button("Record repayment");
        repay.getStyleClass().add("primary");

        repay.setOnAction(event -> {

            Customer selected =
                    table.getSelectionModel().getSelectedItem();

            if (selected == null) {
                warn("Select a customer first.");
                return;
            }

            TextInputDialog dialog = new TextInputDialog();
            styleDialog(dialog);

            dialog.setTitle("Record Repayment");
            dialog.setHeaderText(
                    "Cash repayment for " + selected.name()
                    + "\nOutstanding: " + peso(selected.currentDebt())
            );
            dialog.setContentText("Amount:");

            dialog.showAndWait().ifPresent(value -> {

                try {
                    BigDecimal amount = parseMoney(value, "Repayment");

                    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException(
                                "Repayment must be greater than zero."
                        );
                    }

                    if (amount.compareTo(selected.currentDebt()) > 0) {
                        throw new IllegalArgumentException(
                                "Repayment cannot exceed outstanding debt."
                        );
                    }

                    customers.repay(selected.id(), amount);

                    load.run();

                    alert("Repayment recorded successfully.");

                } catch (Exception ex) {
                    showError("Could not record repayment", ex);
                }
            });
        });

        load.run();
        refreshCredit = load;

        VBox tableCard = card(
                cardHeader("Customers", owedChip, repay),
                table
        );

        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        page.getChildren().addAll(formCard, tableCard);

        return page;
    }

    // =========================
    // SALES REPORTS
    // =========================

    private Node reportView() {

        VBox page = page(
                "Sales reports",
                "See how the store is doing for the period you pick."
        );

        ToggleGroup periodGroup = new ToggleGroup();

        ToggleButton day = segment("Today", "DAY", periodGroup);
        ToggleButton week = segment("This week", "WEEK", periodGroup);
        ToggleButton month = segment("This month", "MONTH", periodGroup);

        day.setSelected(true);
        keepOneSelected(periodGroup);

        Label cash = new Label(peso(BigDecimal.ZERO));
        Label debt = new Label(peso(BigDecimal.ZERO));
        Label profit = new Label(peso(BigDecimal.ZERO));

        Label cashCaption = new Label("Today");
        Label debtCaption = new Label("All customers, all time");
        Label profitCaption = new Label("Today");

        Button refresh = new Button("Refresh");

        refresh.setOnAction(event -> {

            try {
                Toggle selectedToggle = periodGroup.getSelectedToggle();

                String selectedPeriod = selectedToggle == null
                        ? "DAY"
                        : (String) selectedToggle.getUserData();

                String condition =
                        ReportService.periodCondition(selectedPeriod);

                String periodName = switch (selectedPeriod) {
                    case "WEEK" -> "This week";
                    case "MONTH" -> "This month";
                    default -> "Today";
                };

                cash.setText(peso(reports.cashCollected(condition)));
                debt.setText(peso(reports.creditOutstanding()));
                profit.setText(peso(reports.grossProfit(condition)));

                cashCaption.setText(periodName);
                profitCaption.setText(periodName);

            } catch (Exception ex) {
                showError("Could not load reports", ex);
            }
        });

        // Changing the period updates the numbers immediately.
        periodGroup.selectedToggleProperty().addListener(
                (observable, oldToggle, newToggle) -> {
                    if (newToggle != null) {
                        refresh.fire();
                    }
                }
        );

        Region toolbarSpacer = new Region();
        HBox.setHgrow(toolbarSpacer, Priority.ALWAYS);

        HBox toolbar = new HBox(
                12,
                segmented(false, day, week, month),
                toolbarSpacer,
                refresh
        );
        toolbar.setAlignment(Pos.CENTER_LEFT);

        HBox kpis = new HBox(
                16,
                kpiCard("Cash collected", cash, cashCaption, "kpi-green"),
                kpiCard("Outstanding utang", debt, debtCaption, "kpi-red"),
                kpiCard(
                        "Estimated gross profit",
                        profit,
                        profitCaption,
                        "kpi-gold"
                )
        );

        Label note = new Label(
                "Gross profit uses the cost price saved at "
                + "the time of sale. A top-selling product "
                + "report can be added separately."
        );
        note.getStyleClass().add("callout");
        note.setWrapText(true);
        note.setMaxWidth(Double.MAX_VALUE);

        page.getChildren().addAll(toolbar, kpis, note);

        refreshReports = refresh::fire;
        refresh.fire();

        return page;
    }

    private static VBox kpiCard(
            String title,
            Label value,
            Label caption,
            String dotClass) {

        Region dot = new Region();
        dot.getStyleClass().addAll("kpi-dot", dotClass);

        Label heading = new Label(title);
        heading.getStyleClass().add("kpi-title");

        HBox top = new HBox(8, dot, heading);
        top.setAlignment(Pos.CENTER_LEFT);

        value.getStyleClass().add("kpi-value");
        caption.getStyleClass().add("kpi-caption");

        VBox box = new VBox(6, top, value, caption);
        box.getStyleClass().addAll("card", "kpi-card");
        box.setPrefWidth(240);
        box.setMaxWidth(Double.MAX_VALUE);

        HBox.setHgrow(box, Priority.ALWAYS);

        return box;
    }

    // =========================
    // INPUT + FORMAT HELPERS
    // =========================

    /** Formats any number (normally a BigDecimal) as ₱1,234.50. */
    private static String peso(Object amount) {

        BigDecimal value;

        try {
            value = amount instanceof BigDecimal bd
                    ? bd
                    : new BigDecimal(String.valueOf(amount));
        } catch (NumberFormatException ex) {
            return "₱" + amount;
        }

        DecimalFormat format = new DecimalFormat(
                "#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.US)
        );
        format.setRoundingMode(RoundingMode.HALF_UP);

        return "₱" + format.format(value);
    }

    /** Parses a peso amount and rounds it to centavos. */
    private static BigDecimal parseMoney(String text, String field) {
        try {
            return new BigDecimal(
                    text.trim().replace("₱", "").replace(",", "")
            ).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            // Deliberately no cause: this is a user-input problem, not a bug.
            throw new IllegalArgumentException(
                    field + " must be a valid amount, for example 25.50."
            );
        }
    }

    private static int parseInt(String text, String field) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    field + " must be a whole number."
            );
        }
    }

    // =========================
    // ERROR AND ALERT HELPERS
    // =========================

    private void showDialog(
            Alert.AlertType type,
            String title,
            String header,
            String message) {

        Alert dialog = new Alert(
                type,
                message == null ? "" : message,
                ButtonType.OK
        );

        styleDialog(dialog);

        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        dialog.showAndWait();
    }

    private void alert(String message) {
        showDialog(
                Alert.AlertType.INFORMATION,
                "Sari-Sari Store POS",
                null,
                message
        );
    }

    private void warn(String message) {
        showDialog(
                Alert.AlertType.WARNING,
                "Sari-Sari Store POS",
                null,
                message
        );
    }

    private void showError(String title, Exception ex) {

        // Plain validation messages (bad input) are shown as warnings
        // without dumping a stack trace; everything else is a real error.
        boolean validation =
                ex instanceof IllegalArgumentException
                && ex.getCause() == null;

        if (!validation) {
            ex.printStackTrace();
        }

        Throwable root = ex;

        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String message = root.getMessage();

        if (message == null || message.isBlank()) {
            message = root.toString();
        }

        showDialog(
                validation
                        ? Alert.AlertType.WARNING
                        : Alert.AlertType.ERROR,
                title,
                title,
                message
        );

        
    }

    public static void main(String[] args) {
        launch(args);
    }

    
}