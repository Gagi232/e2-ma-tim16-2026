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
}