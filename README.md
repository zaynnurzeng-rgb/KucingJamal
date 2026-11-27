# 🐱 Kucing Jamal — Virtual Pet AI & Asisten PR Tsundere

**Kucing Jamal** adalah aplikasi *virtual pet* Android berbasis **Kotlin & Jetpack Compose** yang memadukan keseruan game kucing interaktif klasik dengan kecerdasan **Gemini AI**. 

Jamal punya kepribadian **tsundere**—ketus, jutek, pemarah, dan suka ngomel kalau diganggu—tapi diam-diam peduli dan selalu membantu menjawab obrolan maupun mengerjakan soal PR dengan jelas.

---

## ✨ Fitur Utama

### 🐾 Virtual Pet Interaktif & Gaya Game UI
* **Splash Loading Screen (0%–100%)** dan ikon aplikasi kustom **JAMAL**.
* **UI Kasual 3D:** Top HUD hijau limau, bar status bergaris miring, tombol bulat *bevel* 3D krem, dan panel dialog berkepala *teal*.
* **Sistem Mood & Kenyang (*Real-time*):** Status Mood dan Kenyang tersimpan permanen, berkurang perlahan seiring waktu, dan langsung memengaruhi sikap AI Jamal saat menjawab chat.
* **Zona Sentuh, Elus & *Idle* Otomatis:**
  * Ketuk kepala, perut, kaki kiri, atau kaki kanan untuk reaksi dan efek suara berbeda.
  * **Usap (*swipe*)** kepala atau perut Jamal untuk mengelusnya hingga mendengkur.
  * Jika didiamkan lebih dari 20 detik, Jamal otomatis menguap atau bersin.
* **6 Tombol Aksi Klasik:** Makan, Minum Susu, Lempar Pai, Simbal, Kentut, dan Cakar lengkap dengan animasi (12–24 FPS) serta efek suara asli.

### 📚 Asisten AI, Tutor PR & Memori Jangka Panjang
* **Foto PR (Kamera Langsung & Galeri):** Ambil foto soal langsung dari kamera atau galeri, dilengkapi pratinjau foto (auto-rotasi EXIF) dan kolom catatan tambahan sebelum dikirim.
* **Mode Tutor PR & Rich Text:** Jawaban PR tampil rapi dengan format teks **tebal** dan poin daftar, ditambah tombol cepat **Jelaskan Lebih Simpel** dan **Bikin 1 Soal Latihan**.
* **Riwayat Chat Permanen:** Seluruh obrolan tersimpan lokal di perangkat, lengkap dengan tombol **Putar Suara** di tiap balasan dan tombol **Hapus Riwayat**.
* **Memori Jangka Panjang (🧠):** Jamal otomatis mencatat fakta penting tentang kamu (nama, hobi, pelajaran favorit) secara lokal dan tetap mengingatnya meski riwayat chat dibersihkan.

### 🔊 Suara Native AI & Mode Tirukan Offline
* **Suara Native Gemini TTS:** Setiap dialog *bubble* dan jawaban AI diucapkan secara natural dengan 5 pilihan karakter suara (`Puck`, `Fenrir`, `Charon`, `Kore`, `Aoede`) serta tombol **Stop Bicara**.
* **Cache Suara Permanen:** Audio PCM hasil TTS disimpan lokal di HP agar kalimat yang berulang tidak menguras kuota API, lengkap dengan info ukuran dan tombol **Bersihkan Cache** di menu *Option*.
* **Mode Tirukan Suara Klasik (100% Offline):** Rekam suaramu lewat mikrofon dan dengarkan Jamal menirukannya dengan *pitch* tinggi ala kucing klasik tanpa koneksi internet dan nol kuota API.

### 🛡️ Arsitektur Anti-Limit (Fallback Chain & Multi API Key)
* **Rantai Model Otomatis:** Jika satu model padat (`HTTP 503`) atau kena batas (`HTTP 429`), aplikasi otomatis berpindah ke cadangan berikutnya:
  `gemini-4-hydrogen (penalaran LOW)` → `gemini-3.5-flash-lite` → `gemini-3.6-flash` → `gemma-4-31b-a4b-it`
* **Rotasi Multi API Key:** Mendukung banyak API Key dari Project/akun berbeda dengan pelacakan aktif terpisah untuk **Chat** dan **Suara (TTS)**.

---

## 🛠️ Teknologi yang Digunakan

| Komponen | Teknologi |
| :--- | :--- |
| **Bahasa & UI** | Kotlin, Jetpack Compose (Material 3) |
| **Networking** | OkHttp 4, Gemini REST API (`v1beta`) |
| **Audio & Suara** | `AudioTrack` (PCM 24kHz), `AudioRecord` (Pitch Shift Offline), `SoundPool`, Android `SpeechRecognizer` |
| **Penyimpanan Lokal** | JSON Local Storage (`filesDir`) & `SharedPreferences` |
| **Build & CI/CD** | Gradle 8.9, R8/ProGuard, GitHub Actions (Auto-Signed Release APK) |

---

## 📂 Struktur Utama Proyek

```text
app/src/main/java/com/jamal/kucingjamal/
├── MainActivity.kt          # Layar utama, engine animasi, gestur & panel chat
├── SplashScreen.kt          # Splash loading screen dengan progress bar 0-100%
├── GameUiComponents.kt      # Komponen UI game 3D, striped bar & parser rich text
├── GeminiClient.kt          # Client Gemini API, fallback chain, TTS cache & rotasi key
├── VoiceMimicEngine.kt      # Engine rekam suara & pitch-shift 100% offline
├── PetStateStorage.kt       # Penyimpanan & decay status Mood dan Kenyang
├── MemoryStorage.kt         # Memori jangka panjang otomatis tentang pengguna
├── ChatStorage.kt           # Penyimpanan riwayat chat permanen
└── AppSettingsStorage.kt    # Pengaturan karakter suara, pitch, FPS & cache suara
```

---

## 🚀 Konfigurasi Build (GitHub Actions)

Proyek ini otomatis membangun **Release APK** setiap kali ada *push* ke branch `main` (atau lewat *Run workflow* manual di tab **Actions**).

### 1. Secrets API Key (Wajib minimal 1)
Buka **Settings → Secrets and variables → Actions** di repositori GitHub, lalu tambahkan:
* `GEMINI_API_KEY` — API Key utama dari Google AI Studio.
* `GEMINI_API_KEY_2` *(opsional)* — API Key cadangan dari Project/akun berbeda untuk rotasi otomatis.
* `GEMINI_API_KEY_3` *(opsional)* — API Key cadangan ke-3.

> Kamu juga bisa menambah atau mengganti beberapa API Key langsung dari dalam aplikasi lewat tombol **⚙️ Option** (1 key per baris).

### 2. Secrets Keystore Release *(Opsional)*
Jika tidak diisi, GitHub Actions otomatis membuat *keystore* penandatangan saat *build*. Untuk tanda tangan rilis yang permanen antar-update, tambahkan:
* `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, dan `KEY_PASSWORD`.

---

## 📱 Persyaratan Perangkat
* Android 8.0 (API 26) atau lebih baru.
* Izin **Mikrofon** (untuk Ngobrol AI & Mode Tirukan) dan **Kamera** (untuk Foto PR langsung).
