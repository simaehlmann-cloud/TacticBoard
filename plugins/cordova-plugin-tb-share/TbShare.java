package de.tacticboard.share;

import android.content.Intent;
import android.net.Uri;

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
        if (!"shareFile".equals(action)) {
            return false;
        }

        final String dateiname = args.getString(0);
        final String mimeTyp = args.getString(1);
        final String base64 = args.getString(2);

        cordova.getThreadPool().execute(() -> {
            try {
                teile(dateiname, mimeTyp, base64);
                callbackContext.success();
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

        Intent auswahl = Intent.createChooser(intent, null);
        auswahl.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        cordova.getActivity().startActivity(auswahl);
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
