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
| DİZİ | DİZİBOX, DİZİFİLMİZLE, DİZİRELLA, DMAX (programlar ve canlı), TLC (programlar ve canlı) |
| SPOR | Yayıncı Kaynakları, JUSTIN SPOR, TRGOALS, DOĞU SPOR, VAVOO SPOR, PATRON SPOR, SELCUK SPOR, TARAFTARIUM |

SPOR satırları yatay kartlar ve hizalanmış kanal logoları kullanır. Arama sonuçlarında aynı logolar dikey kartlara yerleştirilir. [Logo kaynakları](assets/channel-logos/README.md)

Film ve dizi içerikleri CloudStream oynatıcısında açılır. Kaynağın sunduğu HTTPS medya bağlantıları ve CloudStream'in desteklediği gömülü oynatıcılardan çözülen bağlantılar kullanılır. Erişim ve medya bağlantısı her içerik için kaynak sağlayıcıya bağlıdır.

DMAX Program Arşivi ve TLC Program Arşivi satırları, programları ve canlı yayın kartlarıyla Film/Dizi bölümünde yer alır. Yayıncının herkese açık medya bilgisi döndürmesi gerekir.

Yeni kaynaklar [Ripplay](https://github.com/Ripplay/cloudstream-repo), [Nikyokki](https://github.com/nikyokki/nik-cloudstream) ve [TurkSinema](https://github.com/Wiojelt/TurkSinema) depolarındaki kataloglardan seçildi. SEYİRBOX, her kaynağın herkese açık HTML sayfalarını kendi ayrıştırıcısıyla okur.

[Kaynak kayıtları](data/seyirbox-sources.json) · [Eklenti dizini](https://raw.githubusercontent.com/ortaksistemtcltv/spor/builds/plugins.json)

Ana yayın derlemesi tek **SEYİRBOX** eklentisini üretir. `-PpreviewCatalogs=true` yerel seçenek olarak FİLM ve DİZİ’yi ayrıca derler. Bu ayrı paketler önizleme deposunu daha önce kurmuş cihazlarda listelenebilir.

## Canlı spor

PATRON SPOR, SELCUK SPOR ve TARAFTARIUM satırlarında resmi canlı yayınlarla eşleşen kanallar yer alır. Bu kartlar beş saniyelik beklemeden sonra ilgili yayıncının akışını CloudStream oynatıcısına verir. TARAFTARIUM24 listesi maç programı sayfalarından oluşur ve doğrudan canlı yayın kartı içermez.

Diğer SPOR satırları `data/channels.json` üzerinden oluşturulur. 404 ya da medya yerine HTML döndüren kayıtlar [devre dışı bırakılan kaynaklar listesinde](data/unavailable-sports-sources.json) işaretlidir.
