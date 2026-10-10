# SkyCore — Sky Survival'a özel eklenti

Yurtdışı ve Türk survival sunucularında sevilen özelliklerin tek, hafif ve Türkçe bir eklentide toplanmış hâli.
Hazır derlenmiş dosya: `server/plugins/SkyCore-1.1.0.jar`. Ayarlar: `server/plugins/SkyCore/config.yml`,
market fiyatları: `server/plugins/SkyCore/market.yml` (ilk açılışta oluşur; her özellik `aktif: true/false`
ile açılıp kapatılır, `/skycore reload` ile yenilenir).

## Gökyüzü spawn adası

`/skycore kurulum onayla` tek komutla gökyüzüne (varsayılan `world` 0, 200, 0) hazır bir spawn adası kurar
(~140.000 blok, birkaç saniye) ve şunları **otomatik** ayarlar:

| Bölüm | Yer | Ne var |
|---|---|---|
| **Meydan** | Merkez | Fıskiye, fenerler, banklar, spawn noktası |
| **Vahşi Doğa portalı** | Kuzey | İçinden geçen `/vahsi` ile dünyada rastgele güvenli bir yere ışınlanır |
| **Market** | Doğu | Ahşap market binası; zümrüt zemine basınca `/market` açılır |
| **PvP arenası** | Güney | Adadaki tek PvP alanı (KOTH arenası kurulmazsa ortadaki altın tepe KOTH alanı olur) |
| **Kasalar** | Batı | Günlük, Nadir ve Efsane kasası (ender sandıkları) |
| **Atlama noktası** | Kuzeydoğu | İskeleden atla, süzülerek dünyaya in (paraşüt) |
| **Sıralama tabloları** | Portal yolunun iki yanı | Altın kürsüler üstünde: en zenginler, bu ayın oycuları, KOTH şampiyonları, en çok görev |

- Spawn noktası (EssentialsX Spawn: `default` ve `newbies`), 5 warp (`/warp market`, `arena`, `kasalar`, `portal`, `atla`)
  ve 14 hologram (4'ü sıralama tablosu; DecentHolograms, `/dh` ile düzenlenebilir) kendiliğinden ayarlanır.
- Ada korunur: blok kırma/koyma, sandık/kapı kullanma, kova, patlama, ateş, su taşması, kar/buz, yaprak dökülmesi yok;
  arena dışında hasar, açlık ve PvP yok; adada düşman yaratık doğmaz. Değiştirmek için `skycore.spawn.duzenle` yetkisi
  (OP'lerde var).
- **Paraşüt:** adadan atlayan oyuncu normal hızda düşer, yere 25 blok kala yavaşlar ve düşme hasarı almaz
  (eski 1.8 istemcilerde de hasar almaz).
- Önizleme: `branding/spawn-adasi-onizleme.png`, `branding/spawn-adasi-harita.png`.
  WorldEdit ile elle koymak istersen: `branding/SkySpawn.schem`. Tasarımı değiştirmek için `tools/spawn_island.py`.

## KOTH arenası: "The Hill"

Aynı `/skycore kurulum onayla` komutu, adanın 220 blok güneyine (`koth-arenasi.konum`) Overcast Network'ün ünlü KOTH
haritası **"The Hill"**i de kurar (Articray, TheZaner, xXFracXx; katkı: ItsMiiOlly, ElectroidFilms — **CC BY-SA 4.0**,
[OvercastCommunity/PublicMaps](https://github.com/OvercastCommunity/PublicMaps)). Harita `tools/import_map.py` ile 1.8
bloklarından güncel sürüme çevrildi.

- Ortadaki katmanlı tepenin üstü KOTH alanıdır; altındaki fenerin ışını gökyüzüne yükselir.
- `/warp koth` → yapımcıların kafaları ve Türkçe tabelalarıyla tanıtım adası; ortadaki işarete basan arenaya geçer.
- `/koth katil` → doğrudan arenadaki iki üsten birine.
- Arena korunur (blok kırılmaz/konmaz, düşman yaratık doğmaz) ama PvP ve hasar açıktır; arenadan düşen paraşütle iner.
- `koth-arenasi.aktif: false` yapılırsa arena kurulmaz, KOTH adadaki altın tepede oynanır.

## Özellikler

| Özellik | Ne yapar | Komut |
|---|---|---|
| **Sunucu marketi** | Kategorili menü (yapı blokları, ağaçlar, madenler, tarım, yiyecek, ganimet, redstone, çeşitli; 190+ eşya). Sol tık al, sağ tık sat, Shift ile yığın/hepsi. Eşya adları oyuncunun dilinde (Türkçe) görünür. Satış fiyatı her zaman alıştan düşük (al-sat ile para basılamaz); isimli/büyülü/hasarlı eşya satılamaz. | `/market`, `/sat` (elindeki), `/sat hepsi` |
| **Klanlar** | Klan kur (5000₺), davet et, klan evi, ortak klan kasası, klan sohbeti. Klan arkadaşları birbirine vuramaz. Klan etiketi TAB listesinde ve isim üstünde görünür. | `/klan`, `/ks <mesaj>` |
| **Kasalar** | Spawn'daki kasaya anahtarla sağ tıkla: dönen çark, sonunda ödül (para, eşya, büyülü kitap, efsane kazma/kılıç, başka anahtar...). Sol tık: ödüller ve şansları. Nadir ödüller herkese duyurulur. Anahtar taklit edilemez (gizli veri). | — |
| **Anahtar kaynakları** | Günlük Kasa: her `/odul`. Nadir Kasa: her aktiflik ödülü. Efsane Kasa: KOTH kazanmak ve 7 günlük seri. | `/skycore anahtar` |
| **KOTH** (Tepenin Kralı) | Her gün 20:00 ve 22:30'da "The Hill" arenasındaki tepeyi 120 sn tek başına (ya da klanınla) tutan kazanır: 5000₺ + Efsane anahtarı. Başka klandan biri tepedeyse sayaç durur. Ekranın üstünde ilerleme çubuğu. | `/koth`, `/koth katil` |
| **Vahşi doğa** | Dünyada 500-5000 blok arası rastgele, güvenli (su/lav/kaktüs olmayan) bir yere ışınlar; parçalar arka planda yüklenir, sunucu donmaz. 60 sn bekleme. | `/vahsi` (`/wild`) |
| **Mezar** | Ölünce eşyalar ve XP, ölünen yerde oyuncunun kafası şeklinde bir mezara girer (lavda yanmaz, boşluğa düşmez). Sahibi sağ tıklayınca hepsi geri gelir, zırhlar giydirilir. İlk 15 dk sadece sahibi açabilir, 60 dk sonra eşyalar dökülür; oyuncu başına en fazla 5 mezar. Mezar kırılamaz, patlamadan/pistondan/sudan etkilenmez. PvP ölümlerinde (ayara göre) eşyalar normal düşer. | `/mezar` |
| **Ağaç devirme** | Eğilerek baltayla kütük kırınca ağacın tamamı devrilir (en fazla 96 blok). Sadece doğal ağaçlar: oyuncunun kütükten yaptığı duvarlar devrilmez. Balta her kütük için aşınır. | Shift + balta |
| **Damar kazma** | Eğilerek kazmayla maden kırınca bitişik aynı madenler de kazılır (en fazla 24, derin kayrak türü dahil); doğru kazma gerekir. | Shift + kazma |
| **Sağ tık hasat** | Olgun buğday, havuç, patates, pancar, siğil, kakaoya sağ tıkla: hasat edilir ve tohum kendiliğinden yeniden ekilir. | Sağ tık |
| **Günlük görevler** | Her gün (00:00'da) her oyuncuya 19 görevlik havuzdan rastgele 3 görev: kaz, öldür, balık tut, hasat et, fırından çıkar, hayvan üret. Bitirince para (bazılarında anahtar), üçü bitince 1000₺ + Nadir anahtar. Oyuncunun kendi koyduğu bloklar ve ipeksi dokunuşla kazılan madenler sayılmaz. | `/gorev` |
| **Yeni oyuncu koruması** | Toplam oynama süresi 60 dakikayı geçmeyen oyuncu PvP'de vurulamaz ve vuramaz (arenalarda geçmez). | `/koruma`, `/koruma kapat` |
| **Güvenli takas** | İki oyuncu aynı menüye eşya koyar, karşı tarafınkini görür; ikisi de onaylayınca değişir. Teklif değişince onaylar sıfırlanır, değişiklikten hemen sonra onay kabul edilmez (son saniye hilesine karşı). Menü kapanırsa herkes eşyasını geri alır. Takaslar konsola kaydedilir. | `/takas <oyuncu>`, `/takas kabul` |
| **Savaş modu** (combat log) | PvP'ye giren iki oyuncu 15 sn "savaşta" sayılır; ekranda geri sayım çıkar. Bu sürede `/spawn`, `/home`, `/tpa`, `/warp`, `/vahsi`, `/back` gibi kaçış komutları çalışmaz. Savaştayken oyundan çıkan oyuncu ölür ve eşyaları yere düşer. | — |
| **Sohbet oyunları** | 10 dakikada bir sohbete soru gelir: kelimeyi ilk yazan, işlemi ilk çözen ya da karışık harflerden kelimeyi ilk bulan para kazanır. Türkçe karakter farkı önemsenmez (kılıç = kilic). | `/skycore oyun` |
| **Banknot** | Parayı kağıda çevirir; sağ tıklayınca geri hesaba yatar (Shift + sağ tık: hepsi). | `/banknot <miktar>` (`5k`, `1.000`, `2,5`) |
| **Kelle avı** (bounty) | Bir oyuncunun kellesine para konur, onu öldüren alır. Aynı IP'deki oyuncular birbirinden ödül alamaz. | `/kelle koy <oyuncu> <miktar>`, `/kelle liste` |
| **Günlük ödül serisi** | Her gün `/odul` ile para + Günlük Kasa anahtarı; üst üste gelince ödül artar (100₺ → ... → 1000₺), her 7. gün Efsane anahtarı. | `/odul` |
| **Aktiflik ödülü** | AFK olmadan oynanan her 60 dakika için 500₺ + Nadir Kasa anahtarı. | — |
| **Otomatik duyurular** | 5 dakikada bir sırayla ipucu mesajları. | `/skycore duyuru` |
| **Kafa düşürme** | PvP'de ölen oyuncunun kafası yere düşer; üzerinde avcının adı ve tarih yazar. | — |
| **Ölüm koordinatı** | Ölen oyuncuya öldüğü yerin koordinatı yazılır. | — |
| **Hoş geldin başlığı** | Girişte (AuthMe şifresinden sonra) büyük "SKY SURVIVAL" başlığı ve ses. | — |
| **Sıralama tabloları** | En zenginler, en çok oynayanlar, en çok görev bitirenler, KOTH şampiyonları, bu ayın oycuları. Spawn'daki hologramlar dakikada bir güncellenir; para ve süre çevrimiçi oyunculardan okunur (çevrimdışının son değeri kalır). `skycore.siralama.gizli` olanlar (admin) görünmez. | `/siralama <para\|sure\|gorev\|koth\|oy>` |
| **Oy ödülleri** | NuVotifier ile oy sitelerinden gelen her oy: 250₺ + Nadir anahtar (çevrimdışıysa girince verilir). Her 30 oyda bir **oy partisi**: çevrimiçi herkese anahtar. Girişte oy vermeyene hatırlatma. Hileye karşı: sadece sunucuda oynamış isimler, aynı siteden 12 saatte bir, aynı IP'den sitede günde en fazla 2 hesap. | `/oy`, `/oy siralama` |
| **Discord** | `/discord` davet bağlantısı (tıklanabilir); DiscordSRV açıksa `/discord link` hesap bağlar. KOTH başlangıcı/kazananı ve oy partisi Discord kanalına da yazılır (`discord-duyurulari`). | `/discord`, `/discord link`, `/site` |

### Klan komutları

`/klan kur <isim>` · `/klan davet <oyuncu>` · `/klan kabul [klan]` · `/klan reddet` · `/klan bilgi [klan]` ·
`/klan liste` · `/klan ev` · `/klan evayarla` · `/klan banka [yatir|cek <miktar>]` · `/klan ayril` ·
`/klan at <oyuncu>` · `/klan devret <oyuncu>` · `/klan sil onayla` · `/ks <mesaj>` (klan sohbeti)

### Yönetim (`skycore.admin`, OP'lerde var)

| Komut | Ne yapar |
|---|---|
| `/skycore kurulum [onayla]` | Spawn adasını ve KOTH arenasını kurar (önce bilgi gösterir) |
| `/skycore hologramlar` | Kurulu adanın hologramlarını (sıralama tabloları dahil) yeniden oluşturur, bloklara dokunmaz |
| `/skycore anahtar <oyuncu\|herkes> <gunluk\|nadir\|efsane> [adet]` | Kasa anahtarı verir |
| `/skycore koth <baslat\|bitir>` | KOTH etkinliğini elle başlatır/bitirir |
| `/skycore oyun` | Hemen bir sohbet oyunu başlatır |
| `/skycore duyuru` | Sıradaki duyuruyu gönderir |
| `/skycore reload` | Ayarları (config.yml, market.yml, gorevler.yml) yeniden yükler |

### PlaceholderAPI

TAB, DecentHolograms vb. için: `%skycore_klan%`, `%skycore_klan_etiket%`, `%skycore_klan_uye%`,
`%skycore_klan_rutbe%`, `%skycore_gunluk_seri%`, `%skycore_kelle%`, `%skycore_koth_galibiyet%`, `%skycore_koth_son%`,
`%skycore_gorev%` (bugün biten görev, ör. 2/3), `%skycore_gorev_toplam%`, `%skycore_koruma%` (yeni oyuncu korumasının kalan süresi).
Sıralama: `%skycore_top_<tablo>_<1-10>%` (hazır satır: "#1 Ali » 12.500₺"), `%skycore_top_<tablo>_<sıra>_isim%`,
`%skycore_top_<tablo>_<sıra>_deger%`, `%skycore_sira_<tablo>%` (oyuncunun sırası); tablolar `para`, `sure`, `gorev`, `koth`, `oy`.
Oy: `%skycore_oy_ay%`, `%skycore_oy_toplam%`, `%skycore_oy_parti%` (ör. 12/30).
Hazır TAB ayarında klan etiketi listede/isim üstünde; klan, günlük seri, görev ve oy partisi skor tablosunda gösterilir.

### Yetkiler

Herkese açık: `skycore.banknot`, `skycore.kelle`, `skycore.odul`, `skycore.vahsi`, `skycore.market`, `skycore.klan`, `skycore.koth`,
`skycore.mezar`, `skycore.gorev`, `skycore.koruma`, `skycore.takas`, `skycore.siralama`, `skycore.oy`. OP'lerde ayrıca `skycore.mezar.admin` (kilitli mezarı açar).
OP'lerde (ve LuckPerms'te `*` olan admin grubunda): `skycore.admin`, `skycore.spawn.duzenle`, `skycore.vahsi.bekleme-yok`.
`skycore.savas.bypass` verilen oyuncu savaş moduna girmez. `skycore.siralama.gizli` olan oyuncu sıralamalarda görünmez
(OP'ler otomatik almaz; LuckPerms'te `*` olan admin grubunda var).

Gerekenler: Vault (VaultUnlocked) + EssentialsX ekonomisi. İsteğe bağlı: EssentialsX Spawn (spawn/warp),
DecentHolograms (hologramlar), PlaceholderAPI (yer tutucular), AuthMe (girişten sonra karşılama), NuVotifier (oy ödülleri),
DiscordSRV (Discord köprüsü, `/discord link`).

## Dupe ve hile korumaları

`src/test/java/.../ExploitTest.java` içindeki her test bir açığı **gerçekten dener** ve SkyCore'un engellediğini doğrular:

| Denenen açık | Sonuç |
|---|---|
| Savaştayken `/essentials:spawn`, `/ehome`, `/tpaccept` gibi takma adlarla kaçmak | Komut adının bütün takma adları ve `eklenti:` önekleri yakalanır |
| Savaştayken çıkıp eşyaları mezara saklamak | Savaştan kaçan ve PvP'de ölen için mezar oluşmaz, eşyalar yere düşer |
| Klan evi / `/vahsi` bekleme süresinde savaşa girip yine de ışınlanmak | Işınlanma anında savaş tekrar kontrol edilir |
| Takas menüsündeki eşyayla ölüp eşyayı korumak, takası hasarla sürdürmek | Ölünce teklif yere düşer, hasar alınca takas iptal olur |
| Karşı tarafın takas kutusundan shift, sayı tuşu, sürükleme, çift tık vb. ile eşya çalmak | Sadece kendi kutuna izin var, diğer bütün tıklamalar engellenir |
| Karşı taraf onaylarken son saniyede teklifi değiştirmek | Değişince iki onay da sıfırlanır, 1 sn onay kabul edilmez |
| Market / kasa menüsünden eşya almak (shift, sayı tuşu, çift tık, `Q`) | Menüler salt okunur |
| Kasa anahtarını iki kez kullanmak, mezarı iki kez toplamak | Anahtar bir kez düşer, mezar bir kez açılır |
| Sahte banknot (NaN, sonsuz, eksi, sınırın üstü) | Geçersiz banknot reddedilir |
| Klan bankası, banknot ve kellede `-500`, `NaN`, `Infinity`, `1e400` | Hiçbiri para yaratmaz/silmez |
| Pistonla koyduğun bloğu itip görevde "doğal blok" diye saydırmak | Koyulan blok işareti pistonla birlikte taşınır |
| Pistonla, dağıtıcıyla spawn adasına/arenaya blok, lav, su sokmak | Dışarıdan korumalı alana itme ve dağıtma engellenir |
| Alt hesaplarla günlük ödülü ve aktiflik ödülünü katlamak | IP başına en fazla 2 hesap ödül alır (`ip-basina-max`) |
| Yeni oyuncu korumasını, ada ya da klan PvP yasağını zehir/yavaşlık iksiriyle aşmak | Atılan ve kalıcı kötü iksirler korunan oyuncuyu etkilemez |
| Marketten al → üret → markete sat ile para basmak | Varsayılan fiyatlarda yok; zümrüt satılamaz (köylü takası). Fiyat değiştirdikten sonra `python3 tools/market_kontrol.py` |
| Kelleyi alt hesapla toplamak | Aynı IP'den kelle ödülü alınamaz |
| Oy ödülünü alt hesaplarla, sahte isimlerle ya da çift gelen oyla katlamak | Sadece oynamış isimler; aynı siteden 12 saatte bir; aynı IP'den sitede günde 2 hesap (`CommunityTest`) |

## Geliştiriciler için

Kaynak kod: `src/main/java/net/skysurvival/skycore/`. Testler: `src/test/java/` (MockBukkit, 66 test).

- Eklenti gerçek **Paper 26.1.2 API** kaynağına karşı derlendi (`--release 21`, uyarısız).
- MockBukkit henüz 26.x'i desteklemediği için testler **Paper 1.21.11 API**'siyle çalıştırıldı; SkyCore'un kullandığı API iki
  sürümde aynı. 66 testin hepsi geçti (ada ve arena kurulumu, koruma, PvP, portal, market, klan, kasa, KOTH, mezar, ağaç devirme,
  hasat, görevler, yeni oyuncu koruması, takas, sıralama tabloları, oy ödülleri (sahte NuVotifier olayıyla), Discord komutu,
  16 dupe/hile denemesi, eski ayar dosyasının güncellenmesi dahil).
- Spawn adası `tools/spawn_island.py` ile üretilir: `src/main/resources/spawn/ada.bp.gz` (bloklar) ve `spawn/ada.yml`
  (spawn, warp, portal, kasa, KOTH ve hologram noktaları).
- KOTH arenası `tools/import_map.py` ile üretilir: `src/main/resources/koth/` (CC BY-SA 4.0, `koth/LICENSE.txt`).
- Kendin derlemek için `gradle build` (JDK 25 gerekir). `build.gradle.kts` kurulum ortamında çalıştırılmadı.
