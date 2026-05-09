package com.example.slagalica.ui.region;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.example.slagalica.R;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class RegionsFragment extends Fragment {

    private MapView mapView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_regions, container, false);

        // Mapa
        Configuration.getInstance().setUserAgentValue(
                requireActivity().getPackageName()
        );
        mapView = view.findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(7.0);
        mapView.getController().setCenter(new GeoPoint(44.0165, 21.0059));

        dodajMarker(mapView, 45.2671, 19.8335, "Vojvodina 🌾");
        dodajMarker(mapView, 44.8176, 20.4569, "Beograd 🏙️");
        dodajMarker(mapView, 44.0, 20.9, "Šumadija ⛰️");
        dodajMarker(mapView, 43.9, 20.0, "Zapadna Srbija 🌲");
        dodajMarker(mapView, 43.9, 22.0, "Istočna Srbija 🏔️");
        dodajMarker(mapView, 43.3, 21.9, "Južna Srbija 🌄");

        // Klikovi na regione
        view.findViewById(R.id.rowVojvodina).setOnClickListener(v ->
                prikaziStatistiku("Vojvodina", "🌾", 3, 5, 2, 24, 156, 245));
        view.findViewById(R.id.rowBeograd).setOnClickListener(v ->
                prikaziStatistiku("Beograd", "🏙️", 7, 4, 6, 41, 289, 312));
        view.findViewById(R.id.rowSumadija).setOnClickListener(v ->
                prikaziStatistiku("Šumadija", "⛰️", 1, 3, 4, 18, 112, 178));
        view.findViewById(R.id.rowZapadna).setOnClickListener(v ->
                prikaziStatistiku("Zapadna Srbija", "🌲", 0, 2, 1, 12, 89, 142));
        view.findViewById(R.id.rowIstocna).setOnClickListener(v ->
                prikaziStatistiku("Istočna Srbija", "🏔️", 0, 1, 2, 8, 67, 98));
        view.findViewById(R.id.rowJuzna).setOnClickListener(v ->
                prikaziStatistiku("Južna Srbija", "🌄", 0, 0, 1, 5, 45, 76));

        return view;
    }

    private void prikaziStatistiku(String naziv, String ikona,
                                   int prvaMesta, int drugaMesta, int trecaMesta,
                                   int aktivnih, int registrovanih, int zvezde) {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_region_stats, null);

        ((TextView) dialogView.findViewById(R.id.tvRegionNaziv))
                .setText(ikona + " " + naziv);
        ((TextView) dialogView.findViewById(R.id.tvZvezde))
                .setText(zvezde + " ⭐");
        ((TextView) dialogView.findViewById(R.id.tvPrvaMesta))
                .setText(String.valueOf(prvaMesta));
        ((TextView) dialogView.findViewById(R.id.tvDrugaMesta))
                .setText(String.valueOf(drugaMesta));
        ((TextView) dialogView.findViewById(R.id.tvTrecaMesta))
                .setText(String.valueOf(trecaMesta));
        ((TextView) dialogView.findViewById(R.id.tvAktivnih))
                .setText(String.valueOf(aktivnih));
        ((TextView) dialogView.findViewById(R.id.tvRegistrovanih))
                .setText(String.valueOf(registrovanih));

        new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Zatvori", null)
                .show();
    }

    private void dodajMarker(MapView map, double lat, double lon, String title) {
        Marker marker = new Marker(map);
        marker.setPosition(new GeoPoint(lat, lon));
        marker.setTitle(title);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(marker);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }
}