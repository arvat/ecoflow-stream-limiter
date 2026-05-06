package com.example.ecoflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;

public class ListDevices {

    private static final String API_HOST = System.getenv()
            .getOrDefault("ECOFLOW_API_HOST", "https://api-e.ecoflow.com");
    private static final String DEVICE_LIST_PATH = "/iot-open/sign/device/list";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) throws Exception {
        String accessKey = "xxx";
        String secretKey = "xxx";

        String nonce = String.valueOf(100000 + RANDOM.nextInt(900000));
        String timestamp = String.valueOf(System.currentTimeMillis());

        // No body, no query params - signed string is just the three auth fields.
        String stringToSign = "accessKey=" + accessKey
                + "&nonce=" + nonce
                + "&timestamp=" + timestamp;
        String sign = hmacSha256Hex(stringToSign, secretKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_HOST + DEVICE_LIST_PATH))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("accessKey", accessKey)
                .header("nonce", nonce)
                .header("timestamp", timestamp)
                .header("sign", sign)
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            System.err.println("HTTP " + response.statusCode() + ": " + response.body());
            System.exit(2);
        }

        JsonNode root = MAPPER.readTree(response.body());
        if (!"0".equals(root.path("code").asText())) {
            System.err.println("API error: " + root.path("message").asText() + " (code=" + root.path("code") + ")");
            System.exit(2);
        }

        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            System.out.println("No devices bound to this account.");
            return;
        }

        // Compact, table-like output. Columns are sized to typical EcoFlow values.
        System.out.printf("%-22s  %-25s  %-8s%n", "SERIAL NUMBER", "PRODUCT", "ONLINE");
        System.out.println("-".repeat(60));
        for (JsonNode device : data) {
            String sn = device.path("sn").asText("?");
            String product = device.path("productName").asText("?");
            // online may be 0/1 (number) or true/false depending on firmware - handle both.
            String online = device.path("online").asInt(0) == 1
                    || device.path("online").asBoolean(false) ? "yes" : "no";
            System.out.printf("%-22s  %-25s  %-8s%n", sn, product, online);
        }
    }

    private static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", e);
        }
    }
}
