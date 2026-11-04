# Tret Launcher TV

Launcher para Android TV / Google TV hecho con Jetpack Compose for TV.

## Compilar sin Android Studio (GitHub Actions)
1. Crea una cuenta en github.com y un repositorio nuevo (privado o público).
2. Sube TODO el contenido de esta carpeta (incluida la carpeta oculta `.github`).
   En la web: "Add file" → "Upload files" y arrastra todo.
3. Ve a la pestaña **Actions** → "Compilar APK" → **Run workflow**.
4. Espera ~5 min. Entra a la ejecución y descarga el artefacto **TretLauncherTV-apk** (zip con `app-debug.apk`).

## Instalar en la TV
- Copia el APK a una USB e instálalo con un explorador de archivos en la TV, o
- Usa la app "Send Files to TV", o ADB: `adb connect IP_DE_TU_TV` y `adb install app-debug.apk`.

## Usarlo como inicio
Abre "Tret Launcher" desde la lista de apps. Para que sea el inicio al presionar Home,
usa Ajustes → "Cambiar launcher predeterminado" (según la marca de la TV puede variar).
Si tu TV no lo permite, por ADB: `adb shell cmd package set-home-activity com.tret.tvlauncher/.MainActivity`

## Personalizar
Todo está en `app/src/main/java/com/tret/tvlauncher/MainActivity.kt`:
- `Config`: ciudad, coordenadas y saludo.
- `continuarEjemplo`: tarjetas de "A continuación".
- Colores del fondo: variable `fondo` en `LauncherApp`.
