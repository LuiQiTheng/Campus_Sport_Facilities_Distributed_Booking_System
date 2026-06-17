import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Handles inter-node communication in the distributed system.
 * - Workers forward booking requests to the Coordinator.
 * - Coordinator replicates updated data to Workers.
 */
public class NodeCommunicator {

    private final String coordinatorUrl;
    private final String[] workerUrls;

    public NodeCommunicator(String coordinatorUrl, String workerUrlsStr) {
        this.coordinatorUrl = coordinatorUrl;
        if (workerUrlsStr != null && !workerUrlsStr.isEmpty()) {
            this.workerUrls = workerUrlsStr.split(",");
        } else {
            this.workerUrls = new String[0];
        }
    }

    /**
     * Worker forwards a booking request to the Coordinator node.
     * The Coordinator will process it with synchronized locking.
     */
    public String forwardBookingToCoordinator(String court, String time, String userName) throws Exception {
        String jsonBody = "{\"court\": \"" + JsonHelper.escapeJson(court) +
                        "\", \"time\": \"" + JsonHelper.escapeJson(time) +
                        "\", \"userName\": \"" + JsonHelper.escapeJson(userName) + "\"}";

        System.out.println("[FORWARD] Sending booking to coordinator: " + coordinatorUrl);
        return sendPost(coordinatorUrl + "/api/book", jsonBody);
    }

    /**
     * Coordinator replicates the latest booking data to all Worker nodes.
     * Runs in a separate thread to avoid blocking the response.
     */
    public void replicateToWorkers(String bookingsJson) {
        for (String workerUrl : workerUrls) {
            final String url = workerUrl.trim();
            new Thread(() -> {
                try {
                    sendPost(url + "/api/replicate", bookingsJson);
                    System.out.println("[REPLICATE] Successfully sent data to " + url);
                } catch (Exception e) {
                    System.err.println("[REPLICATE ERROR] Failed to send to " + url + ": " + e.getMessage());
                }
            }).start();
        }
    }

    /**
     * Send an HTTP POST request with JSON body.
     */
    private String sendPost(String urlStr, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes("UTF-8"));
        }

        int responseCode = conn.getResponseCode();
        InputStream is = (responseCode >= 200 && responseCode < 300)
            ? conn.getInputStream()
            : conn.getErrorStream();

        StringBuilder response = new StringBuilder();
        if (is != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
        }

        conn.disconnect();
        return response.toString();
    }
}
