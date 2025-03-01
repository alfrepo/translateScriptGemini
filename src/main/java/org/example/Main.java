package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class Main {

    public static void main(String[] args) {
        Properties settings = loadSettings("settings.properties");
        Properties apiKeys = loadSettings("api-keys.properties"); // Load API keys

        String inputFile = settings.getProperty("inputFile");
        String outputFile = settings.getProperty("outputFile");
        String apiKey = apiKeys.getProperty("apiKey"); // Get API key from api-keys.properties

        translateAndAppend(inputFile, outputFile, apiKey);
    }

    public static Properties loadSettings(String filename) {
        Properties properties = new Properties();
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream(filename)) {
            if (input == null) {
                System.err.println("Unable to find " + filename);
                return null;
            }
            properties.load(input);
            return properties;
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static void translateAndAppend(String inputFile, String outputFile, String apiKey) {
        String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey;
        HttpClient client = HttpClient.newHttpClient();
        Gson gson = new Gson();

        try {
            InputStream inputStream = Main.class.getClassLoader().getResourceAsStream(inputFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            BufferedWriter writer = createBufferedWriterInResources(outputFile);

            if (inputStream == null) {
                System.err.println("Error: File '" + inputFile + "' not found in resources.");
                return;
            }

            String[] chunk = new String[50];
            int linesRead;

            while ((linesRead = readChunk(reader, chunk)) > 0) {
                StringBuilder chunkBuilder = new StringBuilder();
                for (int i = 0; i < linesRead; i++) {
                    chunkBuilder.append(chunk[i]);
                }
                String nextChunk = chunkBuilder.toString();

                String jsonPayload = createJsonPayload(nextChunk);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                try {
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
                        String translatedText = jsonResponse.getAsJsonArray("candidates").get(0).getAsJsonObject()
                                .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                                .get("text").getAsString();

                        writer.write(translatedText + "\n");
                        System.out.println("Chunk translated and appended.");
                        System.out.println(translatedText);
                    } else {
                        writer.write("HTTP Error: " + response.statusCode() + "\n");
                        System.err.println("HTTP Error: " + response.statusCode());
                        break;
                    }
                } catch (Exception e) {
                    writer.write("API Error: " + e.getMessage() + "\n");
                    System.err.println("API Error: " + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("An error occurred: " + e.getMessage());
        }
    }

    private static BufferedWriter createBufferedWriterInResources(String filename) {
        try {
            Path resourcePath = Paths.get("src", "main", "resources", filename); // Path to resources
            Files.createDirectories(resourcePath.getParent()); // Ensure directory exists
            return new BufferedWriter(new FileWriter(resourcePath.toFile(), java.nio.charset.StandardCharsets.UTF_8, true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int readChunk(BufferedReader reader, String[] chunk) throws IOException {
        int linesRead = 0;
        String line;
        while (linesRead < chunk.length && (line = reader.readLine()) != null) {
            chunk[linesRead++] = line + "\n";
        }
        return linesRead;
    }

    private static String createJsonPayload(String text) {
        JsonObject payload = new JsonObject();
        JsonObject content = new JsonObject();
        JsonObject part = new JsonObject();
        part.addProperty("text", "Translate from Russian to German following text. Return only the translation without any comments :" + text);
        com.google.gson.JsonArray parts = new com.google.gson.JsonArray();
        parts.add(part);
        content.add("parts", parts);
        com.google.gson.JsonArray contents = new com.google.gson.JsonArray();
        contents.add(content);
        payload.add("contents", contents);
        return payload.toString();
    }
}