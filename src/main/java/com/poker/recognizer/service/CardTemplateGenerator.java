package com.poker.recognizer.service;

import com.poker.recognizer.model.CardTemplate;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CardTemplateGenerator {

    private static final String[] RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
    private static final String[] SUITS = {"♠", "♥", "♦", "♣"};
    private static final String[] SUIT_NAMES = {"SPADES", "HEARTS", "DIAMONDS", "CLUBS"};

    public List<CardTemplate> generateAll() {
        List<CardTemplate> templates = new ArrayList<>();
        for (int s = 0; s < SUITS.length; s++) {
            Color color = (s == 1 || s == 2) ? Color.RED : Color.BLACK;
            for (String rank : RANKS) {
                byte[] imgBytes = renderCorner(rank, SUITS[s], color);
                templates.add(new CardTemplate(
                        rank,
                        SUIT_NAMES[s],
                        rank + " of " + SUIT_NAMES[s],
                        imgBytes));
            }
        }
        return templates;
    }

    private byte[] renderCorner(String rank, String suit, Color color) {
        int w = 50, h = 70;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(color);

        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString(rank, 3, 24);

        g.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 20));
        g.drawString(suit, 3, 48);

        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(1));
        g.drawRect(0, 0, w - 1, h - 1);

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", baos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }
}
