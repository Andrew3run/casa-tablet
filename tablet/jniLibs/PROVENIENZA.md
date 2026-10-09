# Provenienza di libgolibrespot.so

È il demone **go-librespot** compilato per Android/ARM a 32 bit, un ELF
eseguibile. Ha il nome `.so` e sta in `jniLibs/` così l'installer di Android lo
estrae in `nativeLibraryDir` già eseguibile. Assistente Home lo lancia come
processo figlio: vedi `src/dev/casa/Musica.java`.

    origine   https://github.com/SEKY443/Android-LibreThing
              release 1.3.1 (27 agosto 2026), app-armeabi-v7a-release.apk,
              voce lib/armeabi-v7a/libgolibrespot.so
    sorgente  https://github.com/SEKY443/go-librespot-termux (forcella di
              devgianlu/go-librespot) + le due toppe Android in
              Android-LibreThing/scripts/patches/
    versione  go-librespot 093d23e0, build v0.0.0-20260824130318-093d23e
    compilato Go 1.27.0, con libVorbis 1.3.7, libFLAC 1.5.0 e mpg123 statici
    dipende   solo da libdl.so e liblog.so: niente ALSA, niente Play Services
    licenza   GPLv3

Per ricompilarlo servono NDK + vcpkg + Go su Linux o macOS
(`scripts/build-go-native.sh`), con le due toppe Android applicate.

## Licenza

Il demone è un **programma separato**, lanciato con ProcessBuilder e
interrogato via HTTP su 127.0.0.1. Se distribuisci l'app, distribuisci anche il
sorgente del demone (i due indirizzi qui sopra).
