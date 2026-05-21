package com.poker.recognizer.model;

import lombok.Data;

@Data
public class CardDetectionResult {
    private int cardIndex;
    private int x;
    private int y;
    private int width;
    private int height;
    private double areaRatio;
    private double aspectRatio;
    private String suitColor;
    private String rank;
    private String suit;
    private String label;
}
