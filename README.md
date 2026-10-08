# Sky Survival — Minecraft Sunucusu

Survival + SMP karışımı (hibrit) bir **Paper** sunucusu.

- **Minecraft Java 26.1.2** (ViaVersion sayesinde daha yeni/eski sürümlerle de girilebilir)
- **Bedrock desteği:** telefon, konsol ve Windows Bedrock oyuncuları da girebilir (Geyser + Floodgate)
- Türkçe mesajlar, ₺ ekonomi, rütbeler, arazi koruması, oyuncu marketleri

---

## Survival mı, SMP mi?

İkisinin ortası olarak kuruldu, istediğin tarafa kaydırabilirsin:

| SMP tarafı | Survival sunucusu tarafı |
|---|---|
| Vanilla dünya, oyuncular kendi üssünü kurar | `/home`, `/tpa`, `/spawn`, `/tpr` (rastgele ışınlanma) |
| Altın kürekle arazi koruması (claim) | Ekonomi (₺), `/pay`, `/sell`, `/baltop` |
| Oyuncuların sandıkla kurduğu marketler | Günlük kit, VIP kiti, rütbeler (Oyuncu / VIP / Mod / Admin) |
| Otur/uzan, Bedrock arkadaşlarla oynama | Tab listesi + yan skor tablosu |

- **Daha "saf SMP" istersen:** `server/setup/plugins.json` içinde QuickShop'u kapat, Essentials'ta `/tpr` ve kit yetkilerini verme.
- **Daha "survival sunucusu" istersen:** `plugins.json` içinde **AuraSkills**'i aç (mcMMO tarzı yetenekler).

---

## Gereksinimler

- **Java 25 veya daha yenisi** → <https://adoptium.net/> (JDK 25, kurarken *Add to PATH* işaretli olsun)
- Sunucuya ayırabileceğin **en az 4 GB RAM**
- Arkadaşların dışarıdan girecekse modemde açılacak portlar:
  - `25565` **TCP** → Java oyuncuları
  - `19132` **UDP** → Bedrock oyuncuları

## Hızlı başlangıç

1. Bu repoyu indir (yeşil **Code → Download ZIP** ya da `git clone`).
2. Başlat:
   - **Windows:** `server/start.bat` dosyasına çift tıkla.
   - **Linux / macOS:** `cd server && ./start.sh`
3. İlk açılışta:
   - Paper (`server.jar`) ve eksik eklentiler resmi sitelerinden **otomatik indirilir**,
   - Minecraft EULA'yı kabul edip etmediğin sorulur (`e` yaz),
   - sunucu açılır.
4. Konsolda `Done` yazısını görünce [`server/setup/ilk-kurulum-komutlari.txt`](server/setup/ilk-kurulum-komutlari.txt) içindeki komutları konsola yapıştır
   (rütbeler, yetkiler, kendini admin yapma, dünya ön-oluşturma, spawn koruması).
5. Oyunda `localhost` (aynı bilgisayar) ya da bilgisayarının IP'si ile bağlan.

RAM miktarını `start.bat` içindeki `set RAM=4G` satırından değiştirebilirsin (Linux'ta `RAM=6G ./start.sh`).

---

## Eklentiler

`server/setup/plugins.json` listesindeki eklentiler otomatik indirilir ve güncellenir.

| Eklenti | Ne işe yarar |
|---|---|
| **LuckPerms** | Rütbe ve yetki sistemi. Tarayıcıda düzenlemek için: `/lp editor` |
| **EssentialsX** (+ Chat, Spawn) | `/home` `/sethome` `/tpa` `/spawn` `/warp` `/back` `/kit` `/msg` `/pay` `/bal` `/sell` `/tpr` ve ekonomi |
| **VaultUnlocked** | Ekonomi köprüsü (Vault'un güncel hâli) |
| **WorldEdit** + **WorldGuard** | Spawn'ı düzenleme ve koruma |
| **CoreProtect** | Blok/sandık kayıtları, grief geri alma: `/co inspect`, `/co rollback` |
| **HuskClaims** | Oyuncuların altın kürekle arazisini koruması, `/trust <oyuncu>` ile arkadaş ekleme |
| **ViaVersion** + **ViaBackwards** | Farklı Minecraft sürümleriyle giriş |
| **Geyser** + **Floodgate** | Bedrock (telefon/konsol) oyuncular Java hesabı olmadan girebilir |
| **PlaceholderAPI** | Skor tablosunda para, rütbe vb. göstermek için |
| **TAB** | Tab listesi başlığı, rütbe önekleri, yan skor tablosu (`/sb` ile gizlenir) |
| **QuickShop-Hikari** | Sandığa eşya koyup tabela ile market kurma |
| **GSit** | `/sit` `/lay` `/crawl`, merdivene sağ tıklayıp oturma |
| **DecentHolograms** | Spawn'a yüzen yazılar (`/dh create ...`) |
| **Chunky** | Dünyayı önceden oluşturur, gezerken lag olmaz |

**İsteğe bağlı (kapalı, açmak için `"enabled": true` yap):**

| Eklenti | Ne işe yarar |
|---|---|
| BlueMap | Tarayıcıdan açılan 3D canlı harita (TCP `8100`) |
| AuraSkills | mcMMO tarzı yetenek/seviye sistemi |
| GrimAC | Hile koruması (anticheat) |
| DiscordSRV | Oyun sohbetini Discord kanalına bağlar (bot token gerekir) |

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
| `server/server.properties` | MOTD, 50 oyuncu, normal zorluk, spawn koruması WorldGuard'a bırakıldı, `allow-flight=true` (otururken/lag'da atılmasın) |
| `server/plugins/Essentials/config.yml` | Dil **Türkçe**, para birimi **₺**, başlangıç parası **100₺**, ışınlanma 3 sn bekleme + 5 sn bekleme süresi, sohbet formatı `[Rütbe] İsim » mesaj` |
| `server/plugins/Essentials/kits.yml` | `baslangic` (ilk girişte otomatik, arazi küreği dahil), `gunluk`, `vip` |
| `server/plugins/Essentials/motd.txt`, `rules.txt` | Türkçe giriş mesajı ve kurallar (`/rules`) |
| `server/plugins/TAB/config.yml` | "SKY SURVIVAL" tab başlığı, yan skor tablosu (rütbe, para, ping), isim altında can |
| `server/plugins/GSit/config.yml` | Dil Türkçe |

`enforce-secure-profile=false`: Bedrock (Geyser) ve farklı sürümlerle giren oyuncuların sohbette atılmaması için kapalı.

Geyser ilk açılışta `plugins/Geyser-Spigot/config.yml` dosyasını oluşturur; Floodgate kurulu olduğu için orada `auth-type` değerinin `floodgate` olduğundan emin ol.

---

## Hosting firmasında (Pterodactyl vb. panel) çalıştırmak

1. Panelde sunucu türü olarak **Paper 26.1.2**, Java sürümü olarak **Java 25** seç.
2. Kendi bilgisayarında bir kez `server` klasöründe `java setup/Setup.java` çalıştır (eksik eklentiler insin).
3. `server` klasörünün **içindekileri** panelin dosya yöneticisine yükle.

---

## Klasör yapısı

```
server/
├── start.bat / start.sh       → sunucuyu başlatır (önce eksikleri indirir)
├── update.bat / update.sh     → Paper ve eklentileri günceller
├── server.properties          → temel sunucu ayarları
├── plugins/                   → eklentiler ve ayarları
└── setup/
    ├── plugins.json           → eklenti listesi (aç/kapat buradan)
    ├── lock.json              → kurulu sürümler (otomatik yazılır)
    ├── Setup.java             → indirme/güncelleme aracı
    └── ilk-kurulum-komutlari.txt → ilk açılışta konsola yapıştırılacak komutlar
```

## Sonraki adımlar için fikirler

- Discord sunucusu + **DiscordSRV** ile sohbet köprüsü
- **BlueMap** ile web'den canlı harita
- Otomatik yedekleme (dünya klasörlerinin düzenli kopyası)
- Ayrı bir **SkyBlock / OneBlock** dünyası (BentoBox + BSkyBlock veya AOneBlock)
- Görevler, sezonluk etkinlikler, spawn yapısı
