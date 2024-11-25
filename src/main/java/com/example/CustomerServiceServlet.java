package com.example;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.io.InputStream;

import java.util.Properties;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@WebServlet(urlPatterns = "/customer-service", name = "CustomerServiceServlet")
public class CustomerServiceServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private String openAiApiKey;

    @Override
    public void init() {
    ServletContext context = getServletContext();
    Properties properties = new Properties();
    try (InputStream input = context.getResourceAsStream("/WEB-INF/config.properties")) {
        properties.load(input);
        openAiApiKey = properties.getProperty("OPEN_AI_API_KEY");
        System.out.println("OpenAI API Key loaded: " + openAiApiKey); // Check if key is loaded
    } catch (Exception e) {
        e.printStackTrace();
    }
}

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, DELETE");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        BufferedReader reader = request.getReader();
        JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();

        String username = jsonObject.get("username").getAsString();
        String description = jsonObject.get("description").getAsString();
        String imageUrl = jsonObject.get("imageUrl").getAsString();
        String confirmationNumber = jsonObject.get("confirmationNumber").getAsString();

        String ticketNumber = generateTicketNumber();

        String decision = getOpenAiDecision(description, imageUrl);
        System.out.println("Original decision text: " + decision);

        if (decision.toLowerCase().contains("1") || decision.toLowerCase().contains("refund order")) {
            decision = "Refund";
            System.out.println("Decision set to: Refund");
        } else if (decision.toLowerCase().contains("2") || decision.toLowerCase().contains("replace order")) {
            decision = "Replace";
            System.out.println("Decision set to: Replace");
        } else if (decision.toLowerCase().contains("3") || decision.toLowerCase().contains("escalate to human agent")) {
            decision = "Escalate";
            System.out.println("Decision set to: Escalate");
        } else {
            decision = "Escalate";
            System.out.println("Decision set to default: Escalate");
        }

        System.out.println("Final decision after processing: " + decision);

        try {
            int userId = MySQLDataStoreUtilities.getUserIdByUsername(username);
            System.out.println("username" + username + " userId " + userId);

            MySQLDataStoreUtilities dbUtilities = new MySQLDataStoreUtilities();
            boolean isStored = dbUtilities.storeTicketInDatabase(userId, ticketNumber, description, imageUrl, decision, confirmationNumber);


            if (isStored) {
                response.getWriter().write("{\"ticketNumber\":\"" + ticketNumber + "\"}");
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\":\"Failed to store ticket in the database.\"}");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("User not found")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"User not found: " + username + "\"}");
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\":\"Database error occurred.\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"An error occurred while processing the request.\"}");
        }
    }


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, DELETE");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String ticketNumber = request.getParameter("ticketNumber");

        MySQLDataStoreUtilities dbUtilities = new MySQLDataStoreUtilities(); 
        String statusResponse = dbUtilities.getTicketStatus(ticketNumber);

        response.getWriter().write(statusResponse);
    }


    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, DELETE");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.addHeader("Access-Control-Max-Age", "3600");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private String generateTicketNumber() {
        return "TICKET-" + System.currentTimeMillis();
    }

    private String getOpenAiDecision(String description, String imageUrl) {
        String apiUrl = "https://api.openai.com/v1/chat/completions";
        String model = "gpt-3.5-turbo";

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + openAiApiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            JsonObject payload = new JsonObject();
            payload.addProperty("model", model);
            
            JsonArray messages = new JsonArray();
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", "Decide on the following issue:\nDescription: " + description + "\nImage URL: " + imageUrl + "\nOptions:\n1. Refund Order\n2. Replace Order\n3. Escalate to Human Agent\nPlease provide your decision.");
            messages.add(message);

            payload.add("messages", messages);
            payload.addProperty("max_tokens", 50);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            System.out.println("Response Message: " + conn.getResponseMessage());

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
                String decision = jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject().get("message").getAsJsonObject().get("content").getAsString().trim();
                System.out.println("Extracted Decision: " + decision);

                return decision;
            } else {
                System.out.println("Error: Received non-OK response from OpenAI.");
                return "Escalate to Human Agent";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Escalate to Human Agent"; 
        }
    }
}
