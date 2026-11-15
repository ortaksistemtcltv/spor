# OSPlayer SporB · CloudStream

Android CloudStream eklentisi. Türkçe canlı yayın araması ve dört kaynak altında toplam 225 benzersiz yayın adresi içerir. Yayınların mevcut erişimi ve cihazdaki oynatma sonuçları doğrulanmadı.

## GitHub’da yayımlama

1. GitHub’da herkese açık bir depo aç. Bu ZIP’in içindeki dosyaları depo köküne yükle. `.github/workflows/build.yml` dahil olmalı.
2. **Actions → CloudStream eklentisini yayımla** çalışmasının tamamlanmasını bekle. Gerekirse **Run workflow** ile ilk çalıştırmayı başlat. İş akışı `builds` dalını oluşturur, `repo.json`, `plugins.json` ve `SporBProvider.cs3` dosyalarını yayımlar.
3. CloudStream’de **Ayarlar → Eklentiler → Depo Ekle** bölümünde iş akışı özetinde gösterilen adresi kullan. Şablon `https://raw.githubusercontent.com/KULLANICI/DEPO/builds/repo.json`.
4. **OSPlayer SporB** eklentisini indir. Sağlayıcılarda seç veya arama ekranını kullan.

## Bakım

Kanal verileri `data/channels.json` dosyasındadır. Eklenti her derlemede bu veriden kataloğu üretir. Kaynak güncellemesinde eklenti sürüm numarasını `SporBProvider/build.gradle.kts` içinde artır.

Yerel çalıştırmada JDK 17 ve Android SDK 35 gerekir. Depo kökünden `python3 scripts/generate_catalogue.py` ve `bash gradlew make makePluginsJson` çalıştır. `GITHUB_REPOSITORY` ortam değişkeni yerel çalıştırmada isteğe bağlıdır.

Derleme yapısı için upstream örnekler. https://github.com/recloudstream/extensions
