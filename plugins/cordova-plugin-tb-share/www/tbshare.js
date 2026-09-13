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
    }
};
