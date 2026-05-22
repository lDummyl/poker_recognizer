package com.poker.recognizer;

import com.poker.recognizer.model.CardDetectionResult;
import com.poker.recognizer.service.OllamaService;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public class CardRecognitionTest {

    @Test
    public void testHighDefCardsOllama() throws Exception {
        List<CardDetectionResult> results;
        try {
            OllamaService ollama = new OllamaService();
            byte[] imageBytes = Files.readAllBytes(Paths.get("high-def-cards.png"));
            String json = ollama.detectCards(imageBytes);
            Files.writeString(Paths.get("ollama_mock", "high-def-cards.json"), json);
            results = OllamaService.parseCards(json);
            System.out.println("(Ollama API)");
        } catch (Exception e) {
            String json = Files.readString(Paths.get("ollama_mock", "high-def-cards.json"));
            results = OllamaService.parseCards(json);
            System.out.println("(mock — Ollama not available: " + e.getMessage() + ")");
        }

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║   HIGH-DEF via OLLAMA — 8♠8♥10♦J♦3♠|8♣3♣     ║");
        System.out.println("╠══════╤═══════════════════════════════════════════════╣");
        System.out.println("║ Card │ Label                                        ║");
        for (var card : results) {
            System.out.printf("║ %4d │ %-45s ║%n", card.getCardIndex(), card.getLabel());
        }
        System.out.println("╚══════╧═══════════════════════════════════════════════╝");

        Set<String> expected = Set.of(
                "8 of SPADES", "8 of HEARTS", "10 of DIAMONDS",
                "J of DIAMONDS", "3 of SPADES", "8 of CLUBS", "3 of CLUBS");
        Set<String> labels = results.stream()
                .map(CardDetectionResult::getLabel)
                .collect(Collectors.toSet());

        System.out.println("Expected: " + expected);
        System.out.println("Found:    " + labels);

        assertTrue("Should detect at least 5 cards", results.size() >= 5);
        for (String exp : expected) {
            assertTrue("Missing: " + exp, labels.contains(exp));
        }
    }
}
