# 2S Launcher

![Lisans](https://img.shields.io/badge/Lisans-GPL-blue.svg)
![Platform](https://img.shields.io/badge/Platform-Android-lightgrey.svg)

2S Launcher; Nintendo DS'ten ilham alan çift bölmeli (2-pane) tasarımıyla öne çıkan, akıcı, göze hitap eden ve açık kaynaklı bir Android başlatıcısıdır. Temel amacı, kullanıcıların dijital kalabalıktan uzaklaşıp asıl işlerine odaklanmalarını sağlamak ve bunu yaparken minimalist, estetik bir arayüz sunmaktır.

## ✨ Özellikler

- 🎯 **Odak Odaklı 2 Bölmeli Tasarım:** Verimliliği artıran, özgün ekran yerleşimi.
- 🧼 **Temiz ve Minimalist Arayüz:** Göz yormayan, sade bir kullanıcı deneyimi.
- 🔒 **Uygulama Gizleme:** Dikkatinizi dağıtan veya kullanmadığınız uygulamaları gizleme imkanı.
- ⚡ **Akıcı Performans:** Hızlı ve pürüzsüz geçişler.

## 📥 Kurulum

### 📦 Hazır Sürüm (Önerilen)

1. [Releases](https://github.com/BleuAxolotlTR/2S-Launcher/releases) sayfasına gidin.
2. En güncel `2S-Launcher.apk` dosyasını indirin.
3. Cihazınıza aktarın, dosyayı çalıştırın ve kurulum adımlarını tamamlayın.

---

### 🛠️ Kaynaktan Derleme

Eğer projeyi kendiniz derlemek veya geliştirmek isterseniz aşağıdaki adımları izleyebilirsiniz:

#### 1. Depoyu Klonlayın
```bash
git clone [https://github.com/BleuAxolotlTR/2S-Launcher.git](https://github.com/BleuAxolotlTR/2S-Launcher.git)
cd 2S-Launcher
```

#### 2. Android Studio ile Derleme
1. Android Studio'yu açın ve **File → Open** seçeneği ile proje klasörünü seçin.
2. Gradle senkronizasyonunun bitmesini bekleyin. *(Eksik SDK bileşenleri için Android Studio indirme önerecektir).*
3. Bir cihaz veya emülatör bağlayıp **Run ▶** butonuna basın.

#### 3. Komut Satırı (CLI) ile Derleme

**Linux / macOS:**
```bash
chmod +x gradlew
./gradlew assembleDebug
```

**Windows:**
```bat
gradlew.bat assembleDebug
```
*Derlenen APK şu konumda oluşur:* `app/build/outputs/apk/debug/app-debug.apk`

#### 4. Release (İmzalı) APK Oluşturma
1. Bir anahtar deposu (keystore) oluşturun:
```bash
keytool -genkey -v -keystore 2s-launcher.jks -keyalg RSA -keysize 2048 -validity 10000 -alias 2s
```
2. `app/build.gradle` içindeki `signingConfigs` bölümünü kendi keystore bilgilerinizle yapılandırın. **Önemli:** Şifreleri GitHub'a yüklemeyin; bunun yerine `local.properties` veya ortam değişkenleri (environment variables) kullanın.
3. Release APK'yı derleyin:
```bash
./gradlew assembleRelease
```
*Çıktı konumu:* `app/build/outputs/apk/release/app-release.apk`

## 🚀 Kullanım

1. Cihazınızda 2S Launcher'ı başlatın.
2. İstenen temel izinleri onaylayın.
3. Ana ekran tuşuna basıp 2S Launcher'ı **Varsayılan Başlatıcı (Default Launcher)** olarak ayarlayarak odaklanmaya hemen başlayın!

## 🤝 Katkıda Bulunma

Geliştirmelere ve katkılara her zaman açığız! Projeye destek olmak için:

1. Bu depoyu fork'layın.
2. Yeni bir dal (branch) oluşturun: `git checkout -b ozellik/yeni-ozellik`
3. Değişikliklerinizi commit'leyin: `git commit -m "Yeni özellik eklendi"`
4. Dalınızı push'layın: `git push origin ozellik/yeni-ozellik`
5. Bir **Pull Request** açın.

Hata bildirimleri, geri bildirimler ve yeni fikirler için lütfen [Issues](https://github.com/BleuAxolotlTR/2S-Launcher/issues) sayfasını kullanın.

## 📄 Lisans

Bu proje **GNU General Public License (GPL)** altında dağıtılmaktadır. Daha fazla detay ve yasal haklar için `LICENSE` dosyasına göz atabilirsiniz.

## 👤 Geliştirici

**BleuAxolotlTR** — [GitHub Profili](https://github.com/BleuAxolotlTR)
