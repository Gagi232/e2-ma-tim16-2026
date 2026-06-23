package com.example.slagalica.ui.region;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.RegionStatsRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.util.RegionUtil;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RegionsFragment extends Fragment {

    private MapView mapView;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final UserRepository userRepo = new UserRepository();
    private final RegionStatsRepository statsRepo = new RegionStatsRepository();

    private String myRegion = null;

    // Redovi rang liste - mapirani po nazivu regiona
    private LinearLayout rowVojvodina, rowBeograd, rowSumadija, rowZapadna, rowIstocna, rowJuzna;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_regions, container, false);

        Configuration.getInstance().setUserAgentValue(requireActivity().getPackageName());
        mapView = view.findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(7.0);
        mapView.getController().setCenter(new GeoPoint(44.0165, 21.0059));

        rowVojvodina = view.findViewById(R.id.rowVojvodina);
        rowBeograd   = view.findViewById(R.id.rowBeograd);
        rowSumadija  = view.findViewById(R.id.rowSumadija);
        rowZapadna   = view.findViewById(R.id.rowZapadna);
        rowIstocna   = view.findViewById(R.id.rowIstocna);
        rowJuzna     = view.findViewById(R.id.rowJuzna);

        rowVojvodina.setOnClickListener(v -> prikaziStatistiku(RegionUtil.Region.VOJVODINA));
        rowBeograd.setOnClickListener(v -> prikaziStatistiku(RegionUtil.Region.BEOGRAD));
        rowSumadija.setOnClickListener(v -> prikaziStatistiku(RegionUtil.Region.SUMADIJA));
        rowZapadna.setOnClickListener(v -> prikaziStatistiku(RegionUtil.Region.ZAPADNA_SRBIJA));
        rowIstocna.setOnClickListener(v -> prikaziStatistiku(RegionUtil.Region.ISTOCNA_SRBIJA));
        rowJuzna.setOnClickListener(v -> prikaziStatistiku(RegionUtil.Region.JUZNA_SRBIJA));

        loadPlayerMarkers();
        loadCurrentUserThenLeaderboard();

        return view;
    }

    /** Prvo saznamo region trenutnog igrača, pa onda učitamo rang listu da bismo mogli da ga obeležimo. */
    private void loadCurrentUserThenLeaderboard() {
        userRepo.getCurrentUser(new UserRepository.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                myRegion = user != null ? user.getRegion() : null;
                loadMonthlyRegionLeaderboard();
            }
            @Override
            public void onError(Exception e) {
                myRegion = null;
                loadMonthlyRegionLeaderboard(); // svejedno prikaži listu, samo bez highlight-a
            }
        });
    }

    private void loadMonthlyRegionLeaderboard() {
        statsRepo.getMonthlyRegionRanking(new RegionStatsRepository.Callback<List<Map.Entry<String, Long>>>() {
            @Override
            public void onSuccess(List<Map.Entry<String, Long>> ranking) {
                if (getView() == null) return;
                for (Map.Entry<String, Long> entry : ranking) {
                    applyRowData(entry.getKey(), entry.getValue());
                }
            }
            @Override
            public void onError(Exception e) {
                Toast.makeText(getContext(), "Greška pri učitavanju rang liste: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyRowData(String regionNaziv, long stars) {
        RegionUtil.Region r = RegionUtil.Region.fromNaziv(regionNaziv);
        LinearLayout row;
        int starsViewId, badgeViewId;

        switch (r) {
            case VOJVODINA: row = rowVojvodina; starsViewId = R.id.tvVojvodinaZvezde; badgeViewId = R.id.badgeVojvodina; break;
            case BEOGRAD: row = rowBeograd; starsViewId = R.id.tvBeogradZvezde; badgeViewId = R.id.badgeBeograd; break;
            case SUMADIJA: row = rowSumadija; starsViewId = R.id.tvSumadijaZvezde; badgeViewId = R.id.badgeSumadija; break;
            case ZAPADNA_SRBIJA: row = rowZapadna; starsViewId = R.id.tvZapadnaZvezde; badgeViewId = R.id.badgeZapadna; break;
            case ISTOCNA_SRBIJA: row = rowIstocna; starsViewId = R.id.tvIstocnaZvezde; badgeViewId = R.id.badgeIstocna; break;
            case JUZNA_SRBIJA: row = rowJuzna; starsViewId = R.id.tvJuznaZvezde; badgeViewId = R.id.badgeJuzna; break;
            default: return;
        }

        ((TextView) row.findViewById(starsViewId)).setText(stars + " ⭐");
        row.findViewById(badgeViewId).setVisibility(
                regionNaziv.equals(myRegion) ? View.VISIBLE : View.GONE);
        row.setBackgroundColor(regionNaziv.equals(myRegion)
                ? Color.parseColor("#FFF6D9") : Color.TRANSPARENT);
    }


    /**
     * Traži unutar reda bilo koji TextView koji NIJE naziv regiona - tj. onaj koji prikazuje zvezde.
     * NAPOMENA: pošto je u tvom XML-u svaki red drugačije struktuiran (Beograd ima ugnježdeni
     * LinearLayout sa "← Vaš region" labelom, ostali nemaju), najsigurnije je da dodaš FIKSAN ID
     * svakom "zvezde" TextView-u u XML-u (npr. tvBeogradZvezde, tvVojvodinaZvezde, ...) i da ovde
     * koristiš findViewById direktno po ID-u umesto generičkog pretraživanja. Ispod je generička
     * verzija koja radi SAMO ako je "zvezde" TextView poslednji direktan child reda.
     */

    private void loadPlayerMarkers() {
        db.collection("users").get()
                .addOnSuccessListener(snap -> {
                    if (mapView == null) return;
                    mapView.getOverlays().clear();

                    List<User> users = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        User u = doc.toObject(User.class);
                        if (u != null) {
                            u.setId(doc.getId());
                            users.add(u);
                        }
                    }

                    for (User u : users) {
                        if (u.getRegion() == null || (u.getRegionLat() == 0 && u.getRegionLng() == 0)) {
                            continue;
                        }
                        RegionUtil.Region r = RegionUtil.Region.fromNaziv(u.getRegion());
                        String title = (u.getUsername() != null ? u.getUsername() : "Igrač") + " " + r.ikona;
                        dodajMarker(mapView, u.getRegionLat(), u.getRegionLng(), title);
                    }

                    mapView.invalidate();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Greška pri učitavanju igrača: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void prikaziStatistiku(RegionUtil.Region region) {
        statsRepo.getRegionDetailStats(region.naziv,
                new RegionStatsRepository.Callback<RegionStatsRepository.RegionDetailStats>() {
                    @Override
                    public void onSuccess(RegionStatsRepository.RegionDetailStats s) {
                        if (getContext() == null) return;
                        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_region_stats, null);
                        ((TextView) dialogView.findViewById(R.id.tvRegionNaziv)).setText(region.ikona + " " + region.naziv);
                        ((TextView) dialogView.findViewById(R.id.tvPrvaMesta)).setText(String.valueOf(s.goldCount));
                        ((TextView) dialogView.findViewById(R.id.tvDrugaMesta)).setText(String.valueOf(s.silverCount));
                        ((TextView) dialogView.findViewById(R.id.tvTrecaMesta)).setText(String.valueOf(s.bronzeCount));
                        ((TextView) dialogView.findViewById(R.id.tvAktivnih)).setText(String.valueOf(s.activeCount));
                        ((TextView) dialogView.findViewById(R.id.tvRegistrovanih)).setText(String.valueOf(s.registeredCount));
                        new AlertDialog.Builder(requireContext()).setView(dialogView).setPositiveButton("Zatvori", null).show();
                    }
                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(getContext(), "Greška pri učitavanju statistike: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
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