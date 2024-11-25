package com.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@WebServlet(urlPatterns = "/customer-orders", name = "CustomerOrdersServlet")
public class CustomerOrdersServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.addHeader("Access-Control-Allow-Origin", "*");
    response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, DELETE");
    response.addHeader("Access-Control-Allow-Headers", "Content-Type");
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    String username = request.getParameter("username");
    
    if (username == null || username.isEmpty()) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().write("{\"error\":\"Username parameter is missing\"}");
        return;
    }

    try {
        List<OrderInfo> orders = MySQLDataStoreUtilities.getOrdersWithProductsByUsername(username);
        JsonArray jsonOrders = new JsonArray();

        System.out.println("All orders for user: " + username);
        for (OrderInfo order : orders) {

            if ("delivered".equalsIgnoreCase(order.getStatus())) {
                for (ProductInfo product : order.getProducts()) {
                    JsonObject orderJson = new JsonObject();
                    orderJson.addProperty("confirmationNumber", order.getConfirmationNumber());
                    orderJson.addProperty("productName", product.getProductName());
                    orderJson.addProperty("orderDate", order.getOrderPlacedDate());
                    jsonOrders.add(orderJson);
                }
            }
        }

        response.getWriter().write(jsonOrders.toString());
    } catch (SQLException e) {
        e.printStackTrace();
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.getWriter().write("{\"error\":\"Database error occurred\"}");
    }
}

}
