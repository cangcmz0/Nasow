# Sky Survival — Herkese Açık Minecraft Sunucusu

Meslek, yetenek ve ekonomi odaklı, herkese açık bir **Survival** sunucusu (Paper).

- **Minecraft Java 26.1.2** (ViaVersion sayesinde daha yeni/eski sürümlerle de girilebilir)
- **Bedrock desteği:** telefon, konsol ve Windows Bedrock oyuncuları da girebilir (Geyser + Floodgate)
- Türkçe mesajlar, ₺ ekonomi, meslekler, yetenekler, rütbeler, arazi koruması, oyuncu marketleri
- Hile koruması, X-Ray engeli, grief kaydı, otomatik yedek, çökünce otomatik yeniden başlama

---

## Oyuncular ne yapabilir?

| Özellik | Komut / nasıl |
|---|---|
| Meslek seçip çalıştıkça para kazanma (madenci, oduncu, çiftçi, avcı...) | `/jobs` |
| Yetenek kasma (madencilik, savaş, tarım...) ve güçlenme | `/skills` |
| Arazi koruma, arkadaş ekleme | Altın kürekle köşeleri işaretle, `/trust <oyuncu>` |
| Sandıkla market kurma, başkasının marketinden alışveriş | Sandığa eşya koy, sandığa vurup fiyat yaz |
| Para işlemleri | `/bal`, `/pay`, `/baltop`, `/sell` (eldeki eşyayı sunucuya sat) |
| Ev ve ışınlanma | `/sethome`, `/home`, `/tpa`, `/spawn`, `/warp`, `/tpr` (rastgele yere git) |
| Günlük ödül | `/kit gunluk` |
| Oturma / uzanma | `/sit`, `/lay`, `/crawl`, merdivene sağ tık |

Rütbeler: **Oyuncu → VIP → Moderatör → Admin**.

---

## Gereksinimler

- **Java 25 veya daha yenisi** → <https://adoptium.net/> (JDK 25, kurarken *Add to PATH* işaretli olsun)
- Sunucuya ayırabileceğin **en az 6–8 GB RAM** (herkese açık sunucu için; test için 4 GB yeter)
- Açılacak portlar:
  - `25565` **TCP** → Java oyuncuları
  - `19132` **UDP** → Bedrock oyuncuları

## Hızlı başlangıç

1. Zip'i aç (ya da repoyu `git clone` ile indir).
2. Başlat:
   - **Windows:** `server/start.bat` dosyasına çift tıkla.
   - **Linux / macOS:** `cd server && ./start.sh`
3. İlk açılışta:
   - Paper (`server.jar`) ve eksik eklentiler resmi sitelerinden **otomatik indirilir**,
   - Minecraft EULA'yı kabul edip etmediğin sorulur (`e` yaz),
   - sunucu açılır.
4. Konsolda `Done` yazısını görünce [`server/setup/ilk-kurulum-komutlari.txt`](server/setup/ilk-kurulum-komutlari.txt) içindeki komutları konsola yapıştır
   (rütbeler, yetkiler, kendini admin yapma, dünya ön-oluşturma). Sonra `stop` yaz; sunucu kendiliğinden yeniden açılır ve X-Ray koruması devreye girer.
5. Oyunda `localhost` (aynı bilgisayar) ya da sunucunun IP adresi ile bağlan.

RAM miktarını `start.bat` içindeki `set RAM=4G` satırından değiştirebilirsin (Linux'ta `RAM=8G ./start.sh`).

**Sunucuyu kapatmak / yeniden başlatmak:** konsola `stop` yaz → 10 saniye içinde otomatik yeniden açılır (çökmelerde de).
Tamamen kapatmak için pencereyi kapat ya da `CTRL+C`. `/restart` komutunu kullanma.

---

## Herkese açık sunucu için önemli notlar

- **Evde barındırma riskli:** Evden açarsan ev IP adresin oyunculara görünür ve DDoS (saldırı) ile internetin kesilebilir.
  Herkese açık sunucu için **DDoS korumalı bir Minecraft hosting** firması ya da VPS kullan.
- **Alan adı:** `oyna.seninsunucun.com` gibi bir alan adı al; IP değişse bile oyuncular aynı adresle girer.
- **VIP satışı (Minecraft kuralları):** Mojang, herkese açık sunucularda **parayla oyun avantajı satmayı yasaklar**.
  VIP'ye sadece kozmetik şeyler verebilirsin (renkli yazı, önek, `/nick`, `/hat`). Bu yüzden hazır VIP yetkileri sadece kozmetiktir.
  VIP'yi parayla değil oylama ya da oyun süresiyle veriyorsan istediğin yetkiyi ekleyebilirsin.
- **Premium (orijinal hesap) zorunlu:** `online-mode=true`. Crack (orijinal olmayan) hesaplar giremez; bu, başkasının senin
  ya da yetkililerin adıyla girmesini engeller. Bedrock oyuncuları kendi Microsoft hesaplarıyla girer.
- **Kurallar:** `server/plugins/Essentials/rules.txt` (oyunda `/rules`). Grief olursa: `/co inspect` ile kim yaptı bak, `/co rollback` ile geri al.

---

## Eklentiler

`server/setup/plugins.json` listesindeki eklentiler otomatik indirilir ve güncellenir.

| Eklenti | Ne işe yarar |
|---|---|
| **LuckPerms** | Rütbe ve yetki sistemi. Tarayıcıda düzenlemek için: `/lp editor` |
| **EssentialsX** (+ Chat, Spawn) | `/home` `/tpa` `/spawn` `/warp` `/kit` `/msg` `/pay` `/bal` `/sell` `/tpr`, ban/mute ve ekonomi |
| **VaultUnlocked** | Ekonomi köprüsü (Vault'un güncel hâli) |
| **Jobs Reborn** (+ CMILib) | Meslekler, çalıştıkça para kazanma (`/jobs`) — Türkçe |
| **AuraSkills** | mcMMO tarzı yetenek ve seviye sistemi (`/skills`) — Türkçe |
| **WorldEdit** + **WorldGuard** | Spawn'ı düzenleme ve koruma |
| **CoreProtect** | Blok/sandık kayıtları, grief geri alma: `/co inspect`, `/co rollback` |
| **HuskClaims** | Altın kürekle arazi koruma, `/trust <oyuncu>` ile arkadaş ekleme |
| **GrimAC** | Hile koruması (fly, speed, killaura...). Moderatörler uyarıları görür |
| **DriveBackupV2** | 3 saatte bir otomatik yedek (`server/backups/`, son 24 saat saklanır). Elle: `/drivebackup backup` |
| **ViaVersion** + **ViaBackwards** | Farklı Minecraft sürümleriyle giriş |
| **Geyser** + **Floodgate** | Bedrock (telefon/konsol) oyuncular girebilir |
| **PlaceholderAPI** | Skor tablosunda para, rütbe vb. göstermek için |
| **TAB** | Tab listesi başlığı, rütbe önekleri, yan skor tablosu (`/sb` ile gizlenir) |
| **QuickShop-Hikari** | Sandığa eşya koyup market kurma |
| **GSit** | `/sit` `/lay` `/crawl`, merdivene sağ tıklayıp oturma |
| **DecentHolograms** | Spawn'a yüzen yazılar (`/dh create ...`) |
| **Chunky** | Dünyayı önceden oluşturur, gezerken lag olmaz |

**Ek olarak Paper'ın kendi X-Ray koruması** ikinci açılışta otomatik açılır (`config/paper-world-defaults.yml`).

**İsteğe bağlı (kapalı, açmak için `"enabled": true` yap):**

| Eklenti | Ne işe yarar |
|---|---|
| DiscordSRV | Oyun sohbetini Discord kanalına bağlar (Discord bot token gerekir) |
| BlueMap | Tarayıcıdan açılan 3D canlı harita (TCP `8100`) |

> Paper zaten **spark** performans aracını içinde getirir: lag olursa `/spark profiler start`.

### Eklenti açmak / kapatmak / eklemek

- **Kapatmak:** `plugins.json` içinde o eklentiye `"enabled": false` ekle → bir sonraki açılışta `.jar` silinir (ayarları kalır).
- **Modrinth'ten yeni eklenti eklemek:** listeye şunu ekle (`project` = Modrinth adresindeki isim):
  ```json
  { "id": "ornek", "name": "Örnek", "source": "modrinth", "project": "ornek-eklenti" }
  ```
- **Elle eklemek:** `.jar` dosyasını doğrudan `server/plugins/` içine atabilirsin; kurulum aracı listede olmayan dosyalara dokunmaz.

### Güncelleme

Sunucu **kapalıyken** `server/update.bat` (Linux: `./update.sh`) çalıştır. Paper ve tüm eklentiler en yeni uyumlu sürüme güncellenir.
İndirilen sürümler `server/setup/lock.json` dosyasında tutulur; güncellemeden sonra bir sorun olursa o dosyayı geri alıp tekrar başlatman yeterli.

---

## Hazır ayarlar

| Dosya | Ne ayarlandı |
|---|---|
| `server/server.properties` | MOTD, 100 oyuncu, görüş mesafesi 8 / simülasyon 6 (performans), normal zorluk, spawn koruması WorldGuard'da |
| `server/plugins/Essentials/config.yml` | Dil **Türkçe**, para birimi **₺**, başlangıç parası **100₺**, ışınlanmada 3 sn bekleme (savaştan kaçış olmasın), sohbet `[Rütbe] İsim » mesaj` |
| `server/plugins/Essentials/kits.yml` | `baslangic` (ilk girişte otomatik, arazi küreği dahil), `gunluk` |
| `server/plugins/Essentials/motd.txt`, `rules.txt` | Türkçe giriş mesajı ve kurallar |
| `server/plugins/TAB/config.yml` | "SKY SURVIVAL" tab başlığı, yan skor tablosu (rütbe, para, ping), isim altında can |
| `server/plugins/Jobs/`, `AuraSkills/`, `GSit/` | Türkçe dil |
| `server/plugins/DriveBackupV2/config.yml` | 3 saatte bir yedek, son 8 yedek, Türkiye saati |

`enforce-secure-profile=false`: Bedrock (Geyser) ve farklı sürümlerle giren oyuncuların sohbette atılmaması için kapalı.

Geyser ilk açılışta `plugins/Geyser-Spigot/config.yml` dosyasını oluşturur; Floodgate kurulu olduğu için orada `auth-type` değerinin `floodgate` olduğundan emin ol.

---

## Hosting firmasında (Pterodactyl vb. panel) çalıştırmak

1. Panelde sunucu türü olarak **Paper 26.1.2**, Java sürümü olarak **Java 25** seç.
2. Kendi bilgisayarında bir kez `server` klasöründe `java setup/Setup.java` çalıştır (eksik eklentiler insin).
3. `server` klasörünün **içindekileri** panelin dosya yöneticisine yükle.
4. Panel kendi başlatma komutunu kullandığı için X-Ray korumasını elle aç: `config/paper-world-defaults.yml` → `anticheat` → `anti-xray` → `enabled: true`.

---

## Klasör yapısı

```
server/
├── start.bat / start.sh       → sunucuyu başlatır (önce eksikleri indirir, kapanınca yeniden açar)
├── update.bat / update.sh     → Paper ve eklentileri günceller
├── server.properties          → temel sunucu ayarları
├── plugins/                   → eklentiler ve ayarları
├── backups/                   → otomatik yedekler (ilk yedekten sonra oluşur)
└── setup/
    ├── plugins.json           → eklenti listesi (aç/kapat buradan)
    ├── lock.json              → kurulu sürümler (otomatik yazılır)
    ├── Setup.java             → indirme/güncelleme aracı
    └── ilk-kurulum-komutlari.txt → ilk açılışta konsola yapıştırılacak komutlar
```

## Sonraki adımlar için fikirler

- **Discord sunucusu** + DiscordSRV ile sohbet köprüsü
- Sunucu listesi sitelerine kayıt + **oy verme ödülleri** (NuVotifier + oy eklentisi)
- **BlueMap** ile web'den canlı harita
- Spawn yapısı, warp noktaları (`/setwarp maden`, `/setwarp market`)
- Sandık kasaları (crates), sezonluk etkinlikler
