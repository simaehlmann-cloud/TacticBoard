package de.tacticboard.share;

import androidx.core.content.FileProvider;

/**
 * Eigene Provider-Klasse, die nichts weiter tut als zu existieren.
 *
 * Cordova deklariert im AndroidManifest bereits einen <provider> mit der
 * Klasse androidx.core.content.FileProvider. Komponenten werden im Manifest
 * ueber ihren Klassennamen identifiziert - haette dieses Plugin dieselbe
 * Klasse noch einmal eingetragen, wuerde der Manifest-Merger beide zu einem
 * Eintrag zusammenfassen und eine der beiden Authorities faellt weg. Das
 * Ergebnis war eine SecurityException beim Teilen, weil Android die Authority
 * de.tacticboard.app.tbshare nicht kannte.
 *
 * Mit einer eigenen Unterklasse bekommt der Eintrag einen eigenen Namen und
 * steht friedlich neben Cordovas Provider.
 */
public class TbFileProvider extends FileProvider {
}
