package de.tacticboard.share;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Teilt eine in JavaScript erzeugte Datei ueber das System-Teilen-Menue.
 *
 * Hintergrund: Der Android-WebView von Cordova registriert keinen
 * DownloadListener. Ein <a download> mit blob:-URL tut deshalb schlicht
 * nichts - Bild-, GIF- und Taktik-Export waren in der App wirkungslos.
 * Auf iOS uebernimmt navigator.share, im Browser bleibt <a download>.
 *
 * Die Datei landet im Cache-Verzeichnis der App und wird ueber einen
 * FileProvider freigegeben. Es wird keine Speicher-Berechtigung benoetigt.
 */
public class TbShare extends CordovaPlugin {

    private static final String ORDNER = "shared";

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
            throws JSONException {
        final boolean inGalerie = "saveImage".equals(action);
        if (!inGalerie && !"shareFile".equals(action)) {
            return false;
        }

        final String dateiname = args.getString(0);
        final String mimeTyp = args.getString(1);
        final String base64 = args.getString(2);

        cordova.getThreadPool().execute(() -> {
            try {
                if (inGalerie && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    callbackContext.success(inGalerieSichern(dateiname, mimeTyp, base64));
                } else {
                    // Vor Android 10 braeuchte das Schreiben in die Galerie eine
                    // Speicher-Berechtigung. Die wollen wir nicht anfragen, also
                    // bekommen diese Geraete das Teilen-Menue.
                    teile(dateiname, mimeTyp, base64);
                    callbackContext.success("");
                }
            } catch (Exception e) {
                callbackContext.error(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
        return true;
    }

    private void teile(String dateiname, String mimeTyp, String base64) throws Exception {
        File ordner = new File(cordova.getActivity().getCacheDir(), ORDNER);
        if (!ordner.exists() && !ordner.mkdirs()) {
            throw new IllegalStateException("Cache-Ordner konnte nicht angelegt werden");
        }

        // Alte Exporte aufraeumen: der Cache soll nicht unbegrenzt wachsen.
        File[] alt = ordner.listFiles();
        if (alt != null) {
            long grenze = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
            for (File f : alt) {
                if (f.lastModified() < grenze) {
                    // Rueckgabewert bewusst ignoriert: schlaegt das Loeschen fehl,
                    // ist das kein Grund, den Export abzubrechen.
                    f.delete();
                }
            }
        }

        File ziel = new File(ordner, saeubere(dateiname));
        byte[] daten = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
        if (daten.length == 0) {
            throw new IllegalStateException("Leere Datei - Base64 konnte nicht dekodiert werden");
        }
        try (OutputStream out = new FileOutputStream(ziel)) {
            out.write(daten);
        }

        String authority = cordova.getActivity().getPackageName() + ".tbshare";
        Uri uri = FileProvider.getUriForFile(cordova.getActivity(), authority, ziel);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeTyp);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_TITLE, ziel.getName());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Entscheidend: ohne ClipData gibt Android die Leseerlaubnis nicht an die
        // vom Auswahldialog gestartete App weiter. Der Anhang ist dann sichtbar,
        // aber nicht lesbar - Mail verschickt nichts, WhatsApp bricht ab.
        intent.setClipData(ClipData.newUri(cordova.getActivity().getContentResolver(),
                ziel.getName(), uri));

        starteAuswahl(intent);
    }

    /**
     * Oeffnet das Teilen-Menue. Fuer exotische MIME-Typen wie application/json
     * hat kaum ein Geraet einen Empfaenger - startActivity wirft dann
     * ActivityNotFoundException und der Export schlaegt fehl, obwohl die Datei
     * laengst geschrieben ist. In dem Fall nochmal mit */* versuchen, damit
     * Dateimanager, Cloud-Dienste und Mail-Apps als Ziel auftauchen.
     */
    private void starteAuswahl(Intent intent) {
        try {
            Intent auswahl = Intent.createChooser(intent, null);
            auswahl.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cordova.getActivity().startActivity(auswahl);
        } catch (android.content.ActivityNotFoundException e) {
            intent.setType("*/*");
            Intent auswahl = Intent.createChooser(intent, null);
            auswahl.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cordova.getActivity().startActivity(auswahl);
        }
    }

    /**
     * Legt das Bild ueber den MediaStore in Bilder/TacticBoard ab. Ab Android 10
     * ist dafuer keine Berechtigung noetig, und die Galerie findet es sofort.
     *
     * @return der angezeigte Ordner, fuer die Rueckmeldung an den Nutzer
     */
    private String inGalerieSichern(String dateiname, String mimeTyp, String base64)
            throws Exception {
        byte[] daten = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
        if (daten.length == 0) {
            throw new IllegalStateException("Leere Datei - Base64 konnte nicht dekodiert werden");
        }

        String ordner = Environment.DIRECTORY_PICTURES + "/TacticBoard";
        ContentResolver resolver = cordova.getActivity().getContentResolver();

        ContentValues werte = new ContentValues();
        werte.put(MediaStore.MediaColumns.DISPLAY_NAME, saeubere(dateiname));
        werte.put(MediaStore.MediaColumns.MIME_TYPE, mimeTyp);
        werte.put(MediaStore.MediaColumns.RELATIVE_PATH, ordner);
        werte.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri ziel = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, werte);
        if (ziel == null) {
            throw new IllegalStateException("MediaStore lieferte keinen Eintrag");
        }
        try (OutputStream out = resolver.openOutputStream(ziel)) {
            if (out == null) {
                throw new IllegalStateException("MediaStore-Datei nicht beschreibbar");
            }
            out.write(daten);
        } catch (Exception e) {
            // Halbfertigen Eintrag nicht in der Galerie zuruecklassen
            resolver.delete(ziel, null, null);
            throw e;
        }

        werte.clear();
        werte.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(ziel, werte, null, null);
        return ordner;
    }

    /**
     * Der Dateiname kommt aus einem Eingabefeld des Nutzers. Alles, was den
     * Cache-Ordner verlassen koennte, wird entfernt.
     */
    private String saeubere(String name) {
        String sauber = name.replaceAll("[^A-Za-z0-9._\\-]", "_");
        while (sauber.startsWith(".")) {
            sauber = sauber.substring(1);
        }
        if (sauber.isEmpty()) {
            sauber = "TacticBoard";
        }
        return sauber.length() > 120 ? sauber.substring(0, 120) : sauber;
    }
}
