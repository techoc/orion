package cn.techoc.oriongateway.core.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GatewayTestClient {

    private static final String BASE_URL = "http://localhost:8080";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Gateway Test Client ===");
        System.out.println("Testing URI sanitization...\n");

        testUri("/test|path");
        testUri("/api/user{id}");
        testUri("/search?q=test|value&filter={category}");
        testUri("/normal/path/without/bad/chars");
        testUri("/test|multiple|chars|in|path");
        testUri("/[test]with|brackets|and^other|bad|chars");

        System.out.println("\n=== Test completed!");
    }

    private static void testUri(String uri) throws Exception {
        URL url = new URL(BASE_URL + uri);
        System.out.println("Testing: " + uri);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        int responseCode = connection.getResponseCode();
        System.out.println("Response Code: " + responseCode);

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        System.out.println("Response: " + response);
        System.out.println("---");
    }
}
