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
| **Arena + KOTH** | Güney | Adadaki tek PvP alanı; ortasında altın KOTH tepesi |
| **Kasalar** | Batı | Günlük, Nadir ve Efsane kasası (ender sandıkları) |
| **Atlama noktası** | Kuzeydoğu | İskeleden atla, süzülerek dünyaya in (paraşüt) |

- Spawn noktası (EssentialsX Spawn: `default` ve `newbies`), 5 warp (`/warp market`, `arena`, `kasalar`, `portal`, `atla`)
  ve 10 hologram (DecentHolograms; `/dh` ile düzenlenebilir) kendiliğinden ayarlanır.
- Ada korunur: blok kırma/koyma, sandık/kapı kullanma, kova, patlama, ateş, su taşması, kar/buz, yaprak dökülmesi yok;
  arena dışında hasar, açlık ve PvP yok; adada düşman yaratık doğmaz. Değiştirmek için `skycore.spawn.duzenle` yetkisi
  (OP'lerde var).
- **Paraşüt:** adadan atlayan oyuncu normal hızda düşer, yere 25 blok kala yavaşlar ve düşme hasarı almaz
  (eski 1.8 istemcilerde de hasar almaz).
- Önizleme: `branding/spawn-adasi-onizleme.png`, `branding/spawn-adasi-harita.png`.
  WorldEdit ile elle koymak istersen: `branding/SkySpawn.schem`. Tasarımı değiştirmek için `tools/spawn_island.py`.

## Özellikler

| Özellik | Ne yapar | Komut |
|---|---|---|
| **Sunucu marketi** | Kategorili menü (yapı blokları, ağaçlar, madenler, tarım, yiyecek, ganimet, redstone, çeşitli; 190+ eşya). Sol tık al, sağ tık sat, Shift ile yığın/hepsi. Eşya adları oyuncunun dilinde (Türkçe) görünür. Satış fiyatı her zaman alıştan düşük (al-sat ile para basılamaz); isimli/büyülü/hasarlı eşya satılamaz. | `/market`, `/sat` (elindeki), `/sat hepsi` |
| **Klanlar** | Klan kur (5000₺), davet et, klan evi, ortak klan kasası, klan sohbeti. Klan arkadaşları birbirine vuramaz. Klan etiketi TAB listesinde ve isim üstünde görünür. | `/klan`, `/ks <mesaj>` |
| **Kasalar** | Spawn'daki kasaya anahtarla sağ tıkla: dönen çark, sonunda ödül (para, eşya, büyülü kitap, efsane kazma/kılıç, başka anahtar...). Sol tık: ödüller ve şansları. Nadir ödüller herkese duyurulur. Anahtar taklit edilemez (gizli veri). | — |
| **Anahtar kaynakları** | Günlük Kasa: her `/odul`. Nadir Kasa: her aktiflik ödülü. Efsane Kasa: KOTH kazanmak ve 7 günlük seri. | `/skycore anahtar` |
| **KOTH** (Tepenin Kralı) | Her gün 20:00 ve 22:30'da arenadaki altın tepeyi 120 sn tek başına (ya da klanınla) tutan kazanır: 5000₺ + Efsane anahtarı. Başka klandan biri tepedeyse sayaç durur. Ekranın üstünde ilerleme çubuğu. | `/koth` |
| **Vahşi doğa** | Dünyada 500-5000 blok arası rastgele, güvenli (su/lav/kaktüs olmayan) bir yere ışınlar; parçalar arka planda yüklenir, sunucu donmaz. 60 sn bekleme. | `/vahsi` (`/wild`) |
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
| **Bilgi komutları** | Discord ve site adresleri (`config.yml` → `bilgi`). | `/discord`, `/site` |

### Klan komutları

`/klan kur <isim>` · `/klan davet <oyuncu>` · `/klan kabul [klan]` · `/klan reddet` · `/klan bilgi [klan]` ·
`/klan liste` · `/klan ev` · `/klan evayarla` · `/klan banka [yatir|cek <miktar>]` · `/klan ayril` ·
`/klan at <oyuncu>` · `/klan devret <oyuncu>` · `/klan sil onayla` · `/ks <mesaj>` (klan sohbeti)

### Yönetim (`skycore.admin`, OP'lerde var)

| Komut | Ne yapar |
|---|---|
| `/skycore kurulum [onayla]` | Spawn adasını kurar (önce bilgi gösterir) |
| `/skycore anahtar <oyuncu\|herkes> <gunluk\|nadir\|efsane> [adet]` | Kasa anahtarı verir |
| `/skycore koth <baslat\|bitir>` | KOTH etkinliğini elle başlatır/bitirir |
| `/skycore oyun` | Hemen bir sohbet oyunu başlatır |
| `/skycore duyuru` | Sıradaki duyuruyu gönderir |
| `/skycore reload` | Ayarları (config.yml, market.yml) yeniden yükler |

### PlaceholderAPI

TAB, DecentHolograms vb. için: `%skycore_klan%`, `%skycore_klan_etiket%`, `%skycore_klan_uye%`,
`%skycore_klan_rutbe%`, `%skycore_gunluk_seri%`, `%skycore_kelle%`, `%skycore_koth_galibiyet%`, `%skycore_koth_son%`.
Hazır TAB ayarında klan etiketi listede/isim üstünde, klan ve günlük seri skor tablosunda gösterilir.

### Yetkiler

Herkese açık: `skycore.banknot`, `skycore.kelle`, `skycore.odul`, `skycore.vahsi`, `skycore.market`, `skycore.klan`, `skycore.koth`.
OP'lerde (ve LuckPerms'te `*` olan admin grubunda): `skycore.admin`, `skycore.spawn.duzenle`, `skycore.vahsi.bekleme-yok`.
`skycore.savas.bypass` verilen oyuncu savaş moduna girmez.

Gerekenler: Vault (VaultUnlocked) + EssentialsX ekonomisi. İsteğe bağlı: EssentialsX Spawn (spawn/warp),
DecentHolograms (hologramlar), PlaceholderAPI (yer tutucular), AuthMe (girişten sonra karşılama).

## Geliştiriciler için

Kaynak kod: `src/main/java/net/skysurvival/skycore/`. Testler: `src/test/java/` (MockBukkit, 29 test).

- Eklenti gerçek **Paper 26.1.2 API** kaynağına karşı derlendi (`--release 21`, uyarısız).
- MockBukkit henüz 26.x'i desteklemediği için testler **Paper 1.21.11 API**'siyle çalıştırıldı; SkyCore'un kullandığı API iki
  sürümde aynı. 29 testin hepsi geçti (ada kurulumu, koruma, PvP, portal, market, klan, kasa, KOTH, eski ayar dosyasının güncellenmesi dahil).
- Spawn adası `tools/spawn_island.py` ile üretilir: `src/main/resources/spawn/ada.bp.gz` (bloklar) ve `spawn/ada.yml`
  (spawn, warp, portal, kasa, KOTH ve hologram noktaları).
- Kendin derlemek için `gradle build` (JDK 25 gerekir). `build.gradle.kts` kurulum ortamında çalıştırılmadı.
