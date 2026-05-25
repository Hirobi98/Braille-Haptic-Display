package com.example.myapplication;

import java.util.HashMap;
import java.util.Map;

/**
 * Braille converter — standard Grade-1 Braille cell layout:
 *
 *   Col →   0    1
 *   Row 0 [dot1][dot4]   ← top
 *   Row 1 [dot2][dot5]   ← middle
 *   Row 2 [dot3][dot6]   ← bottom
 *
 * Each character maps to a 3×2 boolean matrix.
 *   true  = dot raised  (servo UP)
 *   false = dot flat    (servo DOWN)
 *
 * 6-bit encoding: bit0=dot1, bit1=dot2, bit2=dot3,
 *                 bit3=dot4, bit4=dot5, bit5=dot6
 */
public class BrailleConverter {

    private static final Map<Character, Integer> BRAILLE_MAP = new HashMap<>();

    static {
        // ── Letters ──────────────────────────────────────────────────────────
        BRAILLE_MAP.put('a', 0b000001);
        BRAILLE_MAP.put('b', 0b000011);
        BRAILLE_MAP.put('c', 0b001001);
        BRAILLE_MAP.put('d', 0b011001);
        BRAILLE_MAP.put('e', 0b010001);
        BRAILLE_MAP.put('f', 0b001011);
        BRAILLE_MAP.put('g', 0b011011);
        BRAILLE_MAP.put('h', 0b010011);
        BRAILLE_MAP.put('i', 0b001010);
        BRAILLE_MAP.put('j', 0b011010);
        BRAILLE_MAP.put('k', 0b000101);
        BRAILLE_MAP.put('l', 0b000111);
        BRAILLE_MAP.put('m', 0b001101);
        BRAILLE_MAP.put('n', 0b011101);
        BRAILLE_MAP.put('o', 0b010101);
        BRAILLE_MAP.put('p', 0b001111);
        BRAILLE_MAP.put('q', 0b011111);
        BRAILLE_MAP.put('r', 0b010111);
        BRAILLE_MAP.put('s', 0b001110);
        BRAILLE_MAP.put('t', 0b011110);
        BRAILLE_MAP.put('u', 0b100101);
        BRAILLE_MAP.put('v', 0b100111);
        BRAILLE_MAP.put('w', 0b111010);
        BRAILLE_MAP.put('x', 0b101101);
        BRAILLE_MAP.put('y', 0b111101);
        BRAILLE_MAP.put('z', 0b110101);

        // ── Digits ───────────────────────────────────────────────────────────
        BRAILLE_MAP.put('1', 0b000001);
        BRAILLE_MAP.put('2', 0b000011);
        BRAILLE_MAP.put('3', 0b001001);
        BRAILLE_MAP.put('4', 0b011001);
        BRAILLE_MAP.put('5', 0b010001);
        BRAILLE_MAP.put('6', 0b001011);
        BRAILLE_MAP.put('7', 0b011011);
        BRAILLE_MAP.put('8', 0b010011);
        BRAILLE_MAP.put('9', 0b001010);
        BRAILLE_MAP.put('0', 0b011010);

        // ── Space ────────────────────────────────────────────────────────────
        BRAILLE_MAP.put(' ', 0b000000);
    }

    /**
     * Returns a 3×2 boolean matrix for the given character.
     * Returns null if the character is not supported.
     *
     * matrix[row][col]:
     *   row 0, col 0 → dot1   row 0, col 1 → dot4
     *   row 1, col 0 → dot2   row 1, col 1 → dot5
     *   row 2, col 0 → dot3   row 2, col 1 → dot6
     */
    public static boolean[][] charToMatrix(char c) {
        char key = Character.toLowerCase(c);
        Integer bits = BRAILLE_MAP.get(key);
        if (bits == null) return null;

        boolean dot1 = ((bits >> 0) & 1) == 1;
        boolean dot2 = ((bits >> 1) & 1) == 1;
        boolean dot3 = ((bits >> 2) & 1) == 1;
        boolean dot4 = ((bits >> 3) & 1) == 1;
        boolean dot5 = ((bits >> 4) & 1) == 1;
        boolean dot6 = ((bits >> 5) & 1) == 1;

        return new boolean[][] {
                { dot1, dot4 },   // row 0 — top
                { dot2, dot5 },   // row 1 — middle
                { dot3, dot6 }    // row 2 — bottom
        };
    }

    /**
     * Returns a flat 6-element int array for sending over HTTP to ESP32.
     * Order: [dot1, dot2, dot3, dot4, dot5, dot6]
     * 1 = raised, 0 = flat
     *
     * Example JSON for ESP32:
     *   { "char": "a", "dots": [1,0,0,0,0,0] }
     */
    public static int[] charToDotsArray(char c) {
        boolean[][] matrix = charToMatrix(c);
        if (matrix == null) return null;

        return new int[] {
                matrix[0][0] ? 1 : 0,  // dot1
                matrix[1][0] ? 1 : 0,  // dot2
                matrix[2][0] ? 1 : 0,  // dot3
                matrix[0][1] ? 1 : 0,  // dot4
                matrix[1][1] ? 1 : 0,  // dot5
                matrix[2][1] ? 1 : 0   // dot6
        };
    }
}
