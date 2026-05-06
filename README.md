# EcoFlow Stream Microinverter Power Limiter

A Java utility to set the persistent output power limit (custom load) on EcoFlow Stream Microinverters via the official EcoFlow IoT MQTT broker.

## ⚠️ The Problem: PowerStream vs. Stream Architecture

If you have tried using the official EcoFlow IoT Open API to set the output wattage on a newer Stream Microinverter (e.g., SN starting with BK01Z), you likely encountered a `1006 invalid parameter` error when using the documented `WN511_SET_PERMANENT_WATTS_PACK` REST command.

Unlike the older PowerStream devices (which expect `cmdCode` wrappers and deci-watt units), the newer Stream Microinverters expect:

- Raw Watts instead of deci-watts (e.g., `600` instead of `6000`).
- Flat camelCase properties published directly to the device's MQTT `/set` topic, specifically `feedGridModePowLimit` and `feedGridModePowMax`.

This project bypasses the incompatible REST endpoints by fetching temporary MQTT credentials via the `/certification` endpoint and publishing the correct flat JSON payload directly to the device.

## ✨ Features

- **Direct MQTT Publishing**: Fast, reliable command execution bypassing HTTP load balancers.
- **Native Payload Structure**: Uses the exact `feedGridModePowLimit` parameters discovered by eavesdropping on the official mobile app's traffic.
- **Automatic Auth Handling**: Generates the required HmacSHA256 signature to fetch secure MQTT certificates dynamically.

## 🛠️ Prerequisites

- Java 11 or higher (Uses the built-in `java.net.http.HttpClient`)
- EcoFlow Developer Account: You must have an AccessKey and SecretKey from the [EcoFlow Developer Portal](https://developer.ecoflow.com).
- Maven/Gradle Dependencies:
  - Jackson (`com.fasterxml.jackson.core:jackson-databind`) for JSON processing.
  - Eclipse Paho (`org.eclipse.paho:org.eclipse.paho.client.mqttv3`) for MQTT communication.

## 🚀 Configuration

Open `SetStreamWattsMqtt.java` and update the configuration block with your specific details:

```java
// --- 1. CONFIGURATION ---
int watts = 600; // Target limit in raw Watts (e.g., 0 to 800)
String accessKey = "YOUR_ACCESS_KEY";
String secretKey = "YOUR_SECRET_KEY";
String deviceSn  = "YOUR_DEVICE_SN";
```

> **Note**: The script currently defaults to the European broker (`https://api-e.ecoflow.com`). If your account is registered in the US, change the `ECOFLOW_API_HOST` environment variable or hardcode it to `https://api.ecoflow.com`.

## 💻 Usage

Compile and run the Java file. The program will output its progress to the console:

```plaintext
Preparing to set limit to 600 W on BK01Z... via MQTT...
Connecting to MQTT broker at ssl://mqtt-e.ecoflow.com:8883...
Connected!
Publishing to topic: /open/open-123456789/.../set
Payload: {"sn":"...","params":{"feedGridModePowLimit":600,"feedGridModePowMax":600}}
Command successfully published.
Disconnected from broker.
```

## 🔍 How It Works (Under the Hood)

1. **Certification**: The script signs your credentials using HmacSHA256 and calls `GET /iot-open/sign/certification` to retrieve a temporary MQTT `certificateAccount` (username) and `certificatePassword`.
2. **MQTT Connection**: It connects to `mqtts://mqtt-e.ecoflow.com:8883` using Eclipse Paho.
3. **Payload Construction**: It builds a JSON object without a `cmdCode`.
4. **Publishing**: It publishes the JSON to `/open/{certificateAccount}/{deviceSn}/set` at QoS 1 to ensure delivery.

## 🤝 Contributing

If you discover additional flat parameters for the Stream Microinverters (e.g., for toggling priority modes or reading specific telemetry), feel free to open a PR or submit an issue!

## 📜 License

This project is open-source and available under the MIT License.

**Disclaimer**: This project is not affiliated with, endorsed by, or maintained by EcoFlow. Use it at your own risk. Incorrectly configuring power equipment can cause damage or void warranties.
