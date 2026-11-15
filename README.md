# SEYİRBOX · CloudStream

Türkçe film, dizi ve canlı spor sağlayıcısı.

1. CloudStream’de **Ayarlar → Eklentiler → Depo Ekle** bölümünü açın.
2. [Ana depo adresini](https://raw.githubusercontent.com/ortaksistemtcltv/spor/main/repo.json) ekleyin.
3. **SEYİRBOX** eklentisini kurun veya mevcut **SPOR** eklentisini güncelleyin.
4. Ana ekranın sağlayıcı seçiminde **SEYİRBOX**’ı seçin.

```text
https://raw.githubusercontent.com/ortaksistemtcltv/spor/main/repo.json
```

| Ana ekran bölümü | Kaynaklar |
| --- | --- |
| FİLM | HDFİLMCEHENNEMİ, DİZİFİLMİZLE, JETFİLMİZLE, SİNEMAGG, WEBTEİZLE, RAREFILMM |
| DİZİ | DİZİBOX, DİZİFİLMİZLE, DİZİRELLA, DMAX, TLC |
| SPOR | Yayıncı Kaynakları, JUSTIN SPOR, TRGOALS, DOĞU SPOR, VAVOO SPOR |

FİLM ve DİZİ satırları kaynak sitelerin herkese açık kataloglarını yükler. Aramada, sitenin kullanılan arama sayfası sonuç vermezse ilk katalog sayfasındaki başlıklar taranır. CloudStream, sayfada bulunan doğrudan HTTPS HLS, DASH, MP4 ve WebM bağlantılarını kullanır. RareFilmm sayfalarındaki herkese açık OK.ru video oynatıcıları CloudStream’in yerleşik çözümleyicisine aktarılır. Yeni satırlardaki kataloglar doğrulandı. Oynatma her içerik için ayrı değerlendirilir.

Yeni kaynaklar [Ripplay](https://github.com/Ripplay/cloudstream-repo), [Nikyokki](https://github.com/nikyokki/nik-cloudstream) ve [TurkSinema](https://github.com/Wiojelt/TurkSinema) depolarındaki kataloglardan seçildi. SEYİRBOX, her kaynağın herkese açık HTML sayfalarını kendi ayrıştırıcısıyla okur.

[Kaynak kayıtları](data/seyirbox-sources.json) · [Eklenti dizini](https://raw.githubusercontent.com/ortaksistemtcltv/spor/builds/plugins.json)

Ana yayın derlemesi tek **SEYİRBOX** eklentisini üretir. `-PpreviewCatalogs=true` yerel seçenek olarak FİLM ve DİZİ’yi ayrıca derler. Bu ayrı paketler önizleme deposunu daha önce kurmuş cihazlarda listelenebilir.

## PATRON SPOR

[Patron İzle 7/24 kanal kataloğu](data/spor5-patronizle-kanallar.json) 39 kayıt ve 36 benzersiz kanal sayfası içerir. SeyirBox gibi bir istemci, `channelPage` adresini web oynatıcısında veya varsayılan tarayıcıda hemen açabilir. `playback.startupWaitSeconds` sayfa yüklendikten sonra gösterilebilecek 5 saniyelik tahmini bekleme süresidir. Reklamın bitişini ve oynatmayı site yönetir. Gerekirse sayfadaki **Oynat** düğmesine basın.

CloudStream’in yerleşik oynatıcısı doğrudan medya akışı beklediği için bu web sayfaları SEYİRBOX **SPOR** oynatma listesine eklenmez.

[Patron Futbol 7/24 kanal kataloğu](data/patronfutbol-kanallar.json) **PATRON SPOR** için ikinci web adresini kaydeder. Sitenin 7/24 sekmesindeki 39 kanal etiketi 36 benzersiz oynatıcı sayfasına gider. Oynatıcı yolları Patron İzle kataloğundakilerle eşleşir. Her katalog kendi alan adını kullanır.

## SELCUK SPOR

[Selçuk Sports 7/24 TV kanal kataloğu](data/spor6-selcuksports-kanallar.json) 41 kayıt içerir. 38 kayıtta web oynatıcısı adresi vardır. Sky Sports F1, Smart Spor 2 ve TRT Spor 2 için site bir oynatıcı adresi sunmadığından `channelPage` alanı boştur. Doğrudan Selçuk Sports akışları doğrulanana kadar bu liste web kanal kataloğu olarak kullanılabilir.

## TARAFTARIUM

[Taraftarium canlı TV kanal kataloğu](data/taraftarium-canli-kanallar.json), `taraftarium.co/canli/` sayfasında **7/24** olarak işaretlenen 53 benzersiz web oynatıcı sayfasını içerir.

## TARAFTARIUM24

[Taraftarium24 karşılaşma rehberi](data/taraftarium24-program.json), `taraftarium24.run` üzerindeki değişken maç programına ve spor kategorilerine bağlantı verir. Maç ayrıntısındaki yayın seçenekleri sitenin web oynatıcı sayfasını açar.

Bu üç dosya GitHub'daki web kataloglarıdır. CloudStream cihazındaki oynatılabilir gruplar `data/channels.json` üzerinden oluşturulur.
