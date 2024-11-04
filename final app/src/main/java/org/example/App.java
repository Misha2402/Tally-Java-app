package org.example;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.bson.Document;
import java.io.File;
import javax.swing.*;
import java.awt.*;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import javax.swing.border.EmptyBorder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.mongodb.client.MongoCursor;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;



class SalesPlotPane extends JPanel {
    private MongoCollection<Document> ordCollection;
    public SalesPlotPane(MongoDatabase database) {
        this.ordCollection = database.getCollection("Orders");
        setLayout(new BorderLayout());
    }

    public void plotSales(int n) {
        Map<String, Double> salesData = fetchSalesData(n);
        Object[][] data = prepDataForPlot(salesData);
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Object[] row : data) {
            dataset.addValue((Double) row[1], "Total Sales", (String) row[0]);
        }
        JFreeChart chart = ChartFactory.createLineChart(
                "Total Sales for Last " + Math.min(n, 12) + " Months",
                "Month",
                "Total Sales",
                dataset
        );
        // Create and display chart panel
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(600, 400));

        // Clearing existing components
        removeAll();
        add(chartPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private Map<String, Double> fetchSalesData(int n) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime past = now.minusMonths(Math.min(n, 12)); // Past Date
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        String formattedDate = dtf.format(past);
        Map<String, Double> salesMap = new HashMap<>();
        // Fetch orders from DB
        MongoCursor<Document> cursor = ordCollection.find(Filters.gte("orderTime", formattedDate)).iterator();
        while (cursor.hasNext()) {
            Document order = cursor.next();
            String orderTime = order.getString("orderTime");
            double total = order.getDouble("total");
            String month = orderTime.substring(0, 7); // Extract YYYY-MM
            // Aggregate sales per month
            salesMap.put(month, salesMap.getOrDefault(month, 0.0) + total);
        }
        cursor.close();
        return salesMap;
    }

    private Object[][] prepDataForPlot(Map<String, Double> salesData) {
        Object[][] data = new Object[salesData.size()][2];
        int index = 0;
        for (Map.Entry<String, Double> entry : salesData.entrySet()) {
            data[index][0] = entry.getKey(); // Month
            data[index][1] = entry.getValue(); // Total sales
            index++;
        }
        System.out.println("Data for plotting:");
        for (Object[] row : data) {
            System.out.printf("Month: %s, Total Sales: %.2f%n", row[0], row[1]);
        }
        return data;
    }
}

class OrderProcessingPane extends JPanel {
    private JTextField prodIdField;
    private JTextField quantField;
    private JPanel orderListPanel;
    private JScrollPane scrollPane;
    private JButton finishOrderButton;
    private Map<String, Integer> orderItems;
    private MongoDatabase database; // Declare database as a class field
    private MongoCollection<Document> ordersCollection;

    public OrderProcessingPane(MongoDatabase database) {
        setLayout(new BorderLayout());
        this.orderItems = new HashMap<>();
        this.database = database;
        this.ordersCollection = database.getCollection("Orders");
        // Input panel for product ID and quantity
        JPanel inpPanel = new JPanel(new GridLayout(3, 2));
        inpPanel.add(new JLabel("Product ID:"));
        prodIdField = new JTextField();
        inpPanel.add(prodIdField);
        inpPanel.add(new JLabel("Quantity:"));
        quantField = new JTextField();
        inpPanel.add(quantField);
        JButton addItemButton = new JButton("Add Item");
        addItemButton.setAlignmentX(Component.CENTER_ALIGNMENT); // Center the "Add Item" button
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(addItemButton);
        inpPanel.add(new JLabel());  // Spacer to keep grid layout alignment
        inpPanel.add(buttonPanel);
        // Panel of ordered items
        orderListPanel = new JPanel();
        orderListPanel.setLayout(new BoxLayout(orderListPanel, BoxLayout.Y_AXIS));
        // Scroll pane setup
        scrollPane = new JScrollPane(orderListPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(new Dimension(300, 200));
        finishOrderButton = new JButton("Finish Order");
        // Add components to main panel
        add(inpPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(finishOrderButton, BorderLayout.SOUTH);
        addItemButton.addActionListener(e -> addItemToOrder());
        finishOrderButton.addActionListener(e -> finishOrder());
    }

    private void addItemToOrder() {
        String productId = prodIdField.getText();
        int quantity;
        try {
            quantity = Integer.parseInt(quantField.getText());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Not a valid quantity", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Check if the product ID exists in the inventory
        MongoCollection<Document> inventoryCollection = database.getCollection("Inventory");
        Document query = new Document("product_id", productId);
        Document product = inventoryCollection.find(query).first();
        if (product == null) {
            JOptionPane.showMessageDialog(this, "Invalid product ID", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        else if (product.getInteger("Quantity") < quantity) {
            JOptionPane.showMessageDialog(this, "Given quantity exceeds stock", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // If valid item
        if (!orderItems.containsKey(productId)) {
            orderItems.put(productId, quantity);
            addItemToOrderListPanel(productId, quantity);
        } else {
            JOptionPane.showMessageDialog(this, "Item already added. Please update quantity", "Info", JOptionPane.INFORMATION_MESSAGE);
        }
        // Clear input fields
        prodIdField.setText("");
        quantField.setText("");
    }

    private void addItemToOrderListPanel(String productId, int quantity) {
        JPanel itemPanel = new JPanel(new BorderLayout());
        itemPanel.setBorder(new EmptyBorder(5, 10, 5, 10));
        itemPanel.setPreferredSize(new Dimension(orderListPanel.getWidth(), 40));
        itemPanel.setMaximumSize(new Dimension(orderListPanel.getWidth(), 40));
        MongoCollection<Document> inventoryCollection = database.getCollection("Inventory");
        Document query = new Document("product_id", productId);
        Document product = inventoryCollection.find(query).first();
        JLabel itemLabel = new JLabel("ID: " + productId + " | Item: " + product.getString("product_name") + " | Units: " + quantity);
        itemPanel.add(itemLabel, BorderLayout.CENTER);
        JButton deleteButton = new JButton("X");
        deleteButton.setPreferredSize(new Dimension(24, 24));
        deleteButton.setFocusPainted(false);
        deleteButton.setFont(new Font("Arial", Font.BOLD, 10)); // Reduced font size
        deleteButton.setForeground(Color.RED); // Set color for "X"
        deleteButton.setMargin(new Insets(0, 0, 0, 0)); // Remove padding
        deleteButton.addActionListener(e -> {
            orderItems.remove(productId);
            orderListPanel.remove(itemPanel);
            orderListPanel.revalidate();
            orderListPanel.repaint();
        });
        itemPanel.add(deleteButton, BorderLayout.EAST);
        orderListPanel.add(itemPanel);
        orderListPanel.revalidate();
        orderListPanel.repaint();
    }

    private void finishOrder() {
        double total = calculateTotal();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        String orderTime = dtf.format(LocalDateTime.now());
        // Building order document
        Document order = new Document("orderItems", orderItems)
                .append("total", total)
                .append("orderTime", orderTime);

        ordersCollection.insertOne(order);
        // Update inventory stock for each ordered item
        MongoCollection<Document> inventoryCollection = database.getCollection("Inventory");
        for (Map.Entry<String, Integer> entry : orderItems.entrySet()) {
            String productId = entry.getKey();
            int quantityOrdered = entry.getValue();
            Document product = inventoryCollection.find(new Document("product_id", productId)).first();
            if (product != null) {
                int currentStock = product.getInteger("Quantity");
                int updatedStock = currentStock - quantityOrdered;
                inventoryCollection.updateOne(
                        new Document("product_id", productId),
                        new Document("$set", new Document("Quantity", updatedStock))
                );

            }
        }
        // Display total and reset order
        JOptionPane.showMessageDialog(this, "Order complete! Total: " + total, "Order Finished", JOptionPane.INFORMATION_MESSAGE);
        orderItems.clear();
        orderListPanel.removeAll();
        orderListPanel.revalidate();
        orderListPanel.repaint();
    }

    private double calculateTotal() {
        double total = 0.0;
        MongoCollection<Document> inventoryCollection = database.getCollection("Inventory");
        for (Map.Entry<String, Integer> entry : orderItems.entrySet()) {
            String prodId = entry.getKey();
            int quant = entry.getValue();
            // Fetch the details like MRP and discount from the Inventory collection
            Document product = inventoryCollection.find(new Document("product_id", prodId)).first();
            if (product != null) {
                double pricePerItem = product.getDouble("retail_price"); // Fetch MRP
                double discount = product.getDouble("discount"); // Fetch discount
                // Calculate the item total after applying the discount
                double itemTotal = quant * pricePerItem * (1 - discount/100);
                total += itemTotal;
            } else {
                JOptionPane.showMessageDialog(this, "Product ID " + prodId + " not found in inventory.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        // Apply 18% tax
        total *= 1.18;
        BigDecimal roundedTotal = new BigDecimal(total).setScale(2, RoundingMode.HALF_UP);
        return roundedTotal.doubleValue();
    }
}


class MongoDBConnection {
    private static final String CONNECTION_STRING = "mongodb://localhost:27017"; // Replace with your MongoDB URI
    private static final String DATABASE_NAME = "supermarket";
    private static final String COLLECTION_NAME = "Inventory";
    public static MongoCollection<Document> getInventoryCollection() {
        MongoClient mongoClient = MongoClients.create(CONNECTION_STRING);
        MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
        return database.getCollection(COLLECTION_NAME);
    }
}
class CSVReader {
    public static List<Document> readInventoryCSV(String filePath) {
        List<Document> prods = new ArrayList<>();
        try (FileReader reader = new FileReader(filePath)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .withHeader("product id", "product name", "Retail price", "Discount", "Quantity")
                    .withSkipHeaderRecord()
                    .parse(reader);
            for (CSVRecord record : records) {
                Document product = new Document()
                        .append("product_id", record.get("product id"))
                        .append("product_name", record.get("product name"))
                        .append("retail_price", Double.parseDouble(record.get("Retail price")))
                        .append("discount", Double.parseDouble(record.get("Discount")))
                        .append("Quantity",Integer.parseInt(record.get("Quantity")));
                prods.add(product);
            }
        } catch (IOException e) {
            System.out.println("CSV file failed to be read");
        }
        return prods;
    }
}
class InventoryUpdater {
    private final MongoCollection<Document> inventoryCollection;
    public InventoryUpdater() {
        inventoryCollection = MongoDBConnection.getInventoryCollection();
    }
    public void updateInventory(List<Document> prods) {
        for (Document prod : prods) {
            String prodId = prod.getString("product_id");
            inventoryCollection.replaceOne(
                    Filters.eq("product_id", prodId),
                    prod,
                    new ReplaceOptions().upsert(true)
            );
        }
        System.out.println("Inventory updated successfully.");
    }
}

public class App {
    private JPanel mainPanel;
    private JPanel contentPanel;
    private JList<String> menuList;
    private CardLayout cardLayout;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private SalesPlotPane salesPlotPane;

    public App() {
        // Initialize MongoDB connection
        mongoClient = MongoClients.create("mongodb://localhost:27017");
        database = mongoClient.getDatabase("supermarket");
        salesPlotPane = new SalesPlotPane(database);

        // Set up the content panel with CardLayout for content switching
        contentPanel = new JPanel(new CardLayout());
        cardLayout = (CardLayout) contentPanel.getLayout();

        // Initialize and add panels to contentPanel with card layout
        OrderProcessingPane orderProcessingPane = new OrderProcessingPane(database);
        contentPanel.add(new JPanel(), "Empty");
        contentPanel.add(orderProcessingPane, "OrderProcessing");
        contentPanel.add(salesPlotPane, "SalesPlotting");

        DefaultListModel<String> menuModel = new DefaultListModel<>();
        menuModel.addElement("Update Inventory");
        menuModel.addElement("Process Order");
        menuModel.addElement("Plots");
        menuList = new JList<>(menuModel);
        JScrollPane menuScrollPane = new JScrollPane(menuList);
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(menuScrollPane, BorderLayout.WEST);
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        // Event handler for menu selection
        menuList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedItem = menuList.getSelectedValue();
                if ("Update Inventory".equals(selectedItem)) {
                    // Call the new file selection method
                    selectUpdateInventory();
                } else if ("Process Order".equals(selectedItem)) {
                    cardLayout.show(contentPanel, "OrderProcessing");
                } else if ("Plots".equals(selectedItem)) {
                    salesPlotPane.plotSales(12); // Call the plotting method with n=12
                    cardLayout.show(contentPanel, "SalesPlotting"); // Show the SalesPlotting panel
                } else {
                    cardLayout.show(contentPanel, "Empty");
                }
            }
        });
    }

    // Method to open file chooser and update inventory
    private void selectUpdateInventory() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Inventory CSV File");
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                updateInventory(selectedFile.getAbsolutePath());
                JOptionPane.showMessageDialog(null, "Inventory updated successfully!");
            } catch (IOException ex) {
                System.out.println("Inventory update failed");
                JOptionPane.showMessageDialog(null, "Failed to update inventory.");
            }
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Supermarket Inventory Manager");
        frame.setContentPane(new App().mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setVisible(true);
    }

    public void updateInventory(String csvFilePath) throws IOException {
        List<Document> products = CSVReader.readInventoryCSV(csvFilePath);
        InventoryUpdater updater = new InventoryUpdater();
        updater.updateInventory(products);
    }
}