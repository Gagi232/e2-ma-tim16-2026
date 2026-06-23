package com.example.slagalica.logic;

public class MojBrojLogic {

    /**
     * Evaluira matematički izraz iz stringa.
     * Podržava +, -, ×, ÷ i zagrade.
     * Vraća rezultat ili Integer.MIN_VALUE ako je izraz neispravan.
     */
    public static int evaluate(String expression) {
        try {
            String normalized = expression
                    .replace("×", "*")
                    .replace("÷", "/")
                    .replaceAll("\\s+", "");
            double result = evalExpr(normalized, new int[]{0});
            if (Double.isInfinite(result) || Double.isNaN(result)) return Integer.MIN_VALUE;
            // Proveravamo da li je ceo broj
            if (result == Math.floor(result)) return (int) result;
            return Integer.MIN_VALUE; // razlomak nije dozvoljen
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    // Rekurzivni descent parser
    private static double evalExpr(String s, int[] pos) {
        double val = evalTerm(s, pos);
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == '+') { pos[0]++; val += evalTerm(s, pos); }
            else if (c == '-') { pos[0]++; val -= evalTerm(s, pos); }
            else break;
        }
        return val;
    }

    private static double evalTerm(String s, int[] pos) {
        double val = evalFactor(s, pos);
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == '*') { pos[0]++; val *= evalFactor(s, pos); }
            else if (c == '/') {
                pos[0]++;
                double divisor = evalFactor(s, pos);
                if (divisor == 0) throw new ArithmeticException("Deljenje nulom");
                val /= divisor;
            }
            else break;
        }
        return val;
    }

    private static double evalFactor(String s, int[] pos) {
        if (pos[0] >= s.length()) throw new RuntimeException("Neočekivani kraj");
        if (s.charAt(pos[0]) == '(') {
            pos[0]++; // preskočimo '('
            double val = evalExpr(s, pos);
            if (pos[0] < s.length() && s.charAt(pos[0]) == ')') pos[0]++;
            return val;
        }
        // Broj
        int start = pos[0];
        if (pos[0] < s.length() && s.charAt(pos[0]) == '-') pos[0]++; // unary minus
        while (pos[0] < s.length() && Character.isDigit(s.charAt(pos[0]))) pos[0]++;
        return Double.parseDouble(s.substring(start, pos[0]));
    }

    /**
     * Bodovanje po specifikaciji:
     * - Tačan broj: 10 bodova
     * - Nije tačan, ali je bliži: 5 bodova
     * - Ništa nije uneto: 0 bodova
     */
    public static int calcScore(int myResult, int oppResult, int target, boolean isMyRound) {
        boolean myExact  = (myResult != Integer.MIN_VALUE && myResult == target);
        boolean oppExact = (oppResult != Integer.MIN_VALUE && oppResult == target);

        if (myExact) return 10;

        // Ni jedan nije tačan — ko je bliži?
        if (myResult == Integer.MIN_VALUE || myResult == -999999) return 0;

        int myDiff  = Math.abs(myResult - target);
        int oppDiff = (oppResult == Integer.MIN_VALUE || oppResult == -999999)
                ? Integer.MAX_VALUE : Math.abs(oppResult - target);

        if (myDiff < oppDiff) return 5;
        if (myDiff == oppDiff && isMyRound) return 5; // spec: runda bila moja
        return 0;
    }
}