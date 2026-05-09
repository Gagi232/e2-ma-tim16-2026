package com.example.slagalica.ui.region;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

        Configuration.getInstance().setUserAgentValue(
                requireActivity().getPackageName()
        );

        mapView = view.findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        // Centriraj na Srbiju
        mapView.getController().setZoom(7.0);
        mapView.getController().setCenter(new GeoPoint(44.0165, 21.0059));

        // Dodaj markere za regione
        dodajMarker(mapView, 45.2671, 19.8335, "Vojvodina 🌾");
        dodajMarker(mapView, 44.8176, 20.4569, "Beograd 🏙️");
        dodajMarker(mapView, 44.0, 20.9, "Šumadija ⛰️");
        dodajMarker(mapView, 43.9, 20.0, "Zapadna Srbija 🌲");
        dodajMarker(mapView, 43.9, 22.0, "Istočna Srbija 🏔️");
        dodajMarker(mapView, 43.3, 21.9, "Južna Srbija 🌄");

        return view;
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