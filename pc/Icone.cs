// Le icone di Gestione Home: gli stessi Material Symbols del tablet.
//
// PERCHE' NON SONO DISEGNATE QUI. Un'icona non e' un disegno: e' un segno che
// si legge in un decimo di secondo, e per arrivarci ci vuole il lavoro di chi
// le disegna di mestiere. Sul tablet questa lezione era gia' stata pagata -
// prima le icone erano manciate di drawLine, e la lampadina da un metro
// sembrava un lucchetto - e la conclusione vale anche qui.
//
// PERCHE' SONO LE STESSE, E NON SOLO SIMILI. Chi apre questa finestra ha il
// tablet davanti, appeso al muro o sul tavolo: due iconografie diverse per le
// stesse sei sezioni sarebbero due apparecchi diversi. La tavolozza veniva gia'
// da Tinte.java; adesso vengono da li' anche i segni e la scala dei corpi.
//
// I PERCORSI NON SONO STATI RICOPIATI A MANO. Sono stati estratti da
// tablet/src/dev/casa/Icone.java con uno script, cosi' come sono: coordinate
// nel riquadro "0 -960 960 960" dei Material Symbols (stile rounded, peso 400,
// Apache 2.0). Ricopiare quaranta stringhe di trecento caratteri a mano e' un
// errore di trascrizione che aspetta solo di succedere, e che si vedrebbe come
// un'icona storta senza che nessuno sappia perche'.
//
// Il prezzo della copia e' scritto: se un giorno il tablet cambia un'icona, qui
// non cambia da sola. E' lo stesso costo che il progetto paga gia' per
// Tuya.java, ed e' il costo che due progetti separati hanno per definizione.
//
// L'analizzatore capisce il sottoinsieme che i Material Symbols usano davvero -
// M L H V Q T C S Z, assolute e relative - e non gli archi, che in questo
// insieme non compaiono mai.

using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Globalization;

namespace Casa
{
    /// <summary>Un segno, dal quadrato unitario a un rettangolo qualsiasi.</summary>
    public static class Icone
    {
        // I percorsi si analizzano la prima volta che si disegnano, e poi
        // restano: un GraphicsPath e' memoria vera, una stringa no.
        private static readonly Dictionary<string, GraphicsPath> fatti =
            new Dictionary<string, GraphicsPath>();

        /// <summary>Chi non viene dai Material Symbols: il marchio Spotify,
        ///  che ha un riquadro suo (vedi U e V in fondo).</summary>
        private static readonly HashSet<string> RIQUADRO24 =
            new HashSet<string> { "spotify" };

        private static readonly Dictionary<string, string> DATI =
            new Dictionary<string, string>
        {
            { "home_piena", "M160-200v-360q0-19 8.5-36t23.5-28l240-180q21-16 48-16t48 16l240 180q15 11 23.5 28t8.5 36v360q0 33-23.5 56.5T720-120H600q-17 0-28.5-11.5T560-160v-200q0-17-11.5-28.5T520-400h-80q-17 0-28.5 11.5T400-360v200q0 17-11.5 28.5T360-120H240q-33 0-56.5-23.5T160-200Z" },
            { "musica_piena", "M287-167q-47-47-47-113t47-113q47-47 113-47 23 0 42.5 5.5T480-418v-382q0-17 11.5-28.5T520-840h160q17 0 28.5 11.5T720-800v80q0 17-11.5 28.5T680-680H560v400q0 66-47 113t-113 47q-66 0-113-47Z" },
            { "radio_piena", "M160-80q-33 0-56.5-23.5T80-160v-534l523-213q14-5 27.5.5T649-887q5 14-.5 27.5T629-841L332-720h468q33 0 56.5 23.5T880-640v480q0 33-23.5 56.5T800-80H160Zm231-149q29-29 29-71t-29-71q-29-29-71-29t-71 29q-29 29-29 71t29 71q29 29 71 29t71-29ZM160-520h480v-40q0-17 11.5-28.5T680-600q17 0 28.5 11.5T720-560v40h80v-120H160v120Z" },
            { "app_piena", "M200-520q-33 0-56.5-23.5T120-600v-160q0-33 23.5-56.5T200-840h160q33 0 56.5 23.5T440-760v160q0 33-23.5 56.5T360-520H200Zm0 400q-33 0-56.5-23.5T120-200v-160q0-33 23.5-56.5T200-440h160q33 0 56.5 23.5T440-360v160q0 33-23.5 56.5T360-120H200Zm400-400q-33 0-56.5-23.5T520-600v-160q0-33 23.5-56.5T600-840h160q33 0 56.5 23.5T840-760v160q0 33-23.5 56.5T760-520H600Zm0 400q-33 0-56.5-23.5T520-200v-160q0-33 23.5-56.5T600-440h160q33 0 56.5 23.5T840-360v160q0 33-23.5 56.5T760-120H600Z" },
            { "sveglia_piena", "M520-456v-144q0-17-11.5-28.5T480-640q-17 0-28.5 11.5T440-600v159q0 8 3 15.5t9 13.5l112 112q11 11 28 11t28-11q11-11 11-28t-11-28L520-456ZM339.5-108.5q-65.5-28.5-114-77t-77-114Q120-365 120-440t28.5-140.5q28.5-65.5 77-114t114-77Q405-800 480-800t140.5 28.5q65.5 28.5 114 77t77 114Q840-515 840-440t-28.5 140.5q-28.5 65.5-77 114t-114 77Q555-80 480-80t-140.5-28.5ZM82-668q-11-11-11-28t11-28l114-114q11-11 28-11t28 11q11 11 11 28t-11 28L138-668q-11 11-28 11t-28-11Zm796 0q-11 11-28 11t-28-11L708-782q-11-11-11-28t11-28q11-11 28-11t28 11l114 114q11 11 11 28t-11 28Z" },
            { "lampada", "M423.5-103.5Q400-127 400-160h160q0 33-23.5 56.5T480-80q-33 0-56.5-23.5ZM360-200q-17 0-28.5-11.5T320-240q0-17 11.5-28.5T360-280h240q17 0 28.5 11.5T640-240q0 17-11.5 28.5T600-200H360Zm-30-120q-69-41-109.5-110T180-580q0-125 87.5-212.5T480-880q125 0 212.5 87.5T780-580q0 81-40.5 150T630-320H330Zm24-80h252q45-32 69.5-79T700-580q0-92-64-156t-156-64q-92 0-156 64t-64 156q0 54 24.5 101t69.5 79Zm126 0Z" },
            { "lampada_piena", "M423.5-103.5Q400-127 400-160h160q0 33-23.5 56.5T480-80q-33 0-56.5-23.5ZM360-200q-17 0-28.5-11.5T320-240q0-17 11.5-28.5T360-280h240q17 0 28.5 11.5T640-240q0 17-11.5 28.5T600-200H360Zm-30-120q-69-41-109.5-110T180-580q0-125 87.5-212.5T480-880q125 0 212.5 87.5T780-580q0 81-40.5 150T630-320H330Z" },
            { "microfono", "M395-435q-35-35-35-85v-240q0-50 35-85t85-35q50 0 85 35t35 85v240q0 50-35 85t-85 35q-50 0-85-35Zm45 275v-83q-92-13-157.5-78T203-479q-2-17 9-29t28-12q17 0 28.5 11.5T284-480q14 70 69.5 115T480-320q72 0 127-45.5T676-480q4-17 15.5-28.5T720-520q17 0 28 12t9 29q-14 91-79 157t-158 79v83q0 17-11.5 28.5T480-120q-17 0-28.5-11.5T440-160Z" },
            { "avvia", "M320-273v-414q0-17 12-28.5t28-11.5q5 0 10.5 1.5T381-721l326 207q9 6 13.5 15t4.5 19q0 10-4.5 19T707-446L381-239q-5 3-10.5 4.5T360-233q-16 0-28-11.5T320-273Z" },
            { "pausa", "M640-200q-33 0-56.5-23.5T560-280v-400q0-33 23.5-56.5T640-760q33 0 56.5 23.5T720-680v400q0 33-23.5 56.5T640-200Zm-320 0q-33 0-56.5-23.5T240-280v-400q0-33 23.5-56.5T320-760q33 0 56.5 23.5T400-680v400q0 33-23.5 56.5T320-200Z" },
            { "ferma", "M240-320v-320q0-33 23.5-56.5T320-720h320q33 0 56.5 23.5T720-640v320q0 33-23.5 56.5T640-240H320q-33 0-56.5-23.5T240-320Z" },
            { "precedente", "M220-280v-400q0-17 11.5-28.5T260-720q17 0 28.5 11.5T300-680v400q0 17-11.5 28.5T260-240q-17 0-28.5-11.5T220-280Zm458-1L430-447q-9-6-13.5-14.5T412-480q0-10 4.5-18.5T430-513l248-166q5-4 11-5t11-1q16 0 28 11t12 29v330q0 18-12 29t-28 11q-5 0-11-1t-11-5Z" },
            { "successivo", "M660-280v-400q0-17 11.5-28.5T700-720q17 0 28.5 11.5T740-680v400q0 17-11.5 28.5T700-240q-17 0-28.5-11.5T660-280Zm-440-35v-330q0-18 12-29t28-11q5 0 11 1t11 5l248 166q9 6 13.5 14.5T548-480q0 10-4.5 18.5T530-447L282-281q-5 4-11 5t-11 1q-16 0-28-11t-12-29Z" },
            { "volume_piu", "M760-481q0-83-44-151.5T598-735q-15-7-22-21.5t-2-29.5q6-16 21.5-23t31.5 0q97 43 155 131.5T840-481q0 108-58 196.5T627-153q-16 7-31.5 0T574-176q-5-15 2-29.5t22-21.5q74-34 118-102.5T760-481ZM280-360H160q-17 0-28.5-11.5T120-400v-160q0-17 11.5-28.5T160-600h120l132-132q19-19 43.5-8.5T480-703v446q0 27-24.5 37.5T412-228L280-360Zm380-120q0 42-19 79.5T591-339q-10 6-20.5.5T560-356v-250q0-12 10.5-17.5t20.5.5q31 25 50 63t19 80Z" },
            { "volume_meno", "M360-360H240q-17 0-28.5-11.5T200-400v-160q0-17 11.5-28.5T240-600h120l132-132q19-19 43.5-8.5T560-703v446q0 27-24.5 37.5T492-228L360-360Zm380-120q0 42-19 79.5T671-339q-10 6-20.5.5T640-356v-250q0-12 10.5-17.5t20.5.5q31 25 50 63t19 80Z" },
            { "volume_muto", "M671-177q-11 7-22 13t-23 11q-15 7-30.5 0T574-176q-6-15 1.5-29.5T598-227q7-3 13-6.5t12-7.5L480-368v111q0 27-24.5 37.5T412-228L280-360H160q-17 0-28.5-11.5T120-400v-160q0-17 11.5-28.5T160-600h88L84-764q-11-11-11-28t11-28q11-11 28-11t28 11l680 680q11 11 11 28t-11 28q-11 11-28 11t-28-11l-93-93Zm89-304q0-83-44-151.5T598-735q-15-7-22-21.5t-2-29.5q6-16 21.5-23t31.5 0q97 43 155 131t58 197q0 33-6 65.5T817-353q-8 22-24.5 27.5t-30.5.5q-14-5-22.5-18t-.5-30q11-26 16-52.5t5-55.5ZM591-623q33 21 51 63t18 80v10q0 5-1 10-2 13-14 17t-22-6l-51-51q-6-6-9-13.5t-3-15.5v-77q0-12 10.5-17.5t20.5.5Zm-201-59q-6-6-6-14t6-14l22-22q19-19 43.5-8.5T480-703v63q0 14-12 19t-22-5l-56-56Zm10 328v-94l-72-72H200v80h114l86 86Zm-36-130Z" },
            { "casuale", "M600-160q-17 0-28.5-11.5T560-200q0-17 11.5-28.5T600-240h64l-99-99q-12-12-11.5-28.5T566-396q12-12 28.5-12t28.5 12l97 98v-62q0-17 11.5-28.5T760-400q17 0 28.5 11.5T800-360v160q0 17-11.5 28.5T760-160H600Zm-428-12q-11-11-11-28t11-28l492-492h-64q-17 0-28.5-11.5T560-760q0-17 11.5-28.5T600-800h160q17 0 28.5 11.5T800-760v160q0 17-11.5 28.5T760-560q-17 0-28.5-11.5T720-600v-64L228-172q-11 11-28 11t-28-11Zm-1-560q-11-11-11-28t11-28q11-11 27.5-11t28.5 11l168 167q11 11 11.5 27.5T395-565q-11 11-28 11t-28-11L171-732Z" },
            { "cerca", "M380-320q-109 0-184.5-75.5T120-580q0-109 75.5-184.5T380-840q109 0 184.5 75.5T640-580q0 44-14 83t-38 69l224 224q11 11 11 28t-11 28q-11 11-28 11t-28-11L532-372q-30 24-69 38t-83 14Zm0-80q75 0 127.5-52.5T560-580q0-75-52.5-127.5T380-760q-75 0-127.5 52.5T200-580q0 75 52.5 127.5T380-400Z" },
            { "chiudi", "M480-424 284-228q-11 11-28 11t-28-11q-11-11-11-28t11-28l196-196-196-196q-11-11-11-28t11-28q11-11 28-11t28 11l196 196 196-196q11-11 28-11t28 11q11 11 11 28t-11 28L536-480l196 196q11 11 11 28t-11 28q-11 11-28 11t-28-11L480-424Z" },
            { "indietro", "m382-480 294 294q15 15 14.5 35T675-116q-15 15-35 15t-35-15L297-423q-12-12-18-27t-6-30q0-15 6-30t18-27l308-308q15-15 35.5-14.5T676-844q15 15 15 35t-15 35L382-480Z" },
            { "aggiorna", "M480-160q-134 0-227-93t-93-227q0-134 93-227t227-93q69 0 132 28.5T720-690v-70q0-17 11.5-28.5T760-800q17 0 28.5 11.5T800-760v200q0 17-11.5 28.5T760-520H560q-17 0-28.5-11.5T520-560q0-17 11.5-28.5T560-600h128q-32-56-87.5-88T480-720q-100 0-170 70t-70 170q0 100 70 170t170 70q68 0 124.5-34.5T692-367q8-14 22.5-19.5t29.5-.5q16 5 23 21t-1 30q-41 80-117 128t-169 48Z" },
            { "regola", "M451.5-131.5Q440-143 440-160v-160q0-17 11.5-28.5T480-360q17 0 28.5 11.5T520-320v40h280q17 0 28.5 11.5T840-240q0 17-11.5 28.5T800-200H520v40q0 17-11.5 28.5T480-120q-17 0-28.5-11.5ZM160-200q-17 0-28.5-11.5T120-240q0-17 11.5-28.5T160-280h160q17 0 28.5 11.5T360-240q0 17-11.5 28.5T320-200H160Zm131.5-171.5Q280-383 280-400v-40H160q-17 0-28.5-11.5T120-480q0-17 11.5-28.5T160-520h120v-40q0-17 11.5-28.5T320-600q17 0 28.5 11.5T360-560v160q0 17-11.5 28.5T320-360q-17 0-28.5-11.5ZM480-440q-17 0-28.5-11.5T440-480q0-17 11.5-28.5T480-520h320q17 0 28.5 11.5T840-480q0 17-11.5 28.5T800-440H480Zm131.5-171.5Q600-623 600-640v-160q0-17 11.5-28.5T640-840q17 0 28.5 11.5T680-800v40h120q17 0 28.5 11.5T840-720q0 17-11.5 28.5T800-680H680v40q0 17-11.5 28.5T640-600q-17 0-28.5-11.5ZM160-680q-17 0-28.5-11.5T120-720q0-17 11.5-28.5T160-760h320q17 0 28.5 11.5T520-720q0 17-11.5 28.5T480-680H160Z" },
            { "cestino", "M280-120q-33 0-56.5-23.5T200-200v-520q-17 0-28.5-11.5T160-760q0-17 11.5-28.5T200-800h160q0-17 11.5-28.5T400-840h160q17 0 28.5 11.5T600-800h160q17 0 28.5 11.5T800-760q0 17-11.5 28.5T760-720v520q0 33-23.5 56.5T680-120H280Zm148.5-171.5Q440-303 440-320v-280q0-17-11.5-28.5T400-640q-17 0-28.5 11.5T360-600v280q0 17 11.5 28.5T400-280q17 0 28.5-11.5Zm160 0Q600-303 600-320v-280q0-17-11.5-28.5T560-640q-17 0-28.5 11.5T520-600v280q0 17 11.5 28.5T560-280q17 0 28.5-11.5Z" },
            { "piu", "M440-440H240q-17 0-28.5-11.5T200-480q0-17 11.5-28.5T240-520h200v-200q0-17 11.5-28.5T480-760q17 0 28.5 11.5T520-720v200h200q17 0 28.5 11.5T760-480q0 17-11.5 28.5T720-440H520v200q0 17-11.5 28.5T480-200q-17 0-28.5-11.5T440-240v-200Z" },
            { "meno", "M240-440q-17 0-28.5-11.5T200-480q0-17 11.5-28.5T240-520h480q17 0 28.5 11.5T760-480q0 17-11.5 28.5T720-440H240Z" },
            { "spunta", "m382-354 339-339q12-12 28-12t28 12q12 12 12 28.5T777-636L410-268q-12 12-28 12t-28-12L182-440q-12-12-11.5-28.5T183-497q12-12 28.5-12t28.5 12l142 143Z" },
            { "timer", "M400-840q-17 0-28.5-11.5T360-880q0-17 11.5-28.5T400-920h160q17 0 28.5 11.5T600-880q0 17-11.5 28.5T560-840H400Zm108.5 428.5Q520-423 520-440v-160q0-17-11.5-28.5T480-640q-17 0-28.5 11.5T440-600v160q0 17 11.5 28.5T480-400q17 0 28.5-11.5Zm-168 303Q275-137 226-186t-77.5-114.5Q120-366 120-440t28.5-139.5Q177-645 226-694t114.5-77.5Q406-800 480-800q62 0 119 20t107 58l28-28q11-11 28-11t28 11q11 11 11 28t-11 28l-28 28q38 50 58 107t20 119q0 74-28.5 139.5T734-186q-49 49-114.5 77.5T480-80q-74 0-139.5-28.5Z" },
            { "sveglia_piu", "M440-400v80q0 17 11.5 28.5T480-280q17 0 28.5-11.5T520-320v-80h80q17 0 28.5-11.5T640-440q0-17-11.5-28.5T600-480h-80v-80q0-17-11.5-28.5T480-600q-17 0-28.5 11.5T440-560v80h-80q-17 0-28.5 11.5T320-440q0 17 11.5 28.5T360-400h80ZM339.5-108.5q-65.5-28.5-114-77t-77-114Q120-365 120-440t28.5-140.5q28.5-65.5 77-114t114-77Q405-800 480-800t140.5 28.5q65.5 28.5 114 77t77 114Q840-515 840-440t-28.5 140.5q-28.5 65.5-77 114t-114 77Q555-80 480-80t-140.5-28.5ZM82-668q-11-11-11-28t11-28l114-114q11-11 28-11t28 11q11 11 11 28t-11 28L138-668q-11 11-28 11t-28-11Zm796 0q-11 11-28 11t-28-11L708-782q-11-11-11-28t11-28q11-11 28-11t28 11l114 114q11 11 11 28t-11 28Z" },
            { "notte", "M484-80q-84 0-157.5-32t-128-86.5Q144-253 112-326.5T80-484q0-128 72-232t193-146q22-8 41 5.5t18 36.5q-3 85 27 162t90 137q60 60 137 90t162 27q26-1 38.5 17.5T863-345q-44 120-147.5 192.5T484-80Z" },
            { "sole", "M440-840v-40q0-17 11.5-28.5T480-920q17 0 28.5 11.5T520-880v40q0 17-11.5 28.5T480-800q-17 0-28.5-11.5T440-840Zm0 760v-40q0-17 11.5-28.5T480-160q17 0 28.5 11.5T520-120v40q0 17-11.5 28.5T480-40q-17 0-28.5-11.5T440-80Zm440-360h-40q-17 0-28.5-11.5T800-480q0-17 11.5-28.5T840-520h40q17 0 28.5 11.5T920-480q0 17-11.5 28.5T880-440Zm-760 0H80q-17 0-28.5-11.5T40-480q0-17 11.5-28.5T80-520h40q17 0 28.5 11.5T160-480q0 17-11.5 28.5T120-440Zm670-293-14 14q-11 11-27.5 11T720-720q-11-11-11.5-27.5T719-776l15-15q11-12 28-12t29 12q12 12 11.5 29T790-733ZM241-184l-15 15q-11 12-28 12t-29-12q-12-12-11.5-29t12.5-29l14-14q11-11 27.5-11t28.5 12q11 11 11.5 27.5T241-184Zm492 14-14-14q-11-11-11-27.5t12-28.5q11-11 27.5-11.5T776-241l15 15q12 11 12 28t-12 29q-12 12-29 11.5T733-170ZM184-719l-15-15q-12-11-12-28t12-29q12-12 29-11.5t29 12.5l14 14q11 11 11 27.5T240-720q-11 11-27.5 11.5T184-719Zm126 409q-70-70-70-170t70-170q70-70 170-70t170 70q70 70 70 170t-70 170q-70 70-170 70t-170-70Z" },
            { "accensione", "M480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-73 24.5-138.5T172-736q11-14 26.5-13t26.5 11q11 10 14.5 26T229-679q-32 41-50.5 91.5T160-480q0 134 93 227t227 93q134 0 227-93t93-227q0-57-18.5-107.5T731-679q-14-17-10.5-33t14.5-26q11-10 26.5-11t26.5 13q43 52 67.5 117.5T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm-28.5-371.5Q440-463 440-480v-360q0-17 11.5-28.5T480-880q17 0 28.5 11.5T520-840v360q0 17-11.5 28.5T480-440q-17 0-28.5-11.5Z" },
            { "tavolozza", "M480-80q-82 0-155-31.5t-127.5-86Q143-252 111.5-325T80-480q0-83 32.5-156t88-127Q256-817 330-848.5T488-880q80 0 151 27.5t124.5 76q53.5 48.5 85 115T880-518q0 115-70 176.5T640-280h-74q-9 0-12.5 5t-3.5 11q0 12 15 34.5t15 51.5q0 50-27.5 74T480-80ZM303-457q17-17 17-43t-17-43q-17-17-43-17t-43 17q-17 17-17 43t17 43q17 17 43 17t43-17Zm120-160q17-17 17-43t-17-43q-17-17-43-17t-43 17q-17 17-17 43t17 43q17 17 43 17t43-17Zm200 0q17-17 17-43t-17-43q-17-17-43-17t-43 17q-17 17-17 43t17 43q17 17 43 17t43-17Zm120 160q17-17 17-43t-17-43q-17-17-43-17t-43 17q-17 17-17 43t17 43q17 17 43 17t43-17Z" },
            { "luminosita", "M346-160H240q-33 0-56.5-23.5T160-240v-106l-77-78q-11-12-17-26.5T60-480q0-15 6-29.5T83-536l77-78v-106q0-33 23.5-56.5T240-800h106l78-77q12-11 26.5-17t29.5-6q15 0 29.5 6t26.5 17l78 77h106q33 0 56.5 23.5T800-720v106l77 78q11 12 17 26.5t6 29.5q0 15-6 29.5T877-424l-77 78v106q0 33-23.5 56.5T720-160H614l-78 77q-12 11-26.5 17T480-60q-15 0-29.5-6T424-83l-78-77Zm134-120q83 0 141.5-58.5T680-480q0-83-58.5-141.5T480-680v400Z" },
            { "avanti", "M504-480 348-636q-11-11-11-28t11-28q11-11 28-11t28 11l184 184q6 6 8.5 13t2.5 15q0 8-2.5 15t-8.5 13L404-268q-11 11-28 11t-28-11q-11-11-11-28t11-28l156-156Z" },
            { "clessidra", "M320-160h320v-120q0-66-47-113t-113-47q-66 0-113 47t-47 113v120Zm273-407q47-47 47-113v-120H320v120q0 66 47 113t113 47q66 0 113-47ZM200-80q-17 0-28.5-11.5T160-120q0-17 11.5-28.5T200-160h40v-120q0-61 28.5-114.5T348-480q-51-32-79.5-85.5T240-680v-120h-40q-17 0-28.5-11.5T160-840q0-17 11.5-28.5T200-880h560q17 0 28.5 11.5T800-840q0 17-11.5 28.5T760-800h-40v120q0 61-28.5 114.5T612-480q51 32 79.5 85.5T720-280v120h40q17 0 28.5 11.5T800-120q0 17-11.5 28.5T760-80H200Z" },
            { "cancella", "m560-424 76 76q11 11 28 11t28-11q11-11 11-28t-11-28l-76-76 76-76q11-11 11-28t-11-28q-11-11-28-11t-28 11l-76 76-76-76q-11-11-28-11t-28 11q-11 11-11 28t11 28l76 76-76 76q-11 11-11 28t11 28q11 11 28 11t28-11l76-76ZM360-160q-19 0-36-8.5T296-192L116-432q-16-21-16-48t16-48l180-240q11-15 28-23.5t36-8.5h440q33 0 56.5 23.5T880-720v480q0 33-23.5 56.5T800-160H360Z" },
            { "oggi", "M289-329q-29-29-29-71t29-71q29-29 71-29t71 29q29 29 29 71t-29 71q-29 29-71 29t-71-29ZM200-80q-33 0-56.5-23.5T120-160v-560q0-33 23.5-56.5T200-800h40v-40q0-17 11.5-28.5T280-880q17 0 28.5 11.5T320-840v40h320v-40q0-17 11.5-28.5T680-880q17 0 28.5 11.5T720-840v40h40q33 0 56.5 23.5T840-720v560q0 33-23.5 56.5T760-80H200Zm0-80h560v-400H200v400Z" },
            { "campanella", "M200-200q-17 0-28.5-11.5T160-240q0-17 11.5-28.5T200-280h40v-280q0-83 50-147.5T420-792v-28q0-25 17.5-42.5T480-880q25 0 42.5 17.5T540-820v28q80 20 130 84.5T720-560v280h40q17 0 28.5 11.5T800-240q0 17-11.5 28.5T760-200H200ZM480-80q-33 0-56.5-23.5T400-160h160q0 33-23.5 56.5T480-80ZM120-560q-17 0-28.5-13T82-603q8-75 42-139.5T211-855q13-11 29.5-10t26.5 15q10 14 8 30t-15 28q-39 37-64 86t-33 106q-2 17-14 28.5T120-560Zm720 0q-17 0-29-11.5T797-600q-8-57-33-106t-64-86q-13-12-15-28t8-30q10-14 26.5-15t29.5 10q53 48 87 112.5T878-603q2 17-9.5 30T840-560Z" },
            { "sincronizza", "M240-478q0 45 17 87.5t53 78.5l10 10v-58q0-17 11.5-28.5T360-400q17 0 28.5 11.5T400-360v160q0 17-11.5 28.5T360-160H200q-17 0-28.5-11.5T160-200q0-17 11.5-28.5T200-240h70l-16-14q-52-46-73-105t-21-119q0-94 48-170.5T337-766q14-8 29.5-1t20.5 23q5 15-.5 30T367-691q-58 32-92.5 88.5T240-478Zm480-4q0-45-17-87.5T650-648l-10-10v58q0 17-11.5 28.5T600-560q-17 0-28.5-11.5T560-600v-160q0-17 11.5-28.5T600-800h160q17 0 28.5 11.5T800-760q0 17-11.5 28.5T760-720h-70l16 14q49 49 71.5 106.5T800-482q0 94-48 170.5T623-194q-14 8-29.5 1T573-216q-5-15 .5-30t19.5-23q58-32 92.5-88.5T720-482Z" },
            { "posizione", "M480-186q122-112 181-203.5T720-552q0-109-69.5-178.5T480-800q-101 0-170.5 69.5T240-552q0 71 59 162.5T480-186Zm0 79q-14 0-28-5t-25-15q-65-60-115-117t-83.5-110.5q-33.5-53.5-51-103T160-552q0-150 96.5-239T480-880q127 0 223.5 89T800-552q0 45-17.5 94.5t-51 103Q698-301 648-244T533-127q-11 10-25 15t-28 5Zm0-453Zm0 80q33 0 56.5-23.5T560-560q0-33-23.5-56.5T480-640q-33 0-56.5 23.5T400-560q0 33 23.5 56.5T480-480Z" },
            { "su", "M440-647 244-451q-12 12-28 11.5T188-452q-11-12-11.5-28t11.5-28l264-264q6-6 13-8.5t15-2.5q8 0 15 2.5t13 8.5l264 264q11 11 11 27.5T772-452q-12 12-28.5 12T715-452L520-647v447q0 17-11.5 28.5T480-160q-17 0-28.5-11.5T440-200v-447Z" },
            { "giu", "M440-313v-447q0-17 11.5-28.5T480-800q17 0 28.5 11.5T520-760v447l196-196q12-12 28-11.5t28 12.5q11 12 11.5 28T772-452L508-188q-6 6-13 8.5t-15 2.5q-8 0-15-2.5t-13-8.5L188-452q-11-11-11-27.5t11-28.5q12-12 28.5-12t28.5 12l195 195Z" },
            { "notizie", "M160-120q-33 0-56.5-23.5T80-200v-616q0-7 6-9.5t11 2.5l50 50 52-53q6-6 14-6t14 6l53 53 53-53q6-6 14-6t14 6l52 53 53-53q6-6 14-6t14 6l53 53 52-53q6-6 14-6t14 6l53 53 53-53q6-6 14-6t14 6l52 53 50-50q5-5 11-2.5t6 9.5v616q0 33-23.5 56.5T800-120H160Zm0-80h280v-240H160v240Zm360 0h280v-80H520v80Zm0-160h280v-80H520v80ZM160-520h640v-120H160v120Z" },
            { "spotify", "M12 0C5.4 0 0 5.4 0 12s5.4 12 12 12 12-5.4 12-12S18.66 0 12 0zm5.521 17.34c-.24.359-.66.48-1.021.24-2.82-1.74-6.36-2.101-10.561-1.141-.418.122-.779-.179-.899-.539-.12-.421.18-.78.54-.9 4.56-1.021 8.52-.6 11.64 1.32.42.18.479.659.301 1.02zm1.44-3.3c-.301.42-.841.6-1.262.3-3.239-1.98-8.159-2.58-11.939-1.38-.479.12-1.02-.12-1.14-.6-.12-.48.12-1.021.6-1.141C9.6 9.9 15 10.561 18.72 12.84c.361.181.54.78.241 1.2zm.12-3.36C15.24 8.4 8.82 8.16 5.16 9.301c-.6.179-1.2-.181-1.38-.721-.18-.601.18-1.2.72-1.381 4.26-1.26 11.28-1.02 15.721 1.621.539.3.719 1.02.419 1.56-.299.421-1.02.599-1.559.3z" },
        };

        public static bool Ce(string nome)
        {
            return nome != null && DATI.ContainsKey(nome.ToLowerInvariant());
        }

        /// <summary>
        /// Disegna l'icona centrata dentro il rettangolo, alta quanto il lato
        /// piu' corto. Se il nome non c'e' non disegna niente: un'icona mancante
        /// e' meno peggio di un'eccezione dentro un OnPaint.
        /// </summary>
        public static void Disegna(Graphics g, string nome, RectangleF dove, Color colore)
        {
            GraphicsPath p = Percorso(nome);
            if (p == null) return;
            float lato = Math.Min(dove.Width, dove.Height);
            if (lato <= 0) return;

            GraphicsState salvato = g.Save();
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TranslateTransform(dove.X + (dove.Width - lato) / 2f,
                                 dove.Y + (dove.Height - lato) / 2f);
            g.ScaleTransform(lato, lato);
            using (SolidBrush b = new SolidBrush(colore)) g.FillPath(b, p);
            g.Restore(salvato);
        }

        /// <summary>Il percorso normalizzato nel quadrato da zero a uno.</summary>
        public static GraphicsPath Percorso(string nome)
        {
            if (nome == null) return null;
            string chiave = nome.ToLowerInvariant();
            lock (fatti)
            {
                GraphicsPath gia;
                if (fatti.TryGetValue(chiave, out gia)) return gia;
                string d;
                if (!DATI.TryGetValue(chiave, out d)) return null;
                GraphicsPath p = Analizza(d, RIQUADRO24.Contains(chiave));
                fatti[chiave] = p;
                return p;
            }
        }

        // ---- l'analizzatore ------------------------------------------------
        //
        // Le coordinate arrivano nel riquadro 0 -960 960 960: x da 0 a 960, y da
        // -960 a 0. Si porta tutto nel quadrato unitario dividendo per 960 e
        // aggiungendo uno alla y, cosi' chi disegna decide la grandezza con una
        // moltiplicazione sola.

        private static GraphicsPath Analizza(string d, bool venti4)
        {
            GraphicsPath p = new GraphicsPath(FillMode.Winding);
            int i = 0;
            char comando = ' ';
            float x = 0, y = 0, inizioX = 0, inizioY = 0;
            float controlloX = 0, controlloY = 0;
            bool figuraAperta = false;

            while (i < d.Length)
            {
                char c = d[i];
                if (char.IsWhiteSpace(c) || c == ',') { i++; continue; }
                if (char.IsLetter(c)) { comando = c; i++; }

                bool relativo = char.IsLower(comando);
                char tipo = char.ToUpperInvariant(comando);

                if (tipo == 'Z')
                {
                    if (figuraAperta) p.CloseFigure();
                    x = inizioX; y = inizioY;
                    figuraAperta = false;
                    continue;
                }

                float[] n;
                switch (tipo)
                {
                    case 'M': case 'L': case 'T': n = Numeri(d, ref i, 2); break;
                    case 'H': case 'V':           n = Numeri(d, ref i, 1); break;
                    case 'Q': case 'S':           n = Numeri(d, ref i, 4); break;
                    case 'C':                     n = Numeri(d, ref i, 6); break;
                    default: i++; continue;
                }
                if (n == null) break;

                float px = x, py = y;
                switch (tipo)
                {
                    case 'M':
                        x = relativo ? x + n[0] : n[0];
                        y = relativo ? y + n[1] : n[1];
                        if (figuraAperta) p.CloseFigure();
                        p.StartFigure();
                        figuraAperta = true;
                        inizioX = x; inizioY = y;
                        controlloX = x; controlloY = y;
                        // Dopo una M le coppie che seguono sono delle L: e' la
                        // regola della norma, e i Material Symbols la usano.
                        comando = relativo ? 'l' : 'L';
                        break;

                    case 'L':
                        x = relativo ? x + n[0] : n[0];
                        y = relativo ? y + n[1] : n[1];
                        p.AddLine(U(px, venti4), V(py, venti4), U(x, venti4), V(y, venti4));
                        controlloX = x; controlloY = y;
                        break;

                    case 'H':
                        x = relativo ? x + n[0] : n[0];
                        p.AddLine(U(px, venti4), V(py, venti4), U(x, venti4), V(y, venti4));
                        controlloX = x; controlloY = y;
                        break;

                    case 'V':
                        y = relativo ? y + n[0] : n[0];
                        p.AddLine(U(px, venti4), V(py, venti4), U(x, venti4), V(y, venti4));
                        controlloX = x; controlloY = y;
                        break;

                    case 'Q': case 'T':
                    {
                        float qx, qy;
                        if (tipo == 'Q')
                        {
                            qx = relativo ? x + n[0] : n[0];
                            qy = relativo ? y + n[1] : n[1];
                            x  = relativo ? x + n[2] : n[2];
                            y  = relativo ? y + n[3] : n[3];
                        }
                        else
                        {
                            // T: il controllo e' lo specchio del precedente.
                            qx = 2 * px - controlloX;
                            qy = 2 * py - controlloY;
                            x = relativo ? x + n[0] : n[0];
                            y = relativo ? y + n[1] : n[1];
                        }
                        // GDI+ non ha le quadratiche: si alzano a cubiche, che
                        // e' una conversione esatta e non un'approssimazione.
                        p.AddBezier(U(px, venti4), V(py, venti4),
                                    U(px + 2f / 3f * (qx - px), venti4), V(py + 2f / 3f * (qy - py), venti4),
                                    U(x + 2f / 3f * (qx - x), venti4),   V(y + 2f / 3f * (qy - y), venti4),
                                    U(x, venti4), V(y, venti4));
                        controlloX = qx; controlloY = qy;
                        break;
                    }

                    case 'C': case 'S':
                    {
                        float c1x, c1y, c2x, c2y;
                        if (tipo == 'C')
                        {
                            c1x = relativo ? x + n[0] : n[0];
                            c1y = relativo ? y + n[1] : n[1];
                            c2x = relativo ? x + n[2] : n[2];
                            c2y = relativo ? y + n[3] : n[3];
                            x   = relativo ? x + n[4] : n[4];
                            y   = relativo ? y + n[5] : n[5];
                        }
                        else
                        {
                            c1x = 2 * px - controlloX;
                            c1y = 2 * py - controlloY;
                            c2x = relativo ? x + n[0] : n[0];
                            c2y = relativo ? y + n[1] : n[1];
                            x   = relativo ? x + n[2] : n[2];
                            y   = relativo ? y + n[3] : n[3];
                        }
                        p.AddBezier(U(px, venti4), V(py, venti4), U(c1x, venti4), V(c1y, venti4), U(c2x, venti4), V(c2y, venti4), U(x, venti4), V(y, venti4));
                        controlloX = c2x; controlloY = c2y;
                        break;
                    }
                }
            }
            if (figuraAperta) p.CloseFigure();
            return p;
        }

        // Due riquadri, e non uno solo. I Material Symbols arrivano da
        // "0 -960 960 960" - x da 0 a 960, y da -960 a 0 - e vanno raddrizzati;
        // il marchio Spotify arriva da un 24x24 con la y gia' positiva, e li'
        // basta dividere. Riscalare a mano il secondo per farlo entrare nel
        // primo vorrebbe dire un marchio ridisegnato da noi, che non si sa piu'
        // se e' l'originale: il riquadro giusto costa un booleano.
        private static float U(float x, bool venti4)
        {
            return venti4 ? x / 24f : x / 960f;
        }

        private static float V(float y, bool venti4)
        {
            return venti4 ? y / 24f : y / 960f + 1f;
        }

        private static float[] Numeri(string d, ref int i, int quanti)
        {
            float[] fuori = new float[quanti];
            for (int k = 0; k < quanti; k++)
            {
                while (i < d.Length && (char.IsWhiteSpace(d[i]) || d[i] == ',')) i++;
                int da = i;
                if (i < d.Length && (d[i] == '-' || d[i] == '+')) i++;
                // Un punto solo per numero. Nei percorsi compatti le coordinate
                // si attaccano senza separatore - ".24.359" sono DUE numeri, non
                // uno - e leggendo avidamente tutti i punti si ottiene ".24.359",
                // che non e' un numero e che fa saltare l'analisi con un errore
                // di formato. Succede nel marchio Spotify e non nei Material
                // Symbols, che scrivono le coordinate per esteso.
                bool punto = false;
                while (i < d.Length)
                {
                    if (char.IsDigit(d[i])) { i++; continue; }
                    if (d[i] == '.' && !punto) { punto = true; i++; continue; }
                    break;
                }
                if (i == da) return null;
                fuori[k] = float.Parse(d.Substring(da, i - da), CultureInfo.InvariantCulture);
            }
            return fuori;
        }
    }
}
