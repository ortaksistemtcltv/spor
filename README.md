# SPOR · CloudStream

Türkçe spor yayın kataloğu. Eklenti, dört kaynak altında 225 benzersiz yayın adresi içerir.

1. CloudStream’de **Ayarlar → Eklentiler → Depo Ekle** bölümünü aç.
2. [Depo adresini](https://raw.githubusercontent.com/ortaksistemtcltv/spor/main/repo.json) ekle.
3. **SPOR** eklentisini kur veya güncelle. Sağlayıcı adı **SPOR KANALI** olarak görünür.
4. Önce **Yayıncı Kaynakları** bölümünden TRT Spor, TRT Spor Yıldız veya A Spor’u dene. Sol menüdeki dört katalog **SPOR 1–4** olarak görünür. Bir adres hata verirse, varsa aynı kanalın diğer kaynağını oynatıcının kaynak menüsünden seç.

```text
https://raw.githubusercontent.com/ortaksistemtcltv/spor/main/repo.json
```

[Depo manifesti](./repo.json) · [Eklenti dizini](https://raw.githubusercontent.com/ortaksistemtcltv/spor/builds/plugins.json)

Kaynaklar `main` dalında, derlenen eklenti `builds` dalındadır. Kanal verileri `data/channels.json` dosyasındadır. Yayın erişimi hizmet sağlayıcısına göre değişebilir.
