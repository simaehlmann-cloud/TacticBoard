/* JavaScript-Seite des Teilen-Hooks. Wird von Cordova als window.TbShare
   bereitgestellt; im Browser und auf iOS existiert das Objekt nicht,
   dort greifen die Rueckfallebenen in index.html. */
var exec = require('cordova/exec');

module.exports = {
    /**
     * @param {string} dateiname  z.B. "Taktik_4-4-2.png"
     * @param {string} mimeTyp    z.B. "image/png"
     * @param {string} base64     Dateiinhalt, Base64-kodiert, ohne data:-Praefix
     */
    shareFile: function (dateiname, mimeTyp, base64) {
        return new Promise(function (resolve, reject) {
            exec(resolve, reject, 'TbShare', 'shareFile', [dateiname, mimeTyp, base64]);
        });
    },

    /** Legt das Bild direkt in der Galerie ab (Bilder/TacticBoard).
     *  Vor Android 10 faellt das Plugin auf das Teilen-Menue zurueck.
     *  Loest mit dem Ordnernamen auf, oder mit '' wenn geteilt wurde. */
    saveImage: function (dateiname, mimeTyp, base64) {
        return new Promise(function (resolve, reject) {
            exec(resolve, reject, 'TbShare', 'saveImage', [dateiname, mimeTyp, base64]);
        });
    },

    /** Legt eine Datei im Ordner Downloads/TacticBoard ab, wo jeder
     *  Dateimanager sie findet. Vor Android 10 Rueckfall auf das Teilen-Menue. */
    saveFile: function (dateiname, mimeTyp, base64) {
        return new Promise(function (resolve, reject) {
            exec(resolve, reject, 'TbShare', 'saveFile', [dateiname, mimeTyp, base64]);
        });
    }
};
