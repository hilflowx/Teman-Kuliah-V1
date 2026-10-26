# Teman Kuliah

Aplikasi Android untuk mahasiswa Indonesia: jadwal kuliah, mata kuliah, KRS/KHS (impor lewat OCR), tugas dan proyek, kalender akademik, pengingat/alarm, dan mode fokus belajar.

Semua data disimpan **lokal di perangkat** (Room/SQLite). Tidak ada server dan tidak ada akun cloud.

- Dokumen produk, aturan kerja, dan backlog: [`PRD.md`](PRD.md) (**baca bagian 1 dan 2 sebelum mengubah kode**)
- Paket: `com.temankuliah.app` · `minSdk 24` · `targetSdk 36` · `compileSdk 37`
- Stack: Kotlin, Jetpack Compose (Material 3), Room, Glance, ML Kit Text Recognition (model bawaan)

## Prasyarat

- Android Studio terbaru dan JDK 17
- Android SDK platform 37

## Menjalankan secara lokal

1. Buka Android Studio, pilih **Open**, lalu pilih folder proyek ini.
2. Tunggu sinkronisasi Gradle selesai.
3. Jalankan varian `debug` di emulator atau perangkat.

Tidak ada API key atau file `.env` yang dibutuhkan. Build debug memakai debug keystore bawaan Android, sehingga clone baru langsung bisa dibangun.

```bash
./gradlew :app:assembleDebug
```

## Quality gate

Semua perintah ini harus lulus sebelum perubahan dianggap selesai (sama dengan CI di `.github/workflows/android-ci.yml`):

```bash
./gradlew :app:lintDebug            # warningsAsErrors, terhadap lint-baseline.xml
./gradlew :app:detekt               # terhadap detekt-baseline.xml
./gradlew :buildHealth              # analisis dependency
./gradlew :app:testDebugUnitTest    # unit test (JUnit + Robolectric)
./gradlew :app:assembleDebug
```

Jangan menambah entri baru ke `detekt-baseline.xml` atau `lint-baseline.xml`.

## Build release

Build release memakai minify dan shrink resources. Keystore dibaca dari environment variable (jangan simpan keystore atau sandi di repo):

| Variabel | Keterangan |
|---|---|
| `KEYSTORE_PATH` | Path file keystore |
| `STORE_PASSWORD` | Sandi keystore |
| `KEY_ALIAS` | Alias kunci (default `upload`) |
| `KEY_PASSWORD` | Sandi kunci |

```bash
KEYSTORE_PATH=/path/ke/upload.jks STORE_PASSWORD=... KEY_PASSWORD=... \
  ./gradlew :app:assembleRelease
```

Tanpa `KEYSTORE_PATH`, build release tetap berjalan tetapi **tidak ditandatangani** (Gradle menampilkan peringatan).

Untuk debug keystore kustom, atur `DEBUG_KEYSTORE_PATH` (opsional: `DEBUG_STORE_PASSWORD`, `DEBUG_KEY_ALIAS`, `DEBUG_KEY_PASSWORD`) atau taruh `debug.keystore` di root proyek (sudah diabaikan oleh git).

## Arsitektur singkat

```
ui/        layar Compose + controller per fitur
data/      Room, repository, OCR/dokumen, alarm/notifikasi, cadangan lokal
domain/    aturan murni (jadwal, nilai/IPK, reminder, rekonsiliasi KRS/KHS)
widget/    widget "Hari ini"
```

Satu modul `:app`. Skema Room diekspor ke `app/schemas/`; perubahan skema wajib menaikkan versi, menambah `Migration`, dan menjalankan `MigrationGuardTest`.
