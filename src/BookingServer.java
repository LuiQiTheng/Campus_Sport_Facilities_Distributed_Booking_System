import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Main entry point for the Distributed Booking System.
 * 
 * Each instance runs as either a COORDINATOR or WORKER node.
 * - Coordinator (Node 1): Processes all booking writes, replicates to workers.
 * - Worker (Node 2): Forwards booking writes to coordinator, serves reads locally.
 *
 * Usage:
 *   Coordinator: java BookingServer --port 8081 --role coordinator --workers http://localhost:8082
 *   Worker:      java BookingServer --port 8082 --role worker --coordinator http://localhost:8081
 */
public class BookingServer {

    public static void main(String[] args) throws Exception {
        // Default configuration
        int port = 8081;
        String role = "coordinator";
        String coordinatorUrl = "";
        String workerUrls = "";

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port":
                    port = Integer.parseInt(args[++i]);
                    break;
                case "--role":
                    role = args[++i].toLowerCase();
                    break;
                case "--coordinator":
                    coordinatorUrl = args[++i];
                    break;
                case "--workers":
                    workerUrls = args[++i];
                    break;
            }
        }

        // Initialize the booking data manager (each node has its own JSON file)
        BookingManager manager = new BookingManager("bookings_node" + port + ".json");

        // Initialize inter-node communicator
        NodeCommunicator communicator = new NodeCommunicator(coordinatorUrl, workerUrls);

        // Create HTTP server with thread pool for handling concurrent requests
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(10));

        // Register API handlers
        BookingHandler handler = new BookingHandler(manager, communicator, role, port);
        server.createContext("/api/bookings", handler::handleGetBookings);
        server.createContext("/api/book", handler::handleBook);
        server.createContext("/api/replicate", handler::handleReplicate);
        server.createContext("/api/status", handler::handleStatus);

        // Register static file handler (serves web UI)
        server.createContext("/", new StaticFileHandler());

        // Start the server
        server.start();

        // Print startup banner
        System.out.println("================================================");
        System.out.println("  DISTRIBUTED BOOKING SYSTEM - NODE STARTED");
        System.out.println("================================================");
        System.out.println("  Port : " + port);
        System.out.println("  Role : " + role.toUpperCase());
        if ("worker".equals(role)) {
            System.out.println("  Coordinator : " + coordinatorUrl);
        } else {
            System.out.println("  Workers     : " + (workerUrls.isEmpty() ? "none" : workerUrls));
        }
        System.out.println("  Data File   : bookings_node" + port + ".json");
        System.out.println("  URL         : http://localhost:" + port);
        System.out.println("================================================");
        System.out.println("  Server is ready. Waiting for requests...");
        System.out.println("================================================");
    }
}
