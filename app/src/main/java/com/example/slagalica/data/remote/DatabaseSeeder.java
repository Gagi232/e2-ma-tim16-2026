package com.example.slagalica.data.remote;

import com.example.slagalica.data.model.KoZnaZnaQuestion;
import com.example.slagalica.data.model.SpojnicePar;
import com.example.slagalica.data.model.SpojniceSet;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Arrays;

public class DatabaseSeeder {
    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public static void seedAll() {
        seedKoZnaZna();
        seedSpojnice();
        seedAsocijacije();
        seedSkocko();
    }
    public static void seedKoZnaZna() {
        Object[][] data = {
                {"Koji grad je prestonica Srbije?",
                        new String[]{"Novi Sad","Beograd","Niš","Kragujevac"}, 1},
                {"Koliko meseci ima u godini?",
                        new String[]{"10","11","12","13"}, 2},
                {"Koji element ima hemijski simbol 'O'?",
                        new String[]{"Azot","Vodik","Kiseonik","Ugljenik"}, 2},
                {"Ko je napisao 'Romeo i Julija'?",
                        new String[]{"Šekspir","Servantes","Dante","Gete"}, 0},
                {"Koja je najduža reka na svetu?",
                        new String[]{"Amazon","Nil","Jangtze","Misisipi"}, 1},
                {"Koliko planeta ima u Sunčevom sistemu?",
                        new String[]{"7","8","9","10"}, 1},
                {"Koji sport se igra na Vimbldonu?",
                        new String[]{"Fudbal","Golf","Tenis","Kriket"}, 2},
                {"Koliko strana ima kocka?",
                        new String[]{"4","5","6","8"}, 2},
                {"Koja zemlja ima najviše stanovnika?",
                        new String[]{"SAD","Indija","Kina","Brazil"}, 1},
                {"Koji je hemijski simbol zlata?",
                        new String[]{"Gl","Gd","Go","Au"}, 3}
        };
        for (Object[] row : data) {
            KoZnaZnaQuestion q = new KoZnaZnaQuestion(
                    (String) row[0],
                    Arrays.asList((String[]) row[1]),
                    (int) row[2]);
            db.collection("koZnaZna").add(q);
        }
    }

    public static void seedSpojnice() {
        // Set 1
        SpojniceSet s1 = new SpojniceSet();
        s1.setCategory("Poveži pevača sa pesmom");
        s1.setPairs(Arrays.asList(
                new SpojnicePar("Majkl Džekson",   "Thriller"),
                new SpojnicePar("Madona",           "Like a Prayer"),
                new SpojnicePar("Elton Džon",       "Rocket Man"),
                new SpojnicePar("Freddie Merkjuri", "Bohemian Rhapsody"),
                new SpojnicePar("Celin Dion",       "My Heart Will Go On")));
        db.collection("spojnice").add(s1);

        // Set 2
        SpojniceSet s2 = new SpojniceSet();
        s2.setCategory("Poveži državu sa glavnim gradom");
        s2.setPairs(Arrays.asList(
                new SpojnicePar("Francuska",  "Pariz"),
                new SpojnicePar("Nemačka",    "Berlin"),
                new SpojnicePar("Japan",      "Tokio"),
                new SpojnicePar("Brazil",     "Brazilija"),
                new SpojnicePar("Australija", "Kanbera")));
        db.collection("spojnice").add(s2);

        // Set 3
        SpojniceSet s3 = new SpojniceSet();
        s3.setCategory("Poveži pisca sa delom");
        s3.setPairs(Arrays.asList(
                new SpojnicePar("Dositej Obradović",        "Život i priključenija"),
                new SpojnicePar("Vuk Stefanović Karadžić",  "Srpski rječnik"),
                new SpojnicePar("Branko Radičević",         "Đački rastanak"),
                new SpojnicePar("Jovan Jovanović Zmaj",     "Đulići"),
                new SpojnicePar("Laza Lazarević",           "Školska ikona")));
        db.collection("spojnice").add(s3);
    }
    public static void seedAsocijacije() {
        com.example.slagalica.data.model.Association a = new com.example.slagalica.data.model.Association();
        a.setCol1(Arrays.asList("CRVENO", "PLAVO", "ZELENO", "ŽUTO"));
        a.setCol1Solution("BOJE");
        a.setCol2(Arrays.asList("LOPTA", "MREŽA", "GOL", "TIM"));
        a.setCol2Solution("FUDBAL");
        a.setCol3(Arrays.asList("KAFA", "ŠEĆER", "MLEKO", "ŠOLJA"));
        a.setCol3Solution("DORUČAK");
        a.setCol4(Arrays.asList("KNJIGA", "OLOVKA", "TABLA", "ĐAK"));
        a.setCol4Solution("ŠKOLA");
        a.setFinalSolution("SRBIJA");
        db.collection("asocijacije").add(a);

        com.example.slagalica.data.model.Association a2 = new com.example.slagalica.data.model.Association();
        a2.setCol1(Arrays.asList("SUNCE", "PLAŽA", "PESAK", "MORE"));
        a2.setCol1Solution("LETO");
        a2.setCol2(Arrays.asList("SNEG", "SKIJE", "HLADNO", "JAKNA"));
        a2.setCol2Solution("ZIMA");
        a2.setCol3(Arrays.asList("CVEĆE", "PUPOLJAK", "TOPLO", "MART"));
        a2.setCol3Solution("PROLEĆE");
        a2.setCol4(Arrays.asList("LIŠĆE", "ŽETVA", "OKTOBAR", "VETAR"));
        a2.setCol4Solution("JESEN");
        a2.setFinalSolution("GODISNJA DOBA");
        db.collection("asocijacije").add(a2);
    }

    public static void seedSkocko() {
        String[] symbols = {"⬛","⬜","🔴","💛","🔺","⭐"};
        java.util.Random r = new java.util.Random();

        for (int i = 0; i < 5; i++) {
            java.util.List<String> combo = new java.util.ArrayList<>();
            for (int j = 0; j < 4; j++) {
                combo.add(symbols[r.nextInt(symbols.length)]);
            }
            com.example.slagalica.data.model.SkockoCombo sc = new com.example.slagalica.data.model.SkockoCombo();
            sc.setCombination(combo);
            db.collection("skocko").add(sc);
        }
    }
}