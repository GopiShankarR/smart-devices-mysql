package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import java.io.BufferedReader;
import java.io.IOException;

import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.text.SimpleDateFormat;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.annotation.WebServlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MySQLDataStoreUtilities {
  private static final String DB_URL = "jdbc:mysql://localhost:3306/enterprise";
  private static final String DB_USER = "root";
  private static final String DB_PASSWORD = "root";

  static {
    try {
        Class.forName("com.mysql.cj.jdbc.Driver");
    } catch (ClassNotFoundException e) {
        System.out.println("MySQL JDBC Driver not found");
        e.printStackTrace();
    }
  }

  public static Connection getConnection() throws SQLException {
    return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
  }

  public static boolean userExists(String username, String userType) {
    String query = "SELECT * FROM users WHERE username = ? AND user_type = ?";

    try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, username);
      stmt.setString(2, userType);
      ResultSet rs = stmt.executeQuery();

      return rs.next();

    } catch (SQLException e) {
      e.printStackTrace();
      return false;
    }
  }

  public static int getUserIdByUsername(String username) throws SQLException {
    String query = "SELECT user_id FROM users WHERE username = ?";

    try(Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, username);
      ResultSet rs = stmt.executeQuery();
      if(rs.next()) {
        return rs.getInt("user_id");
      } else {
        throw new SQLException("User not found for username:" + username);
      }
    }
  }

  public static String fetchProductImageFromMySQL(String productModelName) {
    String imageUrl = "";
    String query = "SELECT imageurl FROM products WHERE product_name = ?";

    try (Connection conn = MySQLDataStoreUtilities.getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, productModelName);
      ResultSet rs = stmt.executeQuery();

      if (rs.next()) {
          imageUrl = rs.getString("imageurl");
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }

    return imageUrl;
  }

  public static int saveCustomerAddress(int userId, String name, String street, String city, String state, String zipCode) throws SQLException {
    System.out.println("Inside saveCustomerAddress method ----------------------------");
    String query = "INSERT INTO customer_address (user_id, name, street, city, state, zip_code) " +
                  "VALUES (?, ?, ?, ?, ?, ?)";
    String generatedColumns[] = { "customer_address_id" };

    try (Connection conn = getConnection();
      PreparedStatement stmt = conn.prepareStatement(query, generatedColumns)) {

      stmt.setInt(1, userId); 
      stmt.setString(2, name); 
      stmt.setString(3, street);  
      stmt.setString(4, city);
      stmt.setString(5, state);  
      stmt.setString(6, zipCode); 

      int rows = stmt.executeUpdate();
      System.out.println("Address inserted, rows affected: " + rows);

      try (ResultSet rs = stmt.getGeneratedKeys()) {
        if (rs.next()) {
          int customerAddressId = rs.getInt(1);
          System.out.println("Generated customer_address_id: " + customerAddressId);
          return customerAddressId; 
        }
      }
    }
    throw new SQLException("Failed to insert customer address or retrieve customer_address_id.");
  }

  public static int getStoreIdByStoreName(String storeName) throws SQLException {
    String query = "SELECT store_id FROM stores WHERE store_name = ?";
    
    try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {

      stmt.setString(1, storeName); 
      ResultSet rs = stmt.executeQuery();

      if (rs.next()) {
          return rs.getInt("store_id");
      }
    }

    return -1; 
  }

  public static List<OrderInfo> getOrdersWithProductsByUsername(String username) throws SQLException {
    String query = "SELECT u.user_id, o.confirmation_number, o.delivery_date, o.order_placed_date, o.status, o.delivery_option, " +
                   "p.product_name, p.product_id, p.category, p.price, p.manufacturer, p.product_quantity, p.on_sale, p.manufacturer_rebate " +
                   "FROM orders o " +
                   "JOIN order_items oi ON o.order_id = oi.order_id " +
                   "JOIN products p ON oi.product_id = p.product_id " +
                   "JOIN users u ON o.user_id = u.user_id " +
                   "WHERE u.username = ?";

    List<OrderInfo> orders = new ArrayList<>();

    try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {

      stmt.setString(1, username);
      ResultSet rs = stmt.executeQuery();

      Map<String, OrderInfo> orderMap = new HashMap<>();

      while (rs.next()) {
        int userId = rs.getInt("user_id");
        String confirmationNumber = rs.getString("confirmation_number");
        String deliveryDate = rs.getString("delivery_date");
        String orderPlacedDate = rs.getString("order_placed_date");
        String deliveryOption = rs.getString("delivery_option");
        String status = rs.getString("status");
        String productName = rs.getString("product_name");
        int productId = rs.getInt("product_id");
        String productCategory = rs.getString("category");
        double productPrice = rs.getDouble("price");
        String productManufacturer = rs.getString("manufacturer");

        if (!orderMap.containsKey(confirmationNumber)) {
            OrderInfo newOrder = new OrderInfo(userId, username, confirmationNumber, deliveryDate, orderPlacedDate, status, deliveryOption);
            orderMap.put(confirmationNumber, newOrder);
            orders.add(newOrder);
        }

        orderMap.get(confirmationNumber).addProduct(new ProductInfo(productId, productName, productCategory, productPrice, productManufacturer));
      }
    }

    return orders;
  }


  public static List<OrderInfo> getOrdersByUsername(String username) throws SQLException {
    String query = "SELECT u.user_id, u.username, o.confirmation_number, o.delivery_date, o.order_placed_date, o.status, o.delivery_option " +
                    "FROM orders o JOIN users u ON o.user_id = u.user_id " +
                    "WHERE u.username = ?";

    List<OrderInfo> orders = new ArrayList<>();

    try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {

      stmt.setString(1, username);
      ResultSet rs = stmt.executeQuery();

      while (rs.next()) {
        int userId = rs.getInt("user_id");
        String user = rs.getString("username");
        String confirmationNumber = rs.getString("confirmation_number");
        String deliveryDate = rs.getString("delivery_date");
        String orderPlacedDate = rs.getString("order_placed_date");
        String deliveryOption = rs.getString("delivery_option");
        String status = rs.getString("status");

        OrderInfo order = new OrderInfo(userId, user, confirmationNumber, deliveryDate, orderPlacedDate, status, deliveryOption);
        orders.add(order);
      }
    }

    return orders;
  }

  public static boolean createUser(String username, String password, String userType) {
    String query = "INSERT INTO Users (username, password, user_type) VALUES (?, ?, ?)";

    try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, username);
      stmt.setString(2, password);
      stmt.setString(3, userType);

      int result = stmt.executeUpdate();

      return result > 0;

    } catch (SQLException e) {
        e.printStackTrace();
        return false;
    }
  }

  public static String authenticateUser(String username, String password) throws SQLException {
    String query = "SELECT password, user_type FROM users WHERE username = ?";
    
    try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, username);
      ResultSet rs = stmt.executeQuery();

      if (rs.next()) {
        String dbPassword = rs.getString("password");
        String userType = rs.getString("user_type");

        if (dbPassword.equals(password)) {
          return userType; 
        }
      }
    }
    return null; 
  }

  public static int getOrCreateCartId(String username) throws SQLException {
      String cartCheckQuery = "SELECT cart_id FROM cart WHERE user_id = (SELECT user_id FROM users WHERE username = ?)";
      String cartInsertQuery = "INSERT INTO cart (user_id) SELECT user_id FROM users WHERE username = ?";

      try (Connection conn = getConnection(); PreparedStatement checkStmt = conn.prepareStatement(cartCheckQuery)) {
        checkStmt.setString(1, username);
        ResultSet rs = checkStmt.executeQuery();

        if (rs.next()) {
            return rs.getInt("cart_id"); 
        } else {
          try (PreparedStatement insertStmt = conn.prepareStatement(cartInsertQuery)) {
            insertStmt.setString(1, username);
            insertStmt.executeUpdate();
          }

          ResultSet newRs = checkStmt.executeQuery();
          if (newRs.next()) {
            return newRs.getInt("cart_id");
          }
        }
      }
      return -1;
  }

    public static void addItemToCart(int cartId, String itemId, double itemPrice, int quantity, String imageUrl) throws SQLException {
      String query = "INSERT INTO cart_items (cart_id, product_id, quantity, image_url) " +
                      "VALUES (?, ?, ?, ?) " +
                      "ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)";
      
      try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.setInt(1, cartId);
        stmt.setString(2, itemId);
        stmt.setInt(3, quantity);
        stmt.setString(4, imageUrl);
        stmt.executeUpdate();
      }
    }

    public static void decreaseItemQuantity(int cartId, String itemId) throws SQLException {
      String updateQuery = "UPDATE cart_items SET quantity = quantity - 1 WHERE cart_id = ? AND product_id = ? AND quantity > 0";
      String deleteQuery = "DELETE FROM cart_items WHERE cart_id = ? AND product_id = ? AND quantity = 0";

      try (Connection conn = getConnection(); PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {

        updateStmt.setInt(1, cartId);
        updateStmt.setString(2, itemId);
        updateStmt.executeUpdate();

        try (PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery)) {
          deleteStmt.setInt(1, cartId);
          deleteStmt.setString(2, itemId);
          deleteStmt.executeUpdate();
        }
      }
    }

    public static void deleteItemFromCart(int cartId, String itemId) throws SQLException {
      String query = "DELETE FROM cart_items WHERE cart_id = ? AND product_id = ?";

      try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.setInt(1, cartId);
        stmt.setString(2, itemId);
        stmt.executeUpdate();
      }
    }

    public static List<CartItem> getCartItems(String username) throws SQLException {
        List<CartItem> cartItems = new ArrayList<>();
        String query = "SELECT p.product_id, p.product_name, p.imageurl, p.price, ci.quantity " +
                       "FROM cart_items ci " +
                       "JOIN cart c ON ci.cart_id = c.cart_id " +
                       "JOIN products p ON ci.product_id = p.product_id " +
                       "JOIN users u ON u.user_id = c.user_id " +
                       "WHERE u.username = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String productId = rs.getString("product_id");
                String productName = rs.getString("product_name");
                String productImage = rs.getString("imageurl");
                double price = rs.getDouble("price");
                int quantity = rs.getInt("quantity");

                CartItem cartItem = new CartItem(username, productId, productName, productImage, price, quantity);
                cartItems.add(cartItem);
            }
        }
        return cartItems;
    }

    public static double calculateTotalPrice(List<CartItem> cartItems) {
        return cartItems.stream().mapToDouble(item -> item.getPrice() * item.getQuantity()).sum();
    }

    public static List<Store> getStoreLocations() throws SQLException {
      List<Store> stores = new ArrayList<>();
      String query = "SELECT store_id, store_name, street, city, state, zip_code FROM stores";

      try (Connection conn = getConnection();
          PreparedStatement stmt = conn.prepareStatement(query);
          ResultSet rs = stmt.executeQuery()) {

          while (rs.next()) {
              int storeId = rs.getInt("store_id");
              String storeName = rs.getString("store_name");
              String street = rs.getString("street");
              String city = rs.getString("city");
              String state = rs.getString("state");
              String zip_code = rs.getString("zip_code");

              Store store = new Store(storeId, storeName, street, city, state, zip_code);
              stores.add(store);
          }
      }

      return stores;
    }

 public static int saveOrder(String username, String confirmationNumber, double totalPrice, String deliveryOption, JsonObject addressObject, String orderPlacedDate, String deliveryDate, String status, String creditCardNumber, int storeId, JsonObject customerAddressObject) throws SQLException {
    System.out.println("---------------------------------- Inside saveOrder method ----------------------------------");
    System.out.println("u " + username + " confirmationNumber " + confirmationNumber + " totalPrice " + totalPrice + " deliveryOption " + deliveryOption + " addressObject " + addressObject + " orderPlacedDate " + orderPlacedDate + " deliveryDate " + deliveryDate + " status " + status + " creditCardNumber " + creditCardNumber + " storeId " + storeId + " customerAddressObject " + customerAddressObject);

    String query = "INSERT INTO orders (user_id, confirmation_number, order_placed_date, delivery_option, status, total_sales, store_pickup_id, customer_address_id, home_delivery_id, delivery_date, credit_card_number, shipping_cost) "
                  + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    int userId = getUserIdByUsername(username);

    int addressId = 0; 
    int storePickupId = 0; 
    int homeDeliveryId = 0; 

    int orderId = -1;
    try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
        stmt.setInt(1, userId);
        stmt.setString(2, confirmationNumber);
        stmt.setString(3, orderPlacedDate);
        stmt.setString(4, deliveryOption);
        stmt.setString(5, status);
        stmt.setDouble(6, totalPrice);
        stmt.setInt(7, storeId);
        stmt.setNull(8, java.sql.Types.INTEGER);  
        stmt.setNull(9, java.sql.Types.INTEGER);  

        stmt.setString(10, deliveryDate);
        stmt.setString(11, creditCardNumber);
        stmt.setDouble(12, 10);

        stmt.executeUpdate();

        ResultSet rs = stmt.getGeneratedKeys();
        if (rs.next()) {
            orderId = rs.getInt(1); 
        }
    }

    insertCustomerAddressQuery(userId, customerAddressObject);


    if (orderId > 0) {
        if (deliveryOption.equals("storePickup")) {
            String storeName = addressObject.get("name").getAsString();
            String street = addressObject.get("street").getAsString();
            String city = addressObject.get("city").getAsString();
            String state = addressObject.get("state").getAsString();
            String zipCode = addressObject.get("zip").getAsString();
            String pickupDate = deliveryDate;  

            storePickupId = saveInStorePickup(orderId, storeId, storeName, street, city, state, zipCode, pickupDate);
            System.out.println("Store pickup ID saved: " + storePickupId);

            updateOrderWithStorePickupId(orderId, storeId, storePickupId);
        } else if (deliveryOption.equals("homeDelivery")) {

            String name = addressObject.get("name").getAsString();
            String street = addressObject.get("street").getAsString();
            String city = addressObject.get("city").getAsString();
            String state = addressObject.get("state").getAsString();
            String zipCode = addressObject.get("zip_code").getAsString();

            homeDeliveryId = saveHomeDelivery(userId, name, street, city, state, zipCode);
            System.out.println("Home delivery ID saved: " + homeDeliveryId);

            updateOrderWithHomeDeliveryId(orderId, homeDeliveryId);
        }
    }

    return orderId;
}


public static void updateOrderWithStorePickupId(int orderId, int storeId, int storePickupId) throws SQLException {
  System.out.println(storePickupId + " " + orderId);
    String updateQuery = "UPDATE orders SET store_id = ?, store_pickup_id = ? WHERE order_id = ?";
    
    try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
        stmt.setInt(1, storeId);
        stmt.setInt(2, storePickupId);
        stmt.setInt(3, orderId);
        stmt.executeUpdate();
    }
}

public static void updateOrderWithHomeDeliveryId(int orderId, int homeDeliveryId) throws SQLException {
    String updateQuery = "UPDATE orders SET home_delivery_id = ? WHERE order_id = ?";
    
    try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
        stmt.setInt(1, homeDeliveryId);
        stmt.setInt(2, orderId);
        stmt.executeUpdate();
    }
}



    public static void saveOrderItems(int orderId, JsonArray itemsArray) throws SQLException {
        String query = "INSERT INTO order_items (order_id, product_id, quantity, price) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
            for (int i = 0; i < itemsArray.size(); i++) {
                JsonObject item = itemsArray.get(i).getAsJsonObject();
                stmt.setInt(1, orderId);
                stmt.setString(2, item.get("id").getAsString());
                stmt.setInt(3, item.get("quantity").getAsInt());
                stmt.setDouble(4, item.get("price").getAsDouble());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
    
    public static void insertCustomerAddressQuery(int userId, JsonObject customerAddressObject) throws SQLException {

      String street = customerAddressObject.get("street").getAsString();
      String city = customerAddressObject.get("city").getAsString();
      String state = customerAddressObject.get("state").getAsString();
      String zipCode = customerAddressObject.get("zip_code").getAsString();

      String query = "INSERT INTO customer_address (street, city, state, zip_code, user_id) VALUES (?, ?, ?, ?, ?)";
      try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.setString(1, street);
        stmt.setString(2, city);
        stmt.setString(3, state);
        stmt.setString(4, zipCode);
        stmt.setInt(5, userId);
        stmt.addBatch();
        stmt.executeBatch();
      }
    }

    public static int saveHomeDelivery(int userId, String name, String street, String city, String state, String zipCode) throws SQLException {
      System.out.println("Inside saveHomeDelivery method ----------------------------");
      String query = "INSERT INTO home_delivery (user_id, name, street, city, state, zip_code) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
      String generatedColumns[] = { "home_delivery_id" };

      try (Connection conn = getConnection();
          PreparedStatement stmt = conn.prepareStatement(query, generatedColumns)) {

          stmt.setInt(1, userId);
          stmt.setString(2, name);       
          stmt.setString(3, street);     
          stmt.setString(4, city);       
          stmt.setString(5, state);      
          stmt.setString(6, zipCode);    

          int rows = stmt.executeUpdate();
          System.out.println("Home delivery inserted, rows affected: " + rows);

          try (ResultSet rs = stmt.getGeneratedKeys()) {
              if (rs.next()) {
                  int homeDeliveryId = rs.getInt(1);
                  System.out.println("Generated home_delivery_id: " + homeDeliveryId);
                  return homeDeliveryId; 
              }
          }
      }
      throw new SQLException("Failed to insert home delivery or retrieve home_delivery_id.");
    }

    public static int saveInStorePickup(int orderId, int storeId, String storeName, String street, String city, String state, String zipCode, String pickupDate) throws SQLException {
    String query = "INSERT INTO in_store_pickup (order_id, store_id, storeName, street, city, state, zip_code, pickup_date) "
                 + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    
    try (Connection conn = getConnection();
         PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

        stmt.setInt(1, orderId);
        stmt.setInt(2, storeId);
        stmt.setString(3, storeName);
        stmt.setString(4, street); 
        stmt.setString(5, city);  
        stmt.setString(6, state); 
        stmt.setString(7, zipCode);  
        stmt.setString(8, pickupDate); 

        stmt.executeUpdate();

        ResultSet rs = stmt.getGeneratedKeys();
        if (rs.next()) {
            return rs.getInt(1); 
        }
    }
    return -1;
}

    public static void removeItemsFromCart(String username) throws SQLException {
        int userId = getUserIdByUsername(username);
        String query = "DELETE FROM cart_items WHERE cart_id = (SELECT cart_id FROM cart WHERE user_id = ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }

    public static boolean cancelOrder(String confirmationNumber) throws SQLException {
        String query = "UPDATE orders SET status = 'orderCanceled' WHERE confirmation_number = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, confirmationNumber);
            int rowsUpdated = stmt.executeUpdate();

            return rowsUpdated > 0;
        }
    }

    public static JsonObject getProductReviewData(String productId, String username, String confirmationNumber) throws SQLException {
    String query = "SELECT p.product_name, p.category, p.price AS price, p.manufacturer, " +
                   "o.delivery_option, s.store_id, s.zip_code, s.city, s.state " +
                   "FROM products p " +
                   "JOIN order_items oi ON p.product_id = oi.product_id " +
                   "JOIN orders o ON oi.order_id = o.order_id " +
                   "LEFT JOIN in_store_pickup s ON o.store_pickup_id = s.store_pickup_id " +
                   "JOIN users u ON o.user_id = u.user_id " +
                   "WHERE p.product_id = ? AND u.username = ? AND o.confirmation_number = ?";

    try (Connection conn = getConnection();
         PreparedStatement stmt = conn.prepareStatement(query)) {

        stmt.setString(1, productId);
        stmt.setString(2, username);
        stmt.setString(3, confirmationNumber);

        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            JsonObject jsonResponse = new JsonObject();
            jsonResponse.addProperty("productName", rs.getString("product_name"));
            jsonResponse.addProperty("productCategory", rs.getString("category"));
            jsonResponse.addProperty("productPrice", rs.getDouble("price"));
            jsonResponse.addProperty("manufacturer", rs.getString("manufacturer"));

            if ("storePickup".equalsIgnoreCase(rs.getString("delivery_option"))) {
                jsonResponse.addProperty("storeId", rs.getString("store_id"));
                jsonResponse.addProperty("storeZip", rs.getString("zip_code"));
                jsonResponse.addProperty("storeCity", rs.getString("city"));
                jsonResponse.addProperty("storeState", rs.getString("state"));
            }

            return jsonResponse;
        }
    }
    return null;
}

public static JsonArray getAllOrders() throws SQLException {
        JsonArray jsonOrders = new JsonArray();
        String query = "SELECT u.username, o.confirmation_number, o.delivery_date, o.order_placed_date, o.status, o.delivery_option " +
                       "FROM orders o JOIN users u ON o.user_id = u.user_id";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                JsonObject jsonOrder = new JsonObject();
                jsonOrder.addProperty("username", rs.getString("username"));
                jsonOrder.addProperty("confirmationNumber", rs.getString("confirmation_number"));
                jsonOrder.addProperty("deliveryDate", rs.getString("delivery_date"));
                jsonOrder.addProperty("orderPlacedDate", rs.getString("order_placed_date"));
                jsonOrder.addProperty("status", rs.getString("status"));
                jsonOrder.addProperty("deliveryOption", rs.getString("delivery_option"));
                jsonOrders.add(jsonOrder);
            }
        }

        return jsonOrders;
    }

    public static JsonObject getOrderDetailsByConfirmationNumber(String confirmationNumber) throws SQLException {
        JsonObject jsonOrderDetails = new JsonObject();

        String query = "SELECT u.username, o.confirmation_number, o.delivery_date, o.order_placed_date, o.status, o.delivery_option, oi.items " +
                       "FROM orders o JOIN users u ON o.user_id = u.user_id " +
                       "JOIN order_items oi ON o.order_id = oi.order_id " +
                       "WHERE o.confirmation_number = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, confirmationNumber);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                jsonOrderDetails.addProperty("username", rs.getString("username"));
                jsonOrderDetails.addProperty("confirmationNumber", rs.getString("confirmation_number"));
                jsonOrderDetails.addProperty("deliveryDate", rs.getString("delivery_date"));
                jsonOrderDetails.addProperty("orderPlacedDate", rs.getString("order_placed_date"));
                jsonOrderDetails.addProperty("status", rs.getString("status"));
                jsonOrderDetails.addProperty("deliveryOption", rs.getString("delivery_option"));
                jsonOrderDetails.addProperty("items", rs.getString("items"));
            }
        }

        return jsonOrderDetails;
    }

    public static Product getProductByName(String productName) {
        String query = "SELECT * FROM products WHERE product_name = ?";
        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, productName);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new Product(
                    rs.getInt("product_id"),
                    rs.getString("product_name"),
                    rs.getDouble("price"), 
                    rs.getString("description"),  
                    rs.getString("manufacturer"),
                    rs.getString("imageurl"),
                    rs.getString("category"),  
                    rs.getInt("product_quantity"), 
                    rs.getBoolean("on_sale"),
                    rs.getBoolean("manufacturer_rebate")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Product getProductByNameWithConnection(Connection conn, String productName) throws SQLException {
        String query = "SELECT * FROM products WHERE product_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, productName);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new Product(
                    rs.getInt("product_id"),
                    rs.getString("product_name"),
                    rs.getDouble("price"), 
                    rs.getString("description"), 
                    rs.getString("manufacturer"),
                    rs.getString("imageurl"),
                    rs.getString("category"),
                    rs.getInt("product_quantity"), 
                    rs.getBoolean("on_sale"),
                    rs.getBoolean("manufacturer_rebate")
                );
            }
        }
        return null;
    }


    public static void addOrder(String username, String confirmationNumber, String productName, double price, double discount, String deliveryOption) {
        String query = "INSERT INTO orders (username, confirmation_number, product_name, price, discount, delivery_option) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            stmt.setString(2, confirmationNumber);
            stmt.setString(3, productName);
            stmt.setDouble(4, price);
            stmt.setDouble(5, discount);
            stmt.setString(6, deliveryOption);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void updateOrder(JsonObject jsonObject) throws SQLException {
        String query = "UPDATE orders SET delivery_date = ?, delivery_option = ?, status = ?, order_placed_date = ? WHERE confirmation_number = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, jsonObject.get("deliveryDate").getAsString());
            stmt.setString(2, jsonObject.get("deliveryOption").getAsString());
            stmt.setString(3, jsonObject.get("status").getAsString());
            stmt.setString(4, jsonObject.get("orderPlacedDate").getAsString());
            stmt.setString(5, jsonObject.get("confirmationNumber").getAsString());

            stmt.executeUpdate();
        }
    }

    public static boolean deleteOrder(String confirmationNumber) throws SQLException {
        String query = "DELETE FROM orders WHERE confirmation_number = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, confirmationNumber);
            int rowsDeleted = stmt.executeUpdate();
            return rowsDeleted > 0;
        }
    }

    public static boolean addCustomer(String username, String password) throws SQLException {
        String insertCustomerQuery = "INSERT INTO users (username, password, user_type) VALUES (?, ?, 'Customer')";

        try (Connection conn = getConnection(); 
             PreparedStatement ps = conn.prepareStatement(insertCustomerQuery)) {

            ps.setString(1, username);
            ps.setString(2, password);

            int rowsInserted = ps.executeUpdate();
            return rowsInserted > 0;
        }
    }

    public static List<Product> getAllProducts(Connection conn) throws SQLException {
        String query = "SELECT * FROM products";
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            List<Product> products = new ArrayList<>();
            while (rs.next()) {

                List<Accessory> accessories = new ArrayList<>();

                Product product = new Product(
                        rs.getInt("product_id"),
                        rs.getString("product_name"),
                        rs.getDouble("price"),
                        rs.getString("description"),
                        rs.getString("manufacturer"),
                        rs.getString("imageurl"),
                        rs.getString("category"),
                        rs.getInt("product_quantity"),
                        rs.getBoolean("on_sale"),
                        rs.getBoolean("manufacturer_rebate"),
                        accessories
                );
                products.add(product);
            }
            return products;
        }
    }

    public static List<Product> getProductsByCategory(Connection conn, String category) throws SQLException {
        String query = "SELECT * FROM products WHERE category = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, category);
            ResultSet rs = stmt.executeQuery();

            List<Product> products = new ArrayList<>();
            while (rs.next()) {
                List<Accessory> accessories = new ArrayList<>();

                Product product = new Product(
                        rs.getInt("product_id"),
                        rs.getString("product_name"),
                        rs.getDouble("price"),
                        rs.getString("description"),
                        rs.getString("manufacturer"),
                        rs.getString("imageurl"),
                        rs.getString("category"),
                        rs.getInt("product_quantity"),
                        rs.getBoolean("on_sale"),
                        rs.getBoolean("manufacturer_rebate"),
                        accessories
                );
                products.add(product);
            }
            return products;
        }
    }

    public static int getProductQuantity(int productId) throws SQLException {
      String query = "SELECT product_quantity FROM products WHERE product_id = ?";
      try (Connection conn = getConnection();
          PreparedStatement stmt = conn.prepareStatement(query)) {

          stmt.setInt(1, productId);
          ResultSet rs = stmt.executeQuery();

          if (rs.next()) {
              return rs.getInt("product_quantity");
          }
      }
      return 0;
    }

    public static List<Product> getProductByIdAndCategory(Connection conn, int id, String category) throws SQLException {
        String query = "SELECT * FROM products WHERE product_id = ? AND category = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            stmt.setString(2, category);
            ResultSet rs = stmt.executeQuery();

            List<Product> products = new ArrayList<>();
            if (rs.next()) {
                List<Accessory> accessories = new ArrayList<>();

                Product product = new Product(
                        rs.getInt("product_id"),
                        rs.getString("product_name"),
                        rs.getDouble("price"),
                        rs.getString("description"),
                        rs.getString("manufacturer"),
                        rs.getString("imageurl"),
                        rs.getString("category"),
                        rs.getInt("product_quantity"),
                        rs.getBoolean("on_sale"),
                        rs.getBoolean("manufacturer_rebate"),
                        accessories
                );
                products.add(product);
            }
            return products;
        }
    }

    public boolean addProduct(String name, double price, String description, String manufacturer, String imageUrl, String category, JsonArray aidsArray) throws SQLException {
        String insertProductQuery = "INSERT INTO Products (product_name, price, description, manufacturer, imageurl, category) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertProductQuery, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, name);
            stmt.setDouble(2, price);
            stmt.setString(3, description);
            stmt.setString(4, manufacturer);
            stmt.setString(5, imageUrl);
            stmt.setString(6, category);
            stmt.executeUpdate();

            ResultSet generatedKeys = stmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                int productId = generatedKeys.getInt(1);

                if (aidsArray != null && aidsArray.size() > 0) {
                    addProductAccessories(productId, aidsArray, conn);
                }
            }

            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateProduct(String id, String name, double price, String description, String manufacturer, String imageUrl, String category, int productQuantity, boolean onSale, boolean manufacturerRebate) throws SQLException {
        String updateProductQuery = "UPDATE Products SET product_name = ?, price = ?, description = ?, manufacturer = ?, imageurl = ?, category = ?, product_quantity = ?, on_sale = ?, manufacturer_rebate = ? WHERE product_id = ?";
        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(updateProductQuery)) {

            stmt.setString(1, name);
            stmt.setDouble(2, price);
            stmt.setString(3, description);
            stmt.setString(4, manufacturer);
            stmt.setString(5, imageUrl);
            stmt.setString(6, category);
            stmt.setInt(7, productQuantity);
            stmt.setBoolean(8, onSale);
            stmt.setBoolean(9, manufacturerRebate);
            stmt.setInt(10, Integer.parseInt(id));

            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updateProductQuantity(String productId, int quantityOrdered) throws SQLException {
      String updateProductQuantityQuery = "UPDATE Products SET product_quantity = product_quantity - ? WHERE product_id = ?";
      try (Connection conn = getConnection();
          PreparedStatement stmt = conn.prepareStatement(updateProductQuantityQuery)) {
          stmt.setInt(1, quantityOrdered);
          stmt.setString(2, productId);
          
          int rowsUpdated = stmt.executeUpdate();
          return rowsUpdated > 0; 
      } catch (SQLException e) {
          e.printStackTrace();
          return false;
      }
    }


    public boolean deleteProduct(String id) throws SQLException {
        String deleteProductQuery = "DELETE FROM Products WHERE product_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(deleteProductQuery)) {

            stmt.setInt(1, Integer.parseInt(id));
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean addProductWithId(String id, String name, double price, String description, String manufacturer, String imageUrl, String category, JsonArray aidsArray, int productQuantity, boolean onSale, boolean manufacturerRebate) throws SQLException {
      String insertProductQuery = "INSERT INTO Products (product_id, product_name, price, description, manufacturer, imageurl, category, product_quantity, on_sale, manufacturer_rebate) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
      try (Connection conn = getConnection();
          PreparedStatement stmt = conn.prepareStatement(insertProductQuery)) {

          stmt.setInt(1, Integer.parseInt(id));
          stmt.setString(2, name);
          stmt.setDouble(3, price);
          stmt.setString(4, description);
          stmt.setString(5, manufacturer);
          stmt.setString(6, imageUrl);
          stmt.setString(7, category);
          stmt.setInt(8, productQuantity);
          stmt.setBoolean(9, onSale);
          stmt.setBoolean(10, manufacturerRebate);
          stmt.executeUpdate();

          if (aidsArray != null && aidsArray.size() > 0) {
              addProductAccessories(Integer.parseInt(id), aidsArray, conn);
          }

          return true;
      } catch (SQLException e) {
          e.printStackTrace();
          return false;
      }
    }


    private void addProductAccessories(int productId, JsonArray aidsArray, Connection conn) throws SQLException {
        String insertAccessoryQuery = "INSERT INTO ProductAccessories (product_id, accessory_id) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insertAccessoryQuery)) {
            for (int i = 0; i < aidsArray.size(); i++) {
                String accessoryId = aidsArray.get(i).getAsString();
                stmt.setInt(1, productId);
                stmt.setInt(2, Integer.parseInt(accessoryId));
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public static List<ProductInventory> getAllProductsInventory() throws SQLException {
        List<ProductInventory> inventoryList = new ArrayList<>();
        String query = "SELECT product_name, price, product_quantity FROM products";

        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(query)) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString("product_name");
                double price = rs.getDouble("price");
                int totalQuantity = rs.getInt("product_quantity");
                inventoryList.add(new ProductInventory(name, price, totalQuantity));
            }
        }
        return inventoryList;
    }

    public static List<ProductInventory> getProductsOnSale() throws SQLException {
        List<ProductInventory> saleList = new ArrayList<>();
        String query = "SELECT product_name, price FROM products WHERE on_sale = TRUE";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString("product_name");
                double price = rs.getDouble("price");
                saleList.add(new ProductInventory(name, price, 0)); 
            }
        }
        return saleList;
    }

    public static List<ProductInventory> getProductsWithManufacturerRebates() throws SQLException {
        List<ProductInventory> rebateList = new ArrayList<>();
        String query = "SELECT product_name, price FROM products WHERE manufacturer_rebate = TRUE";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString("product_name");
                double price = rs.getDouble("price");
                rebateList.add(new ProductInventory(name, price, 0)); 
            }
        }
        return rebateList;
    }

    public static List<SalesData> getSalesDataPerProduct(Connection conn) throws SQLException {
      String query = "SELECT p.product_name, p.price, SUM(oi.quantity) AS items_sold, SUM(oi.quantity * p.price) AS total_sales " +
                    "FROM products p " +
                    "JOIN order_items oi ON p.product_id = oi.product_id " +
                    "GROUP BY p.product_name, p.price";

      List<SalesData> salesDataList = new ArrayList<>();
      try (PreparedStatement stmt = conn.prepareStatement(query);
          ResultSet rs = stmt.executeQuery()) {

          while (rs.next()) {
              String productName = rs.getString("product_name");
              double productPrice = rs.getDouble("price");
              int itemsSold = rs.getInt("items_sold");
              double totalSales = rs.getDouble("total_sales");

              salesDataList.add(new SalesData(productName, productPrice, itemsSold, totalSales));
          }
      }
      return salesDataList;
    }

    public static List<DailySalesData> getDailySalesData(Connection conn) throws SQLException {
      String query = "SELECT DATE(o.order_placed_date) AS order_date, SUM(oi.quantity * p.price) AS total_sales " +
                    "FROM orders o " +
                    "JOIN order_items oi ON o.order_id = oi.order_id " +
                    "JOIN products p ON oi.product_id = p.product_id " +
                    "GROUP BY order_date";

      List<DailySalesData> dailySalesDataList = new ArrayList<>();
      try (PreparedStatement stmt = conn.prepareStatement(query);
          ResultSet rs = stmt.executeQuery()) {

          while (rs.next()) {
              String date = rs.getString("order_date");
              double totalSales = rs.getDouble("total_sales");

              dailySalesDataList.add(new DailySalesData(date, totalSales));
          }
      }
      return dailySalesDataList;
    }

    public boolean storeTicketInDatabase(int userId, String ticketNumber, String description, String imageUrl, String status, String confirmationNumber) {
      System.out.println("status inside MySQLDataStoreUtilities:" + status);
      System.out.println("confirmationNumber inside MySQLDataStoreUtilities:" + confirmationNumber);
        String insertTicketQuery = "INSERT INTO customer_service_tickets (user_id, ticket_number, description, image_url, status, confirmation_number, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(insertTicketQuery)) {

            stmt.setInt(1, userId);
            stmt.setString(2, ticketNumber);
            stmt.setString(3, description);
            stmt.setString(4, imageUrl);
            stmt.setString(5, status);
            stmt.setString(6, confirmationNumber);
            stmt.setTimestamp(7, new Timestamp(System.currentTimeMillis())); 
            stmt.setTimestamp(8, new Timestamp(System.currentTimeMillis())); 

            stmt.executeUpdate();

            return true; 
        } catch (SQLException e) {
            e.printStackTrace();
            return false; 
        }
    }

  public String getTicketStatus(String ticketNumber) {
    String query = "SELECT cst.status, p.price AS amount " +
                   "FROM customer_service_tickets cst " +
                   "JOIN orders o ON cst.confirmation_number = o.confirmation_number " +
                   "JOIN order_items oi ON o.order_id = oi.order_id " +
                   "JOIN products p ON oi.product_id = p.product_id " +
                   "WHERE cst.ticket_number = ?";

    try (Connection conn = getConnection();
         PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.setString(1, ticketNumber);
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            String status = rs.getString("status");
            double amount = rs.getDouble("amount");

            if ("Refund".equalsIgnoreCase(status)) {
                return "{\"status\":\"" + status + "\", \"amount\":" + amount + "}";
            } else {
                return "{\"status\":\"" + status + "\"}";
            }
        } else {
            return "{\"error\":\"Ticket not found\"}";
        }
    } catch (SQLException e) {
        e.printStackTrace();
        return "{\"error\":\"Database error\"}";
    }
}

}
