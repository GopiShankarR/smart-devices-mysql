package com.example;

import java.util.Date;

public class Order {
    private int orderId;
    private String confirmationNumber;
    private String productName;
    private Date orderDate;

    public Order(int orderId, String confirmationNumber, String productName, Date orderDate) {
        this.orderId = orderId;
        this.confirmationNumber = confirmationNumber;
        this.productName = productName;
        this.orderDate = orderDate;
    }

    // Getters and setters (if needed)
    public int getOrderId() {
        return orderId;
    }

    public String getConfirmationNumber() {
        return confirmationNumber;
    }

    public String getProductName() {
        return productName;
    }

    public Date getOrderDate() {
        return orderDate;
    }
}

