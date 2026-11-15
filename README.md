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
| SPOR | Yayıncı Kaynakları, JUSTIN SPOR, TRGOALS, DOĞU SPOR, VAVOO SPOR, PATRON SPOR, SELCUK SPOR, TARAFTARIUM, TARAFTARIUM24 |

SPOR satırları yatay kartlar ve hizalanmış kanal logoları kullanır. Arama sonuçlarında aynı logolar dikey kartlara yerleştirilir. [Logo kaynakları](assets/channel-logos/README.md)

Film, dizi ve spor satırlarında kaynak adları gösterilir. DiziFilmizle dizileri ayrı satırda yer alır. Film sayfası doğrudan HTTPS HLS, DASH, MP4 veya WebM bağlantısı sunuyorsa CloudStream oynatıcısı kullanılır. RareFilmm’in herkese açık OK.ru oynatıcıları da desteklenir. Kaynak sitesi web oynatıcısı gerektiriyorsa **Oynat** tarayıcıyı açar. Dizi bölümleri kendi kaynak sayfalarında oynatılır.

DİZİBOX araması sonuç vermezse arşivin ilk sayfası taranır. HDFilmCehennemi kataloğu ve örnek Dizibox detay sayfaları bazı isteklerde erişim kısıtlaması döndürdü. Tarayıcı erişimi kaynak sitesine ve bağlantıya bağlıdır.

Yeni kaynaklar [Ripplay](https://github.com/Ripplay/cloudstream-repo), [Nikyokki](https://github.com/nikyokki/nik-cloudstream) ve [TurkSinema](https://github.com/Wiojelt/TurkSinema) depolarındaki kataloglardan seçildi. SEYİRBOX, her kaynağın herkese açık HTML sayfalarını kendi ayrıştırıcısıyla okur.

[Kaynak kayıtları](data/seyirbox-sources.json) · [Eklenti dizini](https://raw.githubusercontent.com/ortaksistemtcltv/spor/builds/plugins.json)

Ana yayın derlemesi tek **SEYİRBOX** eklentisini üretir. `-PpreviewCatalogs=true` yerel seçenek olarak FİLM ve DİZİ’yi ayrıca derler. Bu ayrı paketler önizleme deposunu daha önce kurmuş cihazlarda listelenebilir.

## Web yayınları ve maç programı

PATRON SPOR, SELCUK SPOR, TARAFTARIUM ve TARAFTARIUM24 kartlarında **Oynat** düğmesi iptal edilebilen beş saniyelik geri sayımı başlatır. Ardından Android’in tarayıcısı kaynak sayfayı açar. Kaynak sayfasındaki reklam ve oynatma davranışını yayın sitesi belirler. Android TV’de akış için URL açabilen bir tarayıcı gerekir.

- [PATRON SPOR](data/spor5-patronizle-kanallar.json) 36 benzersiz kanal sayfası.
- [SELCUK SPOR](data/spor6-selcuksports-kanallar.json) 38 kanal sayfası.
- [TARAFTARIUM](data/taraftarium-canli-kanallar.json) 53 kanal sayfası.
- [TARAFTARIUM24](data/taraftarium24-program.json) Maç programı için 10 branş kartı.

CloudStream'in SPOR satırları `data/channels.json` üzerinden oluşturulur. Tekrar kontrolde 404 ya da medya yerine HTML döndüren kayıtlar [devre dışı bırakılan kaynaklar listesinde](data/unavailable-sports-sources.json) işaretlidir. Diğer kanal kimlikleri aynı kaldığı için mevcut bağlantılar çalışmaya devam eder.
