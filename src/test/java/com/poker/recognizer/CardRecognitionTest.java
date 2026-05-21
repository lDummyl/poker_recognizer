package com.poker.recognizer;

import com.poker.recognizer.model.CardDetectionResult;
import com.poker.recognizer.service.CardRecognitionService;
import com.poker.recognizer.service.CardTemplateGenerator;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CardRecognitionTest {

    private final CardRecognitionService service =
            new CardRecognitionService(new CardTemplateGenerator());

    @Test
    public void testCards2TurnAndHand() throws IOException {
        byte[] imageBytes = Files.readAllBytes(Paths.get("cards2.jpg"));
        List<CardDetectionResult> results = service.detectCards(imageBytes);

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║         CARDS2 — TURN + HAND RECOGNITION           ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.printf("║ Total cards detected: %-28d ║%n", results.size());
        System.out.println("╠══════╤════════╤═══════╤════════╤══════════╤══════════╣");
        System.out.println("║ Card │  X,  Y │  WxH  │ Aspect │ Color    │ Label    ║");
        System.out.println("╠══════╪════════╪═══════╪════════╪══════════╪══════════╣");

        for (var card : results) {
            System.out.printf("║ %4d │ %2d,%3d │ %3dx%3d │ %5.2f  │ %-8s │ %-8s ║%n",
                    card.getCardIndex(),
                    card.getX(), card.getY(),
                    card.getWidth(), card.getHeight(),
                    card.getAspectRatio(),
                    card.getSuitColor(),
                    card.getLabel());
        }

        System.out.println("╚══════╧════════╧═══════╧════════╧══════════╧══════════╝");

        long recognized = results.stream().filter(c -> !"UNKNOWN".equals(c.getLabel())).count();
        Set<String> labels = results.stream()
                .map(CardDetectionResult::getLabel)
                .filter(l -> !"UNKNOWN".equals(l))
                .collect(Collectors.toSet());

        System.out.println("Expected: turn (3♥, A♥, 7♣, A♣, 9♠) + hand (A♦, A♠)");
        System.out.println("Template-matched: " + recognized + " / " + results.size());
        if (!labels.isEmpty()) {
            System.out.println("Recognized: " + labels);
        }

        assertTrue("Should detect at least 5 card regions (turn 5 + hand 2)",
                results.size() >= 5);

        Set<String> expectedTurn = Set.of(
                "3 of HEARTS", "A of HEARTS", "7 of CLUBS", "A of CLUBS", "9 of SPADES");
        Set<String> expectedHand = Set.of("A of DIAMONDS", "A of SPADES");

        for (String expected : expectedTurn) {
            assertTrue("Turn card not found: " + expected, labels.contains(expected));
        }
        for (String expected : expectedHand) {
            assertTrue("Hand card not found: " + expected, labels.contains(expected));
        }

        if (recognized < 5) {
            System.out.println("NOTE: Low template match count. Image may need higher resolution.");
        }
    }
}
