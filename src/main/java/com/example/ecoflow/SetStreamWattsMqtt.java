package com.example.ecoflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sets the persistent output power limit on an EcoFlow Stream Microinverter
 * by publishing the command directly over MQTT.
 */
public class SetStreamWattsMqtt {

    private static final String API_HOST = System.getenv()
            .getOrDefault("ECOFLOW_API_HOST", "https://api-e.ecoflow.com");
    private static final String CERT_PATH = "/iot-open/sign/certification";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    public static void main(String[] args) throws Exception {
        
        // --- 1. CONFIGURATION ---
        int watts = 800; // Target limit in raw Watts
        String accessKey = "xxx";
        String secretKey = "xxx";
        String deviceSn  = "xxx";

        System.out.println("Preparing to set limit to " + watts + " W on " + deviceSn + " via MQTT...");

        // --- 2. FETCH MQTT CREDENTIALS ---
        JsonNode certResponse = fetchCertification(accessKey, secretKey);
        JsonNode data = certResponse.path("data");
        String certificateAccount = data.path("certificateAccount").asText();
        String mqttPassword = data.path("certificatePassword").asText();
        String mqttHost = data.path("url").asText();
        String mqttPort = data.path("port").asText();
        String protocol = data.path("protocol").asText("mqtts");

        String scheme = "mqtts".equalsIgnoreCase(protocol) ? "ssl" : "tcp";
        String brokerUri = scheme + "://" + mqttHost + ":" + mqttPort;
        String clientId = "JAVA_SET_" + UUID.randomUUID();

        // --- 3. CONSTRUCT THE JSON PAYLOAD ---
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sn", deviceSn);
        body.put("params", Map.of(
                "feedGridModePowLimit", watts,
                "feedGridModePowMax", watts
        ));
        
        String jsonPayload = MAPPER.writeValueAsString(body);

        // --- 4. CONNECT & PUBLISH ---
        MqttClient client = new MqttClient(brokerUri, clientId, new MemoryPersistence());
        
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setUserName(certificateAccount);
        opts.setPassword(mqttPassword.toCharArray());
        opts.setCleanSession(true);
        opts.setConnectionTimeout(15);

        try {
            System.out.println("Connecting to MQTT broker at " + brokerUri + "...");
            client.connect(opts);
            System.out.println("Connected!");

            String setTopic = "/open/" + certificateAccount + "/" + deviceSn + "/set";
            
            MqttMessage message = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1); // QoS 1 ensures delivery

            System.out.println("Publishing to topic: " + setTopic);
            System.out.println("Payload: " + jsonPayload);
            
            client.publish(setTopic, message);
            System.out.println("Command successfully published.");

            // Brief pause to ensure network flush before disconnecting
            Thread.sleep(500);
            
        } catch (Exception e) {
            System.err.println("Failed to send MQTT command: " + e.getMessage());
        } finally {
            if (client.isConnected()) {
                client.disconnect();
                System.out.println("Disconnected from broker.");
            }
            client.close();
        }
    }

    /**
     * Reuses the certification fetch logic to grab your temporary MQTT username/password.
     */
    private static JsonNode fetchCertification(String accessKey, String secretKey) throws Exception {
        String nonce = String.valueOf(100000 + RANDOM.nextInt(900000));
        String timestamp = String.valueOf(System.currentTimeMillis());
        String stringToSign = "accessKey=" + accessKey + "&nonce=" + nonce + "&timestamp=" + timestamp;
        String sign = hmacSha256Hex(stringToSign, secretKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_HOST + CERT_PATH))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("accessKey", accessKey)
                .header("nonce", nonce)
                .header("timestamp", timestamp)
                .header("sign", sign)
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode root = MAPPER.readTree(response.body());
        if (!"0".equals(root.path("code").asText())) {
            throw new IllegalStateException("Certification failed: " + response.body());
        }
        return root;
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