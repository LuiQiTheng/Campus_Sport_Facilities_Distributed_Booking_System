import com.sun.net.httpserver.HttpExchange;
import java.io.*;

/**
 * HTTP request handlers for all API endpoints.
 * Routes requests based on the node's role (Coordinator vs Worker).
 */
public class BookingHandler {

    private final BookingManager manager;
    private final NodeCommunicator communicator;
    private String role; // Non-final to support dynamic promotion during failover
    private final int port;

    public BookingHandler(BookingManager manager, NodeCommunicator communicator, String role, int port) {
        this.manager = manager;
        this.communicator = communicator;
        this.role = role;
        this.port = port;
    }

    public String getRole() {
        return role;
    }

    /**
     * GET /api/bookings — Return all current booking data as JSON.
     * Both Coordinator and Worker can serve this from their local copy.
     */
    public void handleGetBookings(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        sendResponse(exchange, 200, manager.getBookingsJson());
    }

    /**
     * POST /api/book — Make a booking request.
     * - Coordinator: processes directly with synchronized lock.
     * - Worker: forwards the request to the Coordinator.
     */
    public void handleBook(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            return;
        }

        String body = readBody(exchange);
        String court = JsonHelper.extractValue(body, "court");
        String time = JsonHelper.extractValue(body, "time");
        String userName = JsonHelper.extractValue(body, "userName");

        if (court == null || time == null || userName == null || userName.trim().isEmpty()) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            sendResponse(exchange, 400, "{\"success\": false, \"message\": \"Missing required fields (court, time, userName).\"}");
            return;
        }

        // Validate user ID format: exactly 6 digits, or BC + 6 digits
        String idPattern = "^(?:(?i)bc)?\\d{6}$";
        if (!userName.trim().matches(idPattern)) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            sendResponse(exchange, 400, "{\"success\": false, \"message\": \"Invalid ID format. Must be exactly 6 digits, optionally starting with BC (e.g. 123456 or BC123456).\"}");
            return;
        }

        String result;

        if ("coordinator".equals(role)) {
            // ===== COORDINATOR: Handle booking directly =====
            System.out.println("[REQUEST] Booking: " + court + " at " + time + " by " + userName);
            result = manager.bookSlot(court, time, userName);

            // If successful, replicate updated data to all worker nodes
            if (result.contains("\"success\": true")) {
                communicator.replicateToWorkers(manager.getBookingsJson());
            }
        } else {
            // ===== WORKER: Forward request to Coordinator =====
            System.out.println("[FORWARD] Forwarding booking to coordinator: " + court + " at " + time + " by " + userName);
            try {
                result = communicator.forwardBookingToCoordinator(court, time, userName);
            } catch (Exception e) {
                System.out.println("\n==================================================");
                System.out.println("[FAILOVER] Coordinator is unreachable: " + e.getMessage());
                System.out.println("[FAILOVER] Promoting Node on port " + port + " to COORDINATOR!");
                System.out.println("==================================================");
                
                // dynamically promote this node's role
                this.role = "coordinator";
                
                // Process the booking locally
                System.out.println("[REQUEST] Processing booking locally as new Coordinator: " + court + " at " + time + " by " + userName);
                result = manager.bookSlot(court, time, userName);
                
                // Attempt to replicate if we succeed and there are other nodes
                if (result.contains("\"success\": true")) {
                    communicator.replicateToWorkers(manager.getBookingsJson());
                }
            }
        }

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        int statusCode = result.contains("\"success\": true") ? 200 : 409;
        sendResponse(exchange, statusCode, result);
    }

    /**
     * POST /api/replicate — Accept replicated booking data from the Coordinator.
     * Only used by Worker nodes.
     */
    public void handleReplicate(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            return;
        }
        String body = readBody(exchange);
        manager.replaceBookings(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        sendResponse(exchange, 200, "{\"success\": true, \"message\": \"Data replicated successfully.\"}");
    }

    /**
     * GET /api/status — Return node status information.
     * Useful for the UI to display which node the user is connected to.
     */
    public void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            return;
        }
        String json = "{\"port\": " + port + ", \"role\": \"" + role + "\", \"status\": \"running\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        sendResponse(exchange, 200, json);
    }

    // --- Helper methods ---

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes("UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
