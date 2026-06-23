package com.example.slagalica.util;

import java.util.Random;

public class RegionUtil {

    public enum Region {
        VOJVODINA("Vojvodina", "🌾", 45.0, 46.2, 19.0, 21.3),
        BEOGRAD("Beograd", "🏙️", 44.6, 44.95, 20.2, 20.65),
        SUMADIJA("Šumadija", "⛰️", 43.7, 44.4, 20.3, 21.4),
        ZAPADNA_SRBIJA("Zapadna Srbija", "🌲", 43.4, 44.3, 19.0, 20.1),
        ISTOCNA_SRBIJA("Istočna Srbija", "🏔️", 43.3, 44.4, 21.4, 22.6),
        JUZNA_SRBIJA("Južna Srbija", "🌄", 42.3, 43.3, 20.9, 22.4);

        public final String naziv;
        public final String ikona;
        public final double latMin, latMax, lonMin, lonMax;

        Region(String naziv, String ikona, double latMin, double latMax, double lonMin, double lonMax) {
            this.naziv = naziv;
            this.ikona = ikona;
            this.latMin = latMin;
            this.latMax = latMax;
            this.lonMin = lonMin;
            this.lonMax = lonMax;
        }

        public static Region fromNaziv(String naziv) {
            for (Region r : values()) if (r.naziv.equals(naziv)) return r;
            return BEOGRAD; // fallback
        }
    }

    private static final Random rnd = new Random();

    /** Generiše nasumičnu tačku unutar bounding box-a regiona. Poziva se SAMO jednom, pri registraciji. */
    public static double[] randomPointIn(Region region) {
        double lat = region.latMin + rnd.nextDouble() * (region.latMax - region.latMin);
        double lon = region.lonMin + rnd.nextDouble() * (region.lonMax - region.lonMin);
        return new double[]{lat, lon};
    }
}