# OSPlayer SporB · CloudStream

Türkçe spor yayın kataloğu. Eklenti, dört kaynak altında 225 benzersiz yayın adresi içerir.

1. CloudStream’de **Ayarlar → Eklentiler → Depo Ekle** bölümünü aç.
2. [Depo adresini](https://raw.githubusercontent.com/ortaksistemtcltv/spor/main/repo.json) ekle.
3. `SporBProvider` eklentisini kur veya güncelle. Ana sayfada sağlayıcı adı **OSPlayer SporB** olarak görünür.
4. Önce **Yayıncı Kaynakları** bölümünden TRT Spor, TRT Spor Yıldız veya A Spor’u dene. Eski kanal adreslerinin erişimi kaynak sunuculara bağlıdır.

```text
https://raw.githubusercontent.com/ortaksistemtcltv/spor/main/repo.json
```

[Depo manifesti](./repo.json) · [Eklenti dizini](https://raw.githubusercontent.com/ortaksistemtcltv/spor/builds/plugins.json)

Kaynaklar `main` dalında, derlenen eklenti `builds` dalındadır. Kanal verileri `data/channels.json` dosyasındadır. Yayın erişimi hizmet sağlayıcısına göre değişebilir.
