# PRD — Teman Kuliah (Android)

> Dokumen ini adalah sumber kebenaran untuk manusia dan AI agent (Claude, Codex, Gemini, Cursor, dsb.).
> **Baca bagian 1 dan 2 sebelum mengubah kode apa pun.**
> Temuan di bagian 8 berasal dari pembacaan kode statis (belum dikompilasi, test belum dijalankan). Verifikasi dulu sebelum memperbaiki.

- Versi dokumen: 1.21 (26 Oktober 2026)
- Basis kode: `teman-kuliah-oct-25-2026` · `versionName 1.0.1` · `versionCode 2`
- Pemilik: Xarverth

---

## 1. Ringkasan produk

**Teman Kuliah** adalah aplikasi Android untuk mahasiswa Indonesia untuk mengelola perkuliahan: jadwal kuliah, mata kuliah, KRS/KHS, tugas dan proyek, kalender akademik, pengingat/alarm, dan mode fokus belajar. Semua data disimpan **lokal di perangkat** (Room/SQLite), tanpa server dan tanpa akun cloud.

**Masalah yang diselesaikan:** data akademik mahasiswa tersebar di dokumen KRS/KHS (PDF/foto), grup chat, dan catatan. Aplikasi mengumpulkannya, mengubah dokumen menjadi data terstruktur lewat OCR, lalu mengingatkan jadwal dan tenggat.

**Prinsip produk**
1. Offline-first dan privat: data tidak keluar dari perangkat.
2. Data pengguna tidak boleh hilang diam-diam (tanpa `fallbackToDestructiveMigration`).
3. Bahasa antarmuka Indonesia; tampilan konsisten (Poppins, kartu biru gradasi, sudut membulat).

## 2. Aturan kerja untuk AI agent (WAJIB)

Aturan ini berasal dari instruksi pemilik pada sesi-sesi sebelumnya.

1. **Jangan buat ulang proyek.** Pertahankan struktur paket, teknologi, fitur, data, palet warna, dan logika yang ada.
2. **Ubah seminimal mungkin.** Kerjakan hanya item yang diminta. Perubahan UI/navigasi tidak boleh mengubah logika bisnis, dan sebaliknya.
3. **Jangan hapus atau korupsi data pengguna.** Perubahan skema Room wajib: naikkan `DATABASE_SCHEMA_VERSION`, tambah `Migration` di `Migrations.kt`, commit file skema baru di `app/schemas/`, dan jalankan `MigrationGuardTest`. Migrasi berisiko harus punya backup otomatis dan jalur rollback.
4. **Tulis unit test** untuk setiap perbaikan bug atau perilaku baru. **Jalankan** test dan laporkan hasil nyata (lulus/gagal, jumlah). Jangan klaim "sudah diuji" tanpa menjalankannya.
5. **Jangan menambah entri baru ke `detekt-baseline.xml` atau `lint-baseline.xml`.** Memperbaiki masalah dan menghapus entri baseline yang sudah tidak berlaku itu boleh.
6. Compiler memakai `allWarningsAsErrors` dan lint memakai `warningsAsErrors`. Kode baru tidak boleh menimbulkan warning.
7. Semua halaman turunan harus punya tombol kembali "←" di header dengan navigasi yang benar (juga untuk tombol back sistem).
8. Teks UI memakai Bahasa Indonesia. Komentar kode di proyek ini juga berbahasa Indonesia; ikuti gaya itu.
9. Jangan menambahkan permission, dependency, atau layanan jaringan tanpa persetujuan pemilik.
10. Jangan menyimpan rahasia (API key, keystore, password) di repo.
11. Jika permintaan ambigu atau bertentangan dengan dokumen ini, **tanyakan ke pemilik**, jangan menebak.

## 3. Tujuan dan non-tujuan

**Tujuan (versi 1.x)**
- Pengguna bisa mengimpor KRS dan KHS (PDF/foto) per semester, dan datanya terpisah rapi antarsemester.
- Jadwal kuliah terbentuk dari KRS atau manual, dengan pengingat yang andal.
- Tugas, proyek, dan ujian terlihat dalam satu kalender dan widget hari ini.
- Aplikasi stabil, bisa dibangun dari clone baru, dan lolos seluruh quality gate (bagian 9).

**Non-tujuan (saat ini)**
- Sinkronisasi cloud, akun online, atau login Google.
- Backend/API, iklan, analitik pihak ketiga.
- Dukungan iOS atau web.

## 4. Pengguna target

Mahasiswa S1/D3 di Indonesia yang memakai KRS/KHS kampus, di ponsel Android 7.0+ (`minSdk 24`). Penggunaan harian, satu tangan, sering tanpa internet. Mode tamu tersedia untuk mencoba tanpa mendaftar.

## 5. Fitur dan status

Status berdasarkan keberadaan kode dan test. Bukan hasil uji di perangkat.

| Area | Isi | Lokasi utama | Status |
|---|---|---|---|
| Auth lokal | Daftar, masuk (email/NIM + sandi PBKDF2), buat sandi, mode tamu, pembatasan percobaan masuk | `ui/auth/`, `data/repository/UserRepository.kt`, `domain/LocalPasswordHasher.kt`, `domain/LoginThrottle.kt` | Ada + test |
| Beranda | Ringkasan hari ini, pintasan | `ui/dashboard/` | Ada |
| Kalender | Jadwal, tugas, ujian, kegiatan akademik | `ui/calendar/` | Ada + test |
| Tugas & Proyek | Tugas, proyek, milestone, filter, status | `ui/tasks/`, `ui/projects/` | Ada + test |
| Akademik | Semester Aktif, KRS, KHS | `ui/academic/` | Ada |
| Impor dokumen | Unggah/pindai KRS-KHS, OCR ML Kit, pratinjau, rekonsiliasi | `data/files/AcademicDocumentProcessor.kt` (fasad; pengurai di `data/files/Academic*`, `KrsRowParser`, `KhsRowParser`), `ui/academic/documents/`, `domain/AcademicReconciler.kt` | Ada + test |
| Mata kuliah & IPK | Daftar MK, detail, IPK/IPS, transkrip cetak | `ui/courses/`, `domain/GpaCalculator.kt`, `domain/TranscriptBuilder.kt` | Ada + test |
| Jadwal Kuliah | Sekali/berulang, offline/online (platform + tautan), pengingat, alarm, deteksi bentrok, tautan ke KRS | `ui/schedule/`, `domain/Schedule*.kt` | Ada + test |
| Pengingat | Alarm tepat waktu, jadwal ulang setelah reboot/ganti zona waktu, diagnostik | `data/notifications/`, `domain/Reminder*.kt` | Ada + test |
| Profil & Pengaturan | Info pribadi, akun, tampilan, notifikasi, privasi, tentang, cadangan/pulihkan | `ui/profile/`, `data/backup/LocalDataArchive.kt` (pendukung `data/backup/Backup*`) | Ada + test |
| Widget | Widget "Hari ini" (Glance) | `widget/` | Ada + test |
| Notifikasi dalam aplikasi | Feed notifikasi | `ui/notifications/`, `domain/NotificationFeed.kt` | Ada + test |

Navigasi bawah: **Beranda · Kalender · Tugas · Akademik · Profil** (ikon Tugas memakai buku `Book`, Akademik memakai toga `School`). Halaman Akademik berisi tiga menu utama: Semester Aktif, KRS, KHS (menu "Dokumen Akademik" sudah dihapus dari UI). Layar Pemindai OCR mandiri, Kalkulator Nilai, dan Mode Fokus Belajar sudah dihapus (keputusan pemilik, 25 Okt 2026); impor KRS/KHS lewat `ui/academic/documents/` tetap ada.

## 6. Arsitektur dan peta kode

- **Stack:** Kotlin 2.2.10, Jetpack Compose (BOM 2026.09.00), Material 3 + adaptive/navigation-suite, Room 2.8.4 (KSP), Glance 1.1.1, ML Kit Text Recognition 16.0.1 (model bawaan/bundled), coroutines 1.10.2, AGP 9.1.1, `compileSdk 37`, `targetSdk 36`, `minSdk 24`, Java 11 + core library desugaring.
- **Pola:** satu `TemanKuliahViewModel` dengan `*Controller` per fitur (turunan `FeatureController`), repository per domain di `data/repository/`, logika murni di `domain/` (mudah diuji).
- **Satu modul:** `:app`, paket `com.temankuliah.app`.

```
ui/        layar Compose + controller per fitur (auth, dashboard, calendar, tasks, projects,
           academic, courses, schedule, profile, notifications, splash, components, theme)
data/      local (Room), repository, files (OCR/dokumen), notifications (alarm), backup (arsip lokal)
domain/    aturan murni: jadwal, nilai/IPK, reminder, tanggal mulai semester, SKS, rekonsiliasi
widget/    widget Hari ini
```

**Navigasi:** state berbasis string di `ui/TemanKuliahApp.kt` (`academicSection`, `profileSection`, `dashboardSection`, `tasksShowingProjects`) plus `BackHandler` bersyarat. Tab dipegang `viewModel.selectedTab`.

## 7. Model data (Room, skema v3)

Tabel: `users`, `courses`, `schedules`, `academic_documents`, `tasks`, `academic_events`, `projects`, `project_milestones`. Semua tabel data diikat `userId`; sebagian besar juga `semester`.

Aturan data penting:
- **KRS dan KHS per semester harus terpisah.** Impor KHS tidak boleh mengubah KRS dan sebaliknya; data antarsemester tidak boleh bocor. `academic_documents` unik per `(userId, semester, type)`.
- `users.authType`: `local` | `guest` (akun Google sudah dihapus dari kode; cadangan dengan `authType = google` kini ditolak validator). Kolom `recoveryCodeHash` sudah dihapus (skema v3, BL-12); cadangan lama yang masih memuat kunci itu tetap terbaca (kunci diabaikan).
- `schedules.sumber` (`KRS`/`MANUAL`), `krsId`, `recurrenceGroupId` (seri berulang), `mode` (`OFFLINE`/`ONLINE`).
- Skema tiap versi diekspor ke `app/schemas/`. Migrasi saat ini: `1 → 2`, `2 → 3`.
- Cadangan/pulihkan: `data/backup/LocalDataArchive.kt` (API publik; baca arsip, sandi JSON, validasi, dan lampiran ada di `BackupArchiveReader`, `BackupSnapshotCodec`, `BackupSnapshotValidator`, `BackupAssetFiles`; dengan hash integritas). `allowBackup=false`, jadi cadangan hanya lewat fitur ini.

## 8. Backlog perbaikan (hasil audit awal)

Prioritas: **P0** kerjakan dulu · **P1** berikutnya · **P2** hanya bila pemilik meminta. Setiap item punya kriteria selesai (DoD). Verifikasi temuan sebelum memperbaiki.

### P0

> Status 1.1: ✅ = sudah dikerjakan pada Tahap 1 (25 Okt 2026), belum diverifikasi dengan build/test (lihat riwayat revisi).

| ID | Temuan | Tindakan | DoD |
|---|---|---|---|
| BL-01 ✅ | **Selesai:** `.env` dihapus; `.gitignore` memuat `.env`, `.env.*`, `*.keystore`, `*.jks`, `debug.keystore`, `*.apk`, `*.aab`, `.idea/`, `*.iml`, `captures/`. (Temuan awal: File `.env` ada di proyek (1 byte, kosong) dan **tidak** ada di `.gitignore`. Risiko tanpa sengaja meng-commit rahasia.) | Tambahkan `.env`, `*.keystore`, `*.jks`, `debug.keystore` ke `.gitignore`; hapus `.env` dari proyek. | `.gitignore` memuat entri tsb; tidak ada file rahasia terlacak. |
| BL-02 ✅ | **Selesai:** README ditulis ulang. (Temuan awal: `README.md` masih templat AI Studio: menyebut `GEMINI_API_KEY`, `.env.example` (tidak ada), langkah hapus `signingConfig = debugConfig` (tidak ada). `build.gradle.kts` merujuk bagian README "Build release" yang tidak ada.) | Tulis ulang README: deskripsi, cara build/test, quality gate, signing release (env `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), ringkasan arsitektur, tautan ke PRD ini. | README akurat; clone baru bisa `./gradlew :app:assembleDebug` mengikuti README. |
| BL-03 🔒 | **Keputusan pemilik (25 Okt 2026): permission `INTERNET` dan `ACCESS_NETWORK_STATE` DIPERTAHANKAN karena aplikasi masih butuh internet. Jangan dihapus.** Catatan audit: saat ini tidak ditemukan kode jaringan di `app/src/main` (OCR memakai model bundled); `metadata.json` masih mendeklarasikan `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API`. | Tidak ada perubahan kode. Saat fitur yang memakai internet ditambahkan, dokumentasikan di PRD (bagian 3 dan 5) apa yang dikirim keluar perangkat dan perbarui prinsip offline-first di bagian 1. | Permission tetap ada; PRD menjelaskan alasan penggunaannya. |

### P1

| ID | Temuan | Tindakan | DoD |
|---|---|---|---|
| BL-04 ✅ | **Selesai:** state dan semua reset dihapus dari `TemanKuliahApp.kt`. (Temuan awal: `showingDocuments` di `TemanKuliahApp.kt` tinggal sisa dari menu Dokumen Akademik yang sudah dihapus: hanya dideklarasikan dan diisi `false`.) | Hapus state dan semua reset-nya. Jangan ubah perilaku navigasi lain. | Tidak ada referensi tersisa; tombol kembali di semua halaman Akademik tetap benar. |
| BL-05 ✅ | **Selesai (1.14):** `detekt` kini 0 temuan di luar baseline (sebelumnya 294). Baseline turun **1.807 → 461** entri (hanya dibuang, tidak ada entri baru; diverifikasi 461 ⊂ 1.807). **Perubahan aturan di `detekt.yml` (perlu persetujuan pemilik, mudah dibatalkan):** `FunctionNaming` mengizinkan PascalCase untuk `@Composable`/`@Preview`; `MaxLineLength` 120 → 160 (komentar dikecualikan); `MagicNumber` mengabaikan deklarasi properti, rentang, dan fungsi `@Composable`/`@Preview`; `LongMethod`, `CognitiveComplexMethod`, `CyclomaticComplexMethod` mengabaikan fungsi `@Composable`/`@Preview`. **Perubahan kode (tanpa perubahan perilaku):** `ReminderScheduler.kt` (pola getar jadi satu konstanta `VIBRATION_PATTERN`, `RINGTONE_STOP_DELAY_MS`; `triggerAlertEffects` dipecah jadi `vibrateNow`/`playRingtoneNow`; helper `buildChannelId`, `channelTitle`, `notificationAudioAttributes`); `ReminderSelfTest.kt` dan `ReminderDiagnostics.kt` (konstanta `MILLIS_PER_SECOND`, `WAIT_POLL_MS`); `RegisterComponents.kt` (konstanta kekuatan sandi, warna `WeakText`); `NotificationFeed.kt` (`buildNotificationFeed` dipecah ke kelas privat `FeedBuilder`; tanda tangan publik tetap); 10 baris >160 karakter dibungkus (termasuk 2 test); file `AdaptivePreviews.kt` → `AdaptiveMatrixPreview.kt` dan `WindowSize.kt` → `TkWidthClass.kt` (sesuai `MatchingDeclarationName`; 4 entri baseline ikut diganti nama). `@Suppress` beralasan (bukan baseline): `setAlarm` dan `awaitTestNotificationVisible` (`ReturnCount`), `baseBuilder`, `stageAcademicPhoto`, `buildNotificationFeed` (`LongParameterList`). **Sisa (opsional, tahap 3):** refaktor fungsi kompleks yang masih di baseline (mis. `CognitiveComplexMethod` 24, `ReturnCount` 37, `LongParameterList` 16 entri). | Lanjutkan hanya bila diminta; refaktor per paket dengan test yang ada. | Detekt 0 temuan dan baseline 461 entri (**diverifikasi dengan detekt-cli 1.23.8, konfigurasi dan baseline yang sama dengan Gradle**). **`:app:detekt` via Gradle, kompilasi, lint, dan 345 test belum dijalankan di lingkungan agent** (tanpa Android SDK/jaringan Gradle): jalankan quality gate bagian 9 sebelum merge. |
| BL-06 ◐ | **Sebagian besar selesai (1.23):** baseline lint kini **15** entri (semula 82 → 43 di 1.10 → 15). Di 1.23 dibuang 28 entri: `UseKtx` (18; kode memakai `edit {}`, `toUri()`, `toDrawable()`, `createBitmap`, `scale`), `ExifInterface` (2; pindah ke `androidx.exifinterface`), `UnusedAttribute` (2; `tools:targetApi="31"` di `today_widget_info.xml`), `DataExtractionRules` (1; `res/xml/data_extraction_rules.xml` + atribut manifest, selaras `allowBackup=false`), `IconLauncherShape` (5; entri **basi**: sudut semua PNG `ic_launcher` sudah transparan, jadi aturan tidak berlaku). Dependency baru: `androidx.core:core-ktx` **1.17.0** dan `androidx.exifinterface:exifinterface` **1.4.1** (katalog `libs.versions.toml`, `app/build.gradle.kts`). **Sisa (15):** `GradleDependency` (8), `NewerVersionAvailable` (4), `AndroidGradlePluginVersion` (2) (pembaruan versi; tidak dikerjakan karena tanpa jaringan versi tidak bisa diverifikasi dan lompatannya besar, mis. plugin Compose 2.2.10 → 2.4.21), `OldTargetApi` (1, lihat BL-08). | Pembaruan versi hanya dengan jaringan: naikkan satu kelompok, jalankan quality gate bagian 9, ulangi. Verifikasi dengan `./gradlew :app:lintDebug`; bila ada entri yang ternyata masih berlaku (mis. `IconLauncherShape`), kembalikan ke baseline atau perbaiki. | `./gradlew :app:lintDebug` lulus dengan baseline lebih kecil. **Belum dijalankan di lingkungan agent.** |
| BL-07 ✅ | **Selesai (1.21):** Uji migrasi skema Room v1 → v2 ditambahkan di `Migration1To2Test.kt` (Robolectric). Database v1 dibangun langsung dari skema resmi `schemas/.../1.json` (semua entitas, indeks, dan `room_master_table`). Menguji migrasi via `MIGRATION_1_2` dan runtime `TemanKuliahDatabase` v2: (1) Semua kolom baru (`sumber`, `krsId`, `sks`, `mode`, dll.) terbentuk dengan nilai default; (2) Baris jadwal yang cocok nama matkul di semester & user yang sama otomatis tertaut ke KRS (`sumber='KRS'`, `krsId`, `sks`); (3) Case-insensitivity dan whitespace trimming pada nama matkul ditangani dengan benar; (4) Multi jadwal/sesi (teori & praktikum) tertaut ke mata kuliah KRS yang sama; (5) Jadwal manual tanpa kecocokan KRS tetap `sumber='MANUAL'`, `krsId=null`, `sks=0`; (6) Tidak ada kebocoran antar-user (user lain tetap manual); (7) Data lama (waktu, hari, ruangan, dosen, tanggal, status selesai, alarm, recurrenceGroupId) 100% utuh; (8) Skema lolos validasi runtime hash identitas Room dan data queryable via `ScheduleDao`. | Tambahkan uji migrasi yang membuktikan data v1 selamat di v2 tanpa data hilang. | Test migrasi `Migration1To2Test` lulus 100% (2 kasus uji); seluruh data v1 utuh; verifikasi via DAO dan SQLite helper. |
| BL-08 | `lint-baseline` memuat `OldTargetApi` (`targetSdk 36`, `compileSdk 37`). | Baca pesan lint, evaluasi perubahan perilaku platform, lalu naikkan `targetSdk` hanya bila lolos uji alarm, notifikasi, dan file picker. | Pengingat dan widget tetap berfungsi di perangkat/emulator target. |
| BL-13 | `gradlew` bukan skrip bawaan Gradle (skrip pendek buatan sendiri, tanpa pengecekan Java dan penanganan path berspasi). Upaya membuat ulang di lingkungan agent gagal (tanpa jaringan). | Jalankan `./gradlew wrapper --gradle-version 9.3.1` di mesin dengan internet, lalu commit `gradlew`, `gradlew.bat`, dan `gradle-wrapper.jar`. | `./gradlew --version` berjalan dari clone baru. |
| BL-14 ✅ | **Selesai (1.11):** job `build-release-r8` ditambahkan di `.github/workflows/android-ci.yml` (`needs: quality-and-tests`, menjalankan `./gradlew :app:assembleRelease` tanpa keystore; bila gagal, mapping dan laporan diunggah). (Temuan awal: CI hanya membangun debug; masalah R8/minify baru ketahuan saat rilis.) | Tambah job `assembleRelease` (tanpa keystore, hanya memastikan R8 lolos) di `android-ci.yml`. | Job CI release hijau. **Belum pernah dijalankan; run pertama mungkin merah bila ada aturan R8 yang kurang di `proguard-rules.pro`.** |
| BL-15 ✅ | **Selesai (1.8):** 6 baris dependency yang di-comment dihapus dari `app/build.gradle.kts` dan 11 entri tak terpakai dihapus dari `libs.versions.toml` (`core-ktx`, `room-ktx`, `lifecycle-runtime-ktx`, `lifecycle-viewmodel-compose`, `material3-window-size-class`, `androidx-junit`, beserta versi `coreKtx`, `roomKtx`, `lifecycleRuntimeKtx`, `lifecycleViewmodelCompose`, `junitVersion`). Tidak ada yang dipakai build; semua entri katalog yang tersisa dirujuk. (Temuan awal: dependency di-comment/tak dipakai.) | Hapus entri yang tak dipakai setelah `:buildHealth` mengonfirmasi. | `:buildHealth` lulus. **Belum dijalankan di lingkungan agent.** |
| BL-16 ✅ | **Selesai (1.19):** Responsivitas Layout & Deteksi Otomatis: (1) Komponen bersama (`TkComponents`, `TkBottomNav`, `TkFormSheet`, `TkFullScreenDialog`, `TkListComponents`) fleksibel terhadap variasi ukuran layar (320dp s/d tablet >=600dp) dan font scale (0.85x - 2.0x); (2) Beranda (`DashboardScreen`, `DashboardGpaCard`, `DashboardAgenda`) adaptif `BoxWithConstraints` pada angka IPK, menghapus manual offset pada batas cumlaude, serta `heightIn(min = ...)` pada indikator timeline & panel statistik; (3) Layar Auth, Profil, Kalender, Tugas, dan Tabel Akademik bebas fixed height kaku & manual offset teks; (4) Pengujian deteksi otomatis via `ResponsiveLayoutLintTest` & pratinjau `ResponsivePreviews.kt`; (5) Pembersihan unused imports dan KTX lint, seluruh 5 quality gate lulus (`:app:lintDebug`, `:app:detekt`, `:buildHealth`, `:app:testDebugUnitTest` 358 test, `:app:assembleDebug`). | Perbaiki layout responsif di seluruh layar dan sediakan deteksi otomatis. | 100% responsif tanpa teks terpotong atau tumpang tindih; seluruh 5 quality gate lulus. |

### P2 (hanya jika diminta pemilik)

| ID | Temuan | Catatan |
|---|---|---|
| BL-09 ✅ | **Selesai (1.18):** posisi halaman di `TemanKuliahApp.kt` kini rute bertipe di `ui/Navigation.kt`: enum `DashboardSection`, `AcademicSection`, `ProfileSection` (menggantikan string "root"/"dashboard"/"krs"/dst., disimpan lewat `rememberSaveable`), plus `resolveBackAction(...)` yang memuat aturan tombol back (urutan: halaman turunan tab aktif → daftar proyek → kembali ke Beranda) dan enum `BackAction`. `subPageTransition` dibuat generik (`root: T`) menggantikan `isRoot(String)`. `AcademicDashboardScreen` dan `ProfileScreen` kini menerima `onOpenSection` bertipe. Perilaku tidak diubah. | Ganti ke rute bertipe (sealed class/enum) tanpa mengubah perilaku. Wajib test navigasi/Robolectric. | Tidak ada string rute tersisa; `NavigationTest` (9 test, JUnit murni) menjaga aturan back. **Belum dikompilasi dan test belum dijalankan; belum ada test Robolectric yang menekan tombol back di UI sungguhan.** |
| BL-10 ✅ | **Selesai (1.15; tahap pertama di 1.7):** (1.7) `ui/dashboard/DashboardComponents.kt` (1.098 baris) dipecah menjadi `DashboardComponents.kt`, `DashboardGpaCard.kt`, `DashboardAgenda.kt`. (1.15) `data/files/AcademicDocumentProcessor.kt` (1.258 baris) dipecah: **fasad** `AcademicDocumentProcessor` (101 baris; tipe data `Extraction`/`AcademicRow`/`KrsTotals`/`ScheduleParts`/`OcrWord` dan API publik tetap di sini, pemanggil tidak berubah) + `AcademicOcrReader` (baca berkas/OCR), `AcademicRowParser` (`findAcademicRows`, jalur berpembatas), `KrsRowParser`, `KhsRowParser`, `AcademicScheduleText` (jadwal/jam), `AcademicNameText` (kode/nama/dosen), `AcademicTextPatterns` (regex bersama), semuanya `internal object` di paket yang sama. `data/backup/LocalDataArchive.kt` (1.044 baris) dipecah: `LocalDataArchive` (API publik `export`/`exportToFile`/`payloadSha256`/`preview`/`restore` + penyusunan ekspor dan pemulihan, 454 baris) + `BackupArchiveReader`, `BackupSnapshotCodec`, `BackupSnapshotValidator`, `BackupAssetFiles`. Isi fungsi dipindah apa adanya (diverifikasi dengan skrip: kumpulan baris kode sebelum dan sesudah identik, selain delegasi fasad dan KDoc salinan). **Helper yang dinaikkan `private` → `internal` (keputusan tahap ini):** 27 anggota di `data/files` (pengurai KRS/KHS, `extractSchedule`, `canonicalDay`, `readKrsTime`/`KrsTime`, dan regex bersama di `AcademicTextPatterns`) serta 23 anggota di `data/backup` (`FORMAT`, `FORMAT_VERSION`, `MAX_ENTRIES`, `Snapshot`, `BackupSettings`, `ParsedArchive`, `encodeSnapshot`, `readSettings`, 8 dekoder `toXxx`, `validateSnapshot`, `semesterStartDateProblem`, 4 fungsi lampiran, `readAndValidate`); tidak ada yang `public`, dan test tidak perlu diubah. **Baseline detekt:** entri dipindah bersama kodenya (kunci memuat nama file/objek); `LargeClass` dan `TooManyFunctions` untuk dua objek lama dibuang karena sudah di bawah ambang. Jumlah entri 461 → 473 **bukan temuan baru**: entri yang sama (mis. literal `3`, `4`, `60`, `40`, `8`, `error: Exception`, kondisi `code.isBlank() && ...`) kini muncul di lebih dari satu berkas hasil pecahan sehingga tiap berkas perlu satu entri. 5 entri `lint-baseline.xml` (`ExifInterface`, `UseKtx`) dipindah ke `AcademicOcrReader.kt`/`LocalDataArchive.kt` tanpa entri baru. | Pecah file besar tanpa mengubah perilaku, dilindungi test yang sudah ada (`AcademicDocumentProcessorTest`, test arsip). | File besar terpecah per tanggung jawab; seluruh quality gate bagian 9 lulus; tidak ada perubahan perilaku. **Belum dikompilasi; `:app:detekt`, `lintDebug`, dan test belum dijalankan di lingkungan agent.** Pemetaan kunci baseline dibuat dengan skrip tanpa detekt, jadi bila `:app:detekt` melaporkan temuan baru di berkas hasil pecahan, kembalikan ke kunci yang sesuai (jangan tambah entri baru). |
| BL-11 | Hampir seluruh teks UI di-hardcode di Kotlin; `strings.xml` hanya 2 entri. | Pindahkan ke `strings.xml` hanya bila ada rencana multi-bahasa. **Keputusan pemilik (26 Okt 2026): belum ada rencana multi-bahasa, jadi BL-11 ditunda; jangan dikerjakan.**|
| BL-12 ✅ | **Selesai (1.22):** kolom `recoveryCodeHash` dihapus dari `UserEntity`, `BackupSnapshotCodec` (tulis dan baca), `forExport`, dan `BackupSnapshotValidator`; skema Room naik ke **v3** dengan `MIGRATION_2_3` (tabel `users` dibuat ulang: `users_new` → salin → `DROP` → `RENAME` → indeks `index_users_nim`/`index_users_email`; nomor `AUTOINCREMENT` tidak mundur). `ALL_MIGRATIONS` kini dipakai `TemanKuliahDatabase`. Cadangan lama yang memuat kunci `recoveryCodeHash` tetap terbaca (kunci diabaikan). Sebelumnya: pembersihan data contoh lama dihapus di 1.16; konversi akun Google → lokal dan `AUTH_GOOGLE` dihapus di 1.17. Pemilik meminta penyelesaian pada 26 Okt 2026. | Selesai. | Kolom `recoveryCodeHash` hilang dengan migrasi yang teruji (`Migration2To3Test`). **Belum dikompilasi dan test belum dijalankan di lingkungan agent. `app/schemas/.../3.json` belum ada: dihasilkan Room saat build pertama, lalu wajib di-commit (`MigrationGuardTest` gagal tanpanya).** |

## 9. Persyaratan non-fungsional dan quality gate

Semua perintah harus lulus sebelum pekerjaan dianggap selesai (sama dengan CI di `.github/workflows/android-ci.yml`):

```bash
./gradlew :app:lintDebug            # warningsAsErrors, UnusedResources/UnusedIds aktif
./gradlew :app:detekt               # terhadap detekt-baseline.xml
./gradlew :buildHealth              # dependency analysis
./gradlew :app:testDebugUnitTest    # ± 330 test (JUnit + Robolectric)
./gradlew :app:assembleDebug
```

- **Keandalan pengingat:** alarm tepat waktu (`SCHEDULE_EXACT_ALARM`; di Android 14+ izin ini default-nya ditolak, jadi UI harus mengarahkan pengguna ke pengaturan), `POST_NOTIFICATIONS` (Android 13+), jadwal ulang setelah reboot, `TIME_SET`, `TIMEZONE_CHANGED`, dan pembaruan aplikasi.
- **Keamanan:** sandi di-hash PBKDF2, percobaan masuk dibatasi, tidak ada rahasia di repo, `allowBackup=false`, `FileProvider` tidak diekspor.
- **Performa:** OCR dan impor berjalan di luar main thread; batalkan coroutine dengan benar (`rethrowIfCancellation`).
- **Aksesibilitas/UX:** dukung ukuran layar adaptif (ponsel, tablet), hormati "reduced motion", kontras tema diuji (`ThemeColorContrastTest`).
- **Rilis:** build release memakai minify + shrink resources dan keystore dari environment; Gradle memberi peringatan bila `KEYSTORE_PATH` kosong (hasil tidak bertanda tangan). CI membangun varian release tanpa tanda tangan hanya untuk memeriksa R8 (BL-14); APK rilis bertanda tangan tetap dibuat manual dengan keystore.

## 10. Definisi selesai (DoD) untuk setiap tugas

1. Perubahan sesuai permintaan dan tidak menyentuh area lain.
2. Test baru/diperbarui ada dan **sudah dijalankan**; hasilnya dilaporkan apa adanya.
3. Seluruh quality gate bagian 9 lulus (atau dijelaskan jelas mengapa tidak bisa dijalankan di lingkungan agent).
4. Tidak ada entri baru di baseline detekt/lint.
5. Bila skema berubah: versi naik, migrasi + file skema + uji migrasi ada.
6. Dokumen ini diperbarui bila fitur, aturan, atau backlog berubah.

## 11. Format laporan agent

Setiap selesai bekerja, laporkan singkat:
- **Yang diubah:** daftar file dan alasan.
- **Yang sengaja tidak diubah.**
- **Hasil test/lint/detekt:** perintah, hasil, jumlah.
- **Risiko atau hal yang belum terverifikasi.**
- **Pertanyaan untuk pemilik** (bila ada).

## 12. Pertanyaan terbuka untuk pemilik

1. ~~Apakah aplikasi 100% offline (BL-03)?~~ **Dijawab:** tidak, masih butuh internet; permission jaringan dipertahankan. **Masih terbuka:** fitur apa yang memakai internet (saat ini belum ada kode jaringan) dan data apa yang boleh keluar dari perangkat? Jawabannya menentukan revisi prinsip "offline-first dan privat" di bagian 1.
2. ~~Apakah sudah ada pengguna nyata atau cadangan lama yang harus tetap terbaca (BL-12)?~~ **Dijawab (26 Okt 2026):** tidak memakai akun Google ataupun Drive, jadi kode Google dihapus. Kolom `recoveryCodeHash` dihapus dengan migrasi `2 → 3` (1.22).
3. Bagaimana nasib pengguna yang lupa sandi? Kode pemulihan sudah dihapus dari kode. Apakah jalur pemulihan (mis. lewat cadangan lokal) perlu dirancang?
4. Rencana rilis: Play Store atau APK langsung? Menentukan kebutuhan signing, target SDK, dan kebijakan izin alarm tepat waktu.
5. ~~Apakah multi-bahasa direncanakan (BL-11)?~~ **Dijawab:** belum ada rencana multi-bahasa (26 Okt 2026); BL-11 ditunda. Tinjau ulang bila rencana berubah.
6. Urutan prioritas fitur berikutnya (mis. redesain halaman Profil/Tampilan/Jadwal yang sedang berjalan): isi di sini agar agent tahu mana yang aktif.

## 13. Riwayat revisi

| Versi | Tanggal | Perubahan |
|---|---|---|
| 1.0 | 2026-10-25 | Draf awal dari audit statis kode. |
| 1.1 | 2026-10-25 | Tahap 1 dikerjakan: BL-01, BL-02, BL-04 selesai; peringatan release tanpa keystore ditambahkan; backlog baru BL-13 s/d BL-15. Belum dikompilasi dan test belum dijalankan (tanpa Android SDK di lingkungan agent). |
| 1.2 | 2026-10-25 | BL-03 diputuskan: permission jaringan dipertahankan (🔒). Pertanyaan terbuka 1 diperbarui. Tidak ada perubahan kode. |
| 1.3 | 2026-10-25 | Tahap 3 (BL-06 sebagian): baseline lint 82 → 62 entri; 9 autoboxing diperbaiki di 8 file UI. Belum dikompilasi dan lint/test belum dijalankan. |
| 1.4 | 2026-10-25 | Tiga fitur dihapus atas permintaan pemilik: Pemindai OCR mandiri (`ui/academic/scanner/`), Kalkulator Nilai (`ui/academic/grades/`), Mode Fokus Belajar (`ui/focus/`), beserta pintasan/rute navigasinya, `TableRowDivider` yang jadi yatim, dan entri baseline terkait (detekt −215, lint −14). Permission `VIBRATE` tetap (dipakai pengingat). Belum dikompilasi; test belum dijalankan. |
| 1.5 | 2026-10-26 | Kode yatim dihapus: file `ui/components/TkFab.kt`; komponen `TkCornerTime`, `TkHeaderIconButton`, `TkLargeTitleHeader`, `TkListDivider`, `TkListRow`, `TkPageHeader`, `TkFilterChipRow`, `TkSegmentedSwitch`, `TkStatTile`, `ProfileInfoItem`, `ProfileInfoDivider`, `BehaviorCard`; 9 warna di `AuthColors.kt`, `TkSky`, `TkSkySoft`, `sheetTop`, `timeCorner`, `DashSkyLight`, ikon `GridSquare`, `formatDayMonth`, `fmt`, `MIN_RECURRING_MEETINGS`; import yang jadi tidak terpakai; 15 entri baseline detekt. Preview Compose dan kode legacy BL-12 sengaja dibiarkan. Belum dikompilasi; test belum dijalankan. |
| 1.6 | 2026-10-26 | BL-05 sebagian: 98 import tak terpakai dihapus di 20 file (paket `domain`, `ui`, `ui/academic`, `ui/auth`, `ui/calendar`, `ui/components`, `ui/courses`, `ui/dashboard`, `ui/profile`, `ui/projects`, `ui/tasks`); 7 parameter tak terpakai dihapus beserta argumen pemanggilnya (`AcademicDocumentProcessor.extract`/`extractImage` (`context`), `parseKrsSchedule` (`fallbackIndex`), `PrivacySecurityScreen` (`user`), `AccountScreen` (`onOpenPersonalInfo`), `ProfileScreen` (`onOpenDocuments`), `ProjectManagementScreen` (`onBack`), `ScheduleCard` (`onDelete`)); KDoc `ScheduleCard` diperbaiki. Baseline detekt 1.946 → 1.822 (−124: 111 `UnusedImports`, 10 `UnusedParameter`, 3 `UnusedPrivate*` basi; 19 di antaranya sudah basi sebelum perubahan (kodenya sudah tidak ada atau tidak lagi bermasalah)). Tidak ada perubahan perilaku. Belum dikompilasi; detekt dan test belum dijalankan. |
| 1.7 | 2026-10-26 | BL-10 sebagian: `DashboardComponents.kt` dipecah menjadi tiga file di `ui/dashboard/` (isi baris kode identik, import dipangkas per file, tidak ada simbol `private` yang perlu dinaikkan). Entri baseline detekt ikut dipindah ke nama file baru (28 entri); entri `TooManyFunctions` untuk file lama dibuang karena tiap file kini di bawah ambang 20 fungsi (baseline 1.822 → 1.821). Belum dikompilasi; detekt dan test belum dijalankan. |
| 1.8 | 2026-10-26 | BL-15 selesai: 6 baris dependency yang di-comment dihapus dari `app/build.gradle.kts`; 11 entri tak terpakai dihapus dari `libs.versions.toml` (6 library, 5 versi). Dua entri baseline lint yang jadi basi (`GradleDependency` untuk `room-ktx` dan `core-ktx`) ikut dihapus (48 → 46); tidak ada entri baru. Tidak ada perubahan kode Kotlin atau perilaku. Belum dikompilasi; `:buildHealth` dan test belum dijalankan. |
| 1.9 | 2026-10-26 | BL-11 diputuskan ditunda atas keputusan pemilik (belum ada rencana multi-bahasa); pertanyaan terbuka 5 dijawab. Hanya dokumen yang berubah; tidak ada perubahan kode. |
| 1.10 | 2026-10-26 | BL-06 sebagian: `TkFormSheet.kt` memakai `LocalWindowInfo.current.containerSize` (bukan `LocalConfiguration.screenHeightDp`); 3 entri baseline lint dibuang (`ConfigurationScreenWidthHeight`, `StaticFieldLeak`, `InlinedApi`), 46 → 43; tidak ada entri baru. Belum dikompilasi; `lintDebug` dan test belum dijalankan. Bila `lintDebug` ternyata masih melaporkan `StaticFieldLeak` atau `InlinedApi`, kembalikan entrinya ke baseline. |
| 1.11 | 2026-10-26 | BL-14 selesai: job `build-release-r8` di `android-ci.yml`. Tidak ada perubahan kode aplikasi. YAML sudah diperiksa sintaksnya, tetapi job belum pernah dijalankan di GitHub Actions. |
| 1.12 | 2026-10-26 | Perbaikan error kompilasi dan pengujian Robolectric: (1) `TkComponents.kt`: tambah missing import `getValue`/`setValue` untuk delegasi `by animateColorAsState` di baris 317; (2) `AdaptiveScreensRenderTest.kt`: tambahkan `@Suppress("DEPRECATION")` pada `createComposeRule()` agar lolos `-Werror`; (3) `TodayWidgetDataTest.kt`: perbaiki batas `todayDue` (`nowMillis + 60_000L < endOfToday`) untuk mencegah overflow tanggal di uji tengah malam (UTC); (4) `TasksScreen.kt`: samakan kapitalisasi label tab switch menjadi `"Proyek Kuliah"`. Seluruh kompilasi dan 345 unit/Robolectric test lulus 100%. |
| 1.13 | 2026-10-26 | BL-05 kelanjutan tahap 2: Pembersihan code smell mekanis dan aman di kode Kotlin: `EmptyElseBlock` (1 di `ProjectDialogs.kt`), `ExplicitItLambdaParameter` (2 di `CalendarScreen.kt`, `TasksScreen.kt`), `ForEachOnRange` (3 di `AcademicComponents.kt`, `CourseScheduleScreen.kt`, `RegisterComponents.kt`), `SpreadOperator` (1 di `TemanKuliahDatabase.kt`), `UseCheckOrError` (3 di `BackupController.kt`), `UseRequire` (2 di `CourseRepository.kt`), `TopLevelPropertyNaming` (2 di `CalendarScreen.kt`, `SplashScreen.kt`), dan 4 `UnusedImports` mati. Sebanyak 14 entri baseline detekt dibuang (`detekt-baseline.xml` turun dari 1.821 → 1.807 entri). Kompilasi dan seluruh 345 test lulus 100%. |
| 1.14 | 2026-10-26 | BL-05 selesai untuk detekt: `detekt.yml` disesuaikan (FunctionNaming Composable, MaxLineLength 160, MagicNumber, batas kompleksitas untuk Composable); konstanta/refaktor mekanis di `ReminderScheduler`, `ReminderSelfTest`, `ReminderDiagnostics`, `RegisterComponents`, `NotificationFeed` (`FeedBuilder`); 2 file diganti nama; 10 baris panjang dibungkus. Baseline detekt 1.807 → 461 (tanpa entri baru). Diverifikasi dengan detekt-cli 1.23.8 (0 temuan) dan pemeriksaan sintaks `kotlinc`; **belum dikompilasi penuh, lint dan test belum dijalankan**. |
| 1.15 | 2026-10-26 | BL-10 selesai: `AcademicDocumentProcessor.kt` dan `LocalDataArchive.kt` dipecah per tanggung jawab (rincian dan daftar helper `internal` di baris BL-10). API publik dan perilaku tidak berubah. Baseline detekt 461 → 473 (entri dipindah/dipecah per berkas, bukan temuan baru); 5 entri baseline lint dipindah ke berkas baru. **Belum dikompilasi; detekt (Gradle), lint, dan test belum dijalankan.** |
| 1.16 | 2026-10-26 | BL-12 sebagian: `removeLegacySampleTaskProjectData` dan collector pemicunya di `init` `TemanKuliahViewModel` dihapus, beserta 4 import yang jadi tidak terpakai. Konversi Google → lokal, `AUTH_GOOGLE`, dan `recoveryCodeHash` sengaja dibiarkan (terkait restore dan alur "Buat sandi"; kolom butuh migrasi). `OneTimeTaskGate` dan testnya dibiarkan. Tidak ada perubahan skema atau baseline. **Belum dikompilasi; detekt, lint, dan test belum dijalankan.** |
| 1.17 | 2026-10-26 | BL-12 sebagian lagi: konversi akun Google → lokal di `UserRepository.upgradeLegacyPasswords` dihapus (hashing sandi teks biasa tetap); konstanta `AUTH_GOOGLE` dihapus; validator cadangan hanya menerima `guest` dan `local`; cabang restore yang memaksa `authType = google` dihapus (`authType` dipertahankan apa adanya). Test disesuaikan di `AuthSecurityRepositoryTest`, `AdaptiveScreensRenderTest`, `LocalDataArchiveHashTest`, `LocalDataArchiveTest`; 4 kunci baseline detekt diperbarui mengikuti teks kode (jumlah entri tetap). `recoveryCodeHash` dan skema Room tidak diubah. **Belum dikompilasi; detekt, lint, dan test belum dijalankan.** |
| 1.18 | 2026-10-26 | BL-09 selesai: rute bertipe (`ui/Navigation.kt`), `TemanKuliahApp.kt`, `AcademicDashboardScreen.kt`, `ProfileScreen.kt` disesuaikan; `NavigationTest` baru. Baris sub-halaman Profil dan reset tab dibungkus agar tidak melewati 160 karakter; 7 entri `MaxLineLength` `TemanKuliahApp.kt` di `detekt-baseline.xml` dihapus karena barisnya sudah tidak ada (`"about"`, `"account"`, `"appearance"`, `"notifications"`, `"personal"`, `"privacy"`, `BackHandler`); tidak ada entri baru. **Belum dikompilasi; detekt, lint, dan test belum dijalankan.** |
| 1.19 | 2026-10-26 | BL-16 selesai: Penguatan layout responsif lintas layar (320dp s/d tablet >=600dp) dan skala font (0.85x s/d 2.0x). Perbaikan komponen UI: `TkEmptyState` adaptif `BoxWithConstraints` (bungkus tombol ke bawah saat sempit), `TkSegmentedControl` min 44dp, dialog `TkAlertDialog` max 420dp & scrollable, bottom nav & rail `heightIn`, `TkFormSheet` max 640dp terpusat, `TkFullScreenDialog` max 720dp & header 2 baris ellipsis, `TkStatTile` multiline. Dashboard: breakpoint tablet 600dp, GPA card adaptif `BoxWithConstraints` (32/38/45sp) tanpa manual offset (`Alignment.CenterEnd`), timeline indicator & stats panel `heightIn(min = ...)`. Layar Auth: field & tombol `heightIn(min = 52.dp)` tanpa offset, scroll container aman. Profil: hilangkan offset badge kamera, subtitle multiline. Kalender: MonthGrid fleksibel, time column fleksibel. Tugas & Dialog: AddTask scrollable tanpa batasan kaku. Tabel akademik: sel angka adaptif. Tambah suite uji deteksi otomatis `ResponsiveLayoutLintTest` dan pratinjau `ResponsivePreviews.kt`. Pembersihan kode: hapus unused import (`ProfileScreen`, `LoginComponents`, `DashboardGpaCard`), perbaiki KTX lint extension (`NotificationController`, `TemanKuliahApp`), dan hapus unused `BoxWithConstraints` (`CalendarScreen`, `DashboardScreen`, `TasksScreen`). Seluruh 5 quality gate dijalankan dan lulus 100% (`:app:lintDebug` 0 new issues, `:app:detekt` 0 violations, `:buildHealth`, `:app:testDebugUnitTest` 358 tests, `:app:assembleDebug`). |
| 1.20 | 2026-10-26 | Refaktor Form Sheet KRS/KHS, Kalender Kegiatan, Date Picker & Navigasi: (1) Form KRS (`AddCourseDialog`) dan KHS (`EditCourseGradeDialog`) diubah ke `TkFormSheet` layar penuh seragam dengan Tambah Tugas dan Jadwal (header tetap dengan tombol tutup bundar, bodi formulir dapat digulir, tombol aksi tetap di bawah `OutlinedButton` Batal & `Button` Simpan); (2) Kalender mendukung penambahan & pemfilteran Kegiatan Kampus (`CalendarAddOptionBottomSheet`, `AcademicEventEditorDialog` chip Kegiatan/Seminar/Ujian, filter chip Kegiatan); (3) `TkDatePickerDialog` diselaraskan dengan palet tema TemanKuliah (biru, surface putih, teks bahasa Indonesia, tombol Batal & Pilih Tanggal bertema); (4) Ikon menu navigasi Tugas diganti dari clipboard menjadi buku (`TkOutlineIcons.Book`) dan Akademik menjadi toga (`TkOutlineIcons.School`). Seluruh 357 unit/Robolectric test lulus 100%. |
| 1.21 | 2026-10-26 | BL-07 selesai: Uji migrasi Room v1 → v2 (`Migration1To2Test.kt`, Robolectric) membuktikan data v1 selamat saat migrasi ke v2 tanpa kehilangan data. Memvalidasi rekonstruksi skema v1 dari `schemas/.../1.json`, eksekusi `MIGRATION_1_2` via Room runtime, penautan otomatis KRS berdasarkan kesamaan nama mata kuliah (case-insensitive & whitespace trimmed) per user dan semester, penanganan multi jadwal/sesi, isolasi jadwal antar-pengguna, keutuhan seluruh atribut jadwal dan KRS lama, serta kepatuhan hash identitas runtime Room dan pembacaan via `ScheduleDao`. Seluruh 359 unit & Robolectric test lulus 100%. |
| 1.22 | 2026-10-26 | BL-12 selesai: `recoveryCodeHash` dihapus dari entitas, kodek, ekspor, dan validator cadangan; `DATABASE_SCHEMA_VERSION` 2 → 3; `MIGRATION_2_3` (tabel `users` dibuat ulang) ditambahkan ke `ALL_MIGRATIONS`, yang kini dipakai `TemanKuliahDatabase`; import `LocalPasswordHasher` yang jadi tidak terpakai di `BackupSnapshotValidator` dihapus. Test: `Migration2To3Test` baru; `Migration1To2Test` kini memasang rantai `MIGRATION_1_2` + `MIGRATION_2_3` dan menegaskan versi 3; `LocalDataArchiveTest` tidak lagi memakai `recoveryCodeHash`. Tidak ada entri baseline baru. **Belum dikompilasi; `3.json` harus dihasilkan build lalu di-commit; detekt, lint, dan test belum dijalankan.** |
| 1.23 | 2026-10-26 | BL-06 sebagian besar: baseline lint 43 → 15. Kode: `SharedPreferences.edit {}` (PreferencesController, BackupController, OneTimeTaskGate, TemanKuliahApp, blok restore di LocalDataArchive), `String.toUri()` (ProfileSettingsScreens, ScheduleCard), `Int.toDrawable()` (SplashScreen), `createBitmap`/`Bitmap.scale` dan `androidx.exifinterface.media.ExifInterface` (AcademicOcrReader); import `android.net.Uri`/`ColorDrawable` yang jadi tidak terpakai dibuang; satu baris >160 karakter di ProfileSettingsScreens dibungkus. Manifest: `android:dataExtractionRules` + `data_extraction_rules.xml`. Widget: `tools:targetApi="31"`. Dependency baru `core-ktx` 1.17.0 dan `exifinterface` 1.4.1 (versi belum diverifikasi; tanpa jaringan). Entri `detekt-baseline.xml` untuk baris yang berubah tidak disentuh (bisa basi; tidak ada entri baru yang ditambahkan). **Belum dikompilasi; `:buildHealth`, detekt, lint, dan test belum dijalankan.** |
