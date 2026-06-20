package com.example.slagalica.data.repository;

import com.example.slagalica.data.model.KoZnaZnaQuestion;
import com.example.slagalica.data.model.SpojniceSet;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameRepository {

    private static final String KZZ      = "koZnaZna";
    private static final String SPOJNICE = "spojnice";
    private static final String ASOCIJACIJE = "asocijacije";
    private static final String SKOCKO      = "skocko";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Dva interfejsa umesto jednog — rade sa lambda sintaksom
    public interface OnSuccess<T> { void run(T result); }
    public interface OnError     { void run(Exception e); }

    // Ko zna zna

    public void getSkockoComboById(String id,
                                   OnSuccess<com.example.slagalica.data.model.SkockoCombo> onSuccess, OnError onError) {
        db.collection(SKOCKO).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { onError.run(new Exception("Nije pronadjeno")); return; }
                    com.example.slagalica.data.model.SkockoCombo c = doc.toObject(com.example.slagalica.data.model.SkockoCombo.class);
                    c.setId(doc.getId());
                    onSuccess.run(c);
                })
                .addOnFailureListener(onError::run);
    }
    public void getRandomKoZnaZnaQuestions(int count,
                                           OnSuccess<List<KoZnaZnaQuestion>> onSuccess, OnError onError) {
        db.collection(KZZ).get()
                .addOnSuccessListener(snap -> {
                    List<KoZnaZnaQuestion> all = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        KoZnaZnaQuestion q = doc.toObject(KoZnaZnaQuestion.class);
                        q.setId(doc.getId());
                        all.add(q);
                    }
                    Collections.shuffle(all);
                    onSuccess.run(all.subList(0, Math.min(count, all.size())));
                })
                .addOnFailureListener(onError::run);
    }

    public void getQuestionsByIds(List<String> ids,
                                  OnSuccess<List<KoZnaZnaQuestion>> onSuccess, OnError onError) {
        List<KoZnaZnaQuestion> result = new ArrayList<>();
        if (ids.isEmpty()) { onSuccess.run(result); return; }
        int[] done = {0};
        for (String id : ids) {
            db.collection(KZZ).document(id).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            KoZnaZnaQuestion q = doc.toObject(KoZnaZnaQuestion.class);
                            q.setId(doc.getId());
                            result.add(q);
                        }
                        if (++done[0] == ids.size()) onSuccess.run(result);
                    })
                    .addOnFailureListener(e -> {
                        if (++done[0] == ids.size()) onSuccess.run(result);
                    });
        }
    }

    // Spojnice

    public void getRandomSpojniceSet(OnSuccess<SpojniceSet> onSuccess, OnError onError) {
        db.collection(SPOJNICE).get()
                .addOnSuccessListener(snap -> {
                    List<SpojniceSet> all = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        SpojniceSet s = doc.toObject(SpojniceSet.class);
                        s.setId(doc.getId());
                        all.add(s);
                    }
                    if (all.isEmpty()) { onError.run(new Exception("Nema setova")); return; }
                    Collections.shuffle(all);
                    onSuccess.run(all.get(0));
                })
                .addOnFailureListener(onError::run);
    }

    public void getSpojniceSetById(String id,
                                   OnSuccess<SpojniceSet> onSuccess, OnError onError) {
        db.collection(SPOJNICE).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { onError.run(new Exception("Set nije pronadjen")); return; }
                    SpojniceSet s = doc.toObject(SpojniceSet.class);
                    s.setId(doc.getId());
                    onSuccess.run(s);
                })
                .addOnFailureListener(onError::run);
    }

    public void getRandomAssociation(OnSuccess<com.example.slagalica.data.model.Association> onSuccess, OnError onError) {
        db.collection(ASOCIJACIJE).get()
                .addOnSuccessListener(snap -> {
                    List<com.example.slagalica.data.model.Association> all = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        com.example.slagalica.data.model.Association a = doc.toObject(com.example.slagalica.data.model.Association.class);
                        a.setId(doc.getId());
                        all.add(a);
                    }
                    if (all.isEmpty()) { onError.run(new Exception("Nema asocijacija")); return; }
                    Collections.shuffle(all);
                    onSuccess.run(all.get(0));
                })
                .addOnFailureListener(onError::run);
    }

    public void getAssociationById(String id, OnSuccess<com.example.slagalica.data.model.Association> onSuccess, OnError onError) {
        db.collection(ASOCIJACIJE).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { onError.run(new Exception("Nije pronadjeno")); return; }
                    com.example.slagalica.data.model.Association a = doc.toObject(com.example.slagalica.data.model.Association.class);
                    a.setId(doc.getId());
                    onSuccess.run(a);
                })
                .addOnFailureListener(onError::run);
    }

    public void getRandomSkockoCombo(OnSuccess<com.example.slagalica.data.model.SkockoCombo> onSuccess, OnError onError) {
        db.collection(SKOCKO).get()
                .addOnSuccessListener(snap -> {
                    List<com.example.slagalica.data.model.SkockoCombo> all = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        com.example.slagalica.data.model.SkockoCombo c = doc.toObject(com.example.slagalica.data.model.SkockoCombo.class);
                        c.setId(doc.getId());
                        all.add(c);
                    }
                    if (all.isEmpty()) { onError.run(new Exception("Nema kombinacija")); return; }
                    Collections.shuffle(all);
                    onSuccess.run(all.get(0));
                })
                .addOnFailureListener(onError::run);
    }


}