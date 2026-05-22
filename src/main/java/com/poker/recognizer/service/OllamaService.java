package com.poker.recognizer.service;

import com.poker.recognizer.model.CardDetectionResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OllamaService {

    private static final Logger log = Logger.getLogger(OllamaService.class.getName());
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "minicpm-v";

    private final HttpClient client;

    public OllamaService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String detectCards(byte[] imageBytes) throws Exception {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);

        String prompt = "Identify all playing cards in this image. "
                + "Output ONLY a JSON object with \"board\" and \"hand\" arrays. "
                + "Use short codes: rank+suit where suit is s/h/d/c. "
                + "Example: {\"board\":[\"8s\",\"8h\",\"Td\",\"Jd\",\"3s\"],"
                + "\"hand\":[\"8c\",\"3c\"]}. "
                + "Ranks: A,K,Q,J,T,9,8,7,6,5,4,3,2. Nothing else.";

        String body = "{\"model\":\"" + MODEL + "\","
                + "\"prompt\":\"" + escapeJson(prompt) + "\","
                + "\"images\":[\"" + base64 + "\"],"
                + "\"stream\":false}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Ollama error " + response.statusCode() + ": " + response.body());
        }

        String json = extractResponse(response.body());
        log.info("Ollama: " + json);
        return json;
    }

    public static List<CardDetectionResult> parseCards(String json) {
        List<CardDetectionResult> results = new ArrayList<>();
        Pattern p = Pattern.compile("\"([AsKhQdJcTt2-9])([shdc])\"", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        int i = 0;
        while (m.find()) {
            String rank = m.group(1).toUpperCase().equals("T") ? "10" : m.group(1).toUpperCase();
            String suit;
            switch (m.group(2).toLowerCase()) {
                case "s": suit = "SPADES"; break;
                case "h": suit = "HEARTS"; break;
                case "d": suit = "DIAMONDS"; break;
                case "c": suit = "CLUBS"; break;
                default: suit = null; break;
            }
            if (suit == null) continue;
            CardDetectionResult r = new CardDetectionResult();
            r.setCardIndex(i++);
            r.setRank(rank);
            r.setSuit(suit);
            r.setLabel(rank + " of " + suit);
            results.add(r);
        }
        return results;
    }

    private String extractResponse(String ollamaResponse) {
        int s = ollamaResponse.indexOf("\"response\"");
        if (s < 0) return ollamaResponse;
        s = ollamaResponse.indexOf("\"", s + 11) + 1;
        int e = ollamaResponse.indexOf("\"", s);
        return (s < 1 || e < s) ? ollamaResponse
                : ollamaResponse.substring(s, e)
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("```json", "")
                        .replace("```", "")
                        .trim();
    }

    private static String escapeJson(String v) {
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
