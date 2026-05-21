package com.poker.recognizer.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CardTemplate {
    private String rank;
    private String suit;
    private String label;
    private byte[] imageBytes;
}
