# 2S Launcher

> [2S Launcher, 2 Bölmeden Oluşan, Nintendo DS’den esinlenen, Akıcı, Göze Hitab eden, Açık kaynak bir Android Başlatıcısıdır. Amaç İnsanların işine odaklanmasını sağlamaktır. Bu doğrultuda öncelik Insanların odaklanmasını sağlamak ve Güzel bir arayüz sunmaktır."]

![Lisans](https://img.shields.io/badge/lisans-[LİSANS]-blue)
![Platform](https://img.shields.io/badge/platform-[Android%20]-lightgrey)

## ✨ Özellikler

- [Odaklanmayı Sağlayan 2 Bölmeli Tasarım.]
- [Temiz Arayüz.]
- [Minimalist Tasarım.]
- [Uygulama Gizleme.]

## 📥 Kurulum

### Hazır sürüm (önerilen)

1. [Releases](https://github.com/BleuAxolotlTR/2S-Launcher/releases) sayfasına gidin.
2. 2S-Launcher.apk Dosyasını Indirin.
3. Dosyayı çalıştırın ve kurulum adımlarını Tamamlayın.

### Kaynaktan derleme

## 1. Depoyu klonlayın

```bash
git clone https://github.com/BleuAxolotlTR/2S-Launcher.git
cd 2S-Launcher
```

## 2. Android Studio ile derleme

1. Android Studio'yu açın ve **File → Open** ile proje klasörünü seçin.
2. Gradle senkronizasyonunun bitmesini bekleyin. Eksik SDK bileşenleri için Android Studio indirme önerecektir.
3. Bir cihaz veya emülatör bağlayıp **Run ▶** butonuna basın.

### 3. Komut satırı ile derleme

**Linux / macOS:**

```bash
chmod +x gradlew
./gradlew assembleDebug
```

**Windows:**

```bat
gradlew.bat assembleDebug
```

Derlenen APK şu konumda oluşur:

```
app/build/outputs/apk/debug/app-debug.apk
```

### 4. Release (imzalı) APK oluşturma

1. Bir anahtar deposu (keystore) oluşturun:

```bash
   keytool -genkey -v -keystore 2s-launcher.jks -keyalg RSA -keysize 2048 -validity 10000 -alias 2s
```

2. `app/build.gradle` içinde `signingConfigs` bölümünü kendi keystore bilgilerinizle yapılandırın. Şifreleri depoya **eklemeyin**; `local.properties` veya ortam değişkenleri kullanın.
3. Release APK'yı derleyin:

```bash
   ./gradlew assembleRelease
```

Çıktı: `app/build/outputs/apk/release/app-release.apk`


## 🚀 Kullanım

1. Launcher'ı açın.
2. Ve Kullanmaya Başlayın.

## 🤝 Katkıda Bulunma

Katkılar memnuniyetle karşılanır!

1. Depoyu fork'layın
2. Yeni bir dal oluşturun (`git checkout -b ozellik/yeni-ozellik`)
3. Değişikliklerinizi commit'leyin (`git commit -m "Yeni özellik eklendi"`)
4. Dalınızı push'layın (`git push origin ozellik/yeni-ozellik`)
5. Pull Request açın

Hata bildirimleri ve öneriler için [Issues](https://github.com/BleuAxolotlTR/2S-Launcher/issues) sayfasını kullanabilirsiniz.

## 📄 Lisans

Bu proje GNU GENERAL PUBLIC LICENSE lisansı altında dağıtılmaktadır. Ayrıntılar için `LICENSE` dosyasına bakın.

## 👤 Geliştirici

**BleuAxolotlTR** — [GitHub](https://github.com/BleuAxolotlTR)
