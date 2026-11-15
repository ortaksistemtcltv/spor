# SPOR · CloudStream

Türkçe spor yayın kataloğu. Eklenti, dört kaynak altında 225 benzersiz yayın adresi içerir.

1. CloudStream’de **Ayarlar → Eklentiler → Depo Ekle** bölümünü aç.
2. [Depo adresini](https://raw.githubusercontent.com/ortaksistemtcltv/spor/main/repo.json) ekle.
3. **SPOR** eklentisini kur veya güncelle. Sağlayıcı adı **SPOR KANALI** olarak görünür.
4. Önce **Yayıncı Kaynakları** bölümünden TRT Spor, TRT Spor Yıldız veya A Spor’u dene. Sol menüde **SPOR 1–4** bulunur. Bir adres hata verirse, varsa aynı kanalın diğer kaynağını oynatıcının kaynak menüsünden seç.

```text
https://raw.githubusercontent.com/ortaksistemtcltv/spor/main/repo.json
```

[Depo manifesti](./repo.json) · [Eklenti dizini](https://raw.githubusercontent.com/ortaksistemtcltv/spor/builds/plugins.json)

Kaynaklar `main` dalında, derlenen eklenti `builds` dalındadır. Kanal verileri `data/channels.json` dosyasındadır. Yayın erişimi hizmet sağlayıcısına göre değişebilir.

## SPOR 5 · Patron İzle

[Patron İzle 7/24 kanal kataloğu](data/spor5-patronizle-kanallar.json) 39 kayıt ve 36 benzersiz kanal sayfası içerir. JSON içindeki `channelPage` alanı web oynatıcısının adresidir. Doğrudan Patron İzle akışları doğrulanana kadar bu liste web kanal kataloğu olarak kullanılabilir.

## SPOR 6 · Selçuk Sports

[Selçuk Sports 7/24 TV kanal kataloğu](data/spor6-selcuksports-kanallar.json) 41 kayıt içerir. 38 kayıtta web oynatıcısı adresi vardır. Sky Sports F1, Smart Spor 2 ve TRT Spor 2 için site bir oynatıcı adresi sunmadığından `channelPage` alanı boştur. Doğrudan Selçuk Sports akışları doğrulanana kadar bu liste web kanal kataloğu olarak kullanılabilir.
