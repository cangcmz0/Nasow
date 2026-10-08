# SkyCore — Sky Survival'a özel eklenti

Yurtdışı ve Türk survival sunucularında sevilen özelliklerin tek, hafif ve Türkçe bir eklentide toplanmış hâli.
Hazır derlenmiş dosya: `server/plugins/SkyCore-1.0.0.jar`. Ayarlar: `server/plugins/SkyCore/config.yml`
(ilk açılışta oluşur; her özellik `aktif: true/false` ile açılıp kapatılır, `/skycore reload` ile yenilenir).

## Özellikler

| Özellik | Ne yapar | Komut |
|---|---|---|
| **Savaş modu** (combat log) | PvP'ye giren iki oyuncu 15 sn "savaşta" sayılır; ekranda geri sayım çıkar. Bu sürede `/spawn`, `/home`, `/tpa`, `/warp`, `/tpr`, `/back` gibi kaçış komutları çalışmaz. Savaştayken oyundan çıkan oyuncu ölür ve eşyaları yere düşer. Yetkili tarafından atılan oyuncu cezalandırılmaz. | — |
| **Sohbet oyunları** | 10 dakikada bir sohbete soru gelir: kelimeyi ilk yazan, işlemi ilk çözen ya da karışık harflerden kelimeyi ilk bulan para kazanır. Türkçe karakter farkı önemsenmez (kılıç = kilic). | `/skycore oyun` (hemen başlat) |
| **Banknot** | Parayı kağıda çevirir; sağ tıklayınca geri hesaba yatar (Shift + sağ tık: hepsi). Takas ve sandıkta para saklamak için. Değer, eşyanın gizli verisinde tutulur, taklit edilemez. | `/banknot <miktar>` (`5k`, `1.000`, `2,5` yazılabilir) |
| **Kelle avı** (bounty) | Bir oyuncunun kellesine para konur, onu öldüren alır. Aynı IP'deki oyuncular birbirinden ödül alamaz (yan hesap hilesi). | `/kelle koy <oyuncu> <miktar>`, `/kelle liste`, `/kelle <oyuncu>` |
| **Günlük ödül serisi** | Her gün `/odul` ile para; üst üste her gün gelince ödül artar (100₺ → 150₺ → ... → en fazla 1000₺), bir gün kaçırılırsa seri sıfırlanır. Girişte hatırlatır. | `/odul` |
| **Aktiflik ödülü** | AFK olmadan oynanan her 60 dakika için 500₺. 5 dakikadan uzun kıpırdamayan oyuncu sayılmaz. | — |
| **Otomatik duyurular** | 5 dakikada bir sırayla ipucu mesajları. | `/skycore duyuru` |
| **Kafa düşürme** | PvP'de ölen oyuncunun kafası (skiniyle) yere düşer; üzerinde avcının adı ve tarih yazar. | — |
| **Ölüm koordinatı** | Ölen oyuncuya öldüğü yerin koordinatı yazılır. | — |
| **Hoş geldin başlığı** | Girişte (AuthMe şifresinden sonra) büyük "SKY SURVIVAL" başlığı ve ses; ilk girişte farklı yazı. | — |
| **Bilgi komutları** | Discord ve site adresleri (`config.yml` → `bilgi`). | `/discord`, `/site` |

Yönetim: `/skycore reload`, `/skycore oyun`, `/skycore duyuru` (yetki: `skycore.admin`, OP'lerde var).

Yetkiler: `skycore.banknot`, `skycore.kelle`, `skycore.odul` herkese açık.
`skycore.savas.bypass` verilen oyuncu savaş moduna girmez (LuckPerms'te `*` yetkisi olan admin grubunda otomatik var).

Gerekenler: Vault (VaultUnlocked) + EssentialsX ekonomisi. AuthMe kuruluysa onunla uyumlu çalışır
(şifre girmeyen oyuncu sohbet oyununa katılamaz, aktiflik ödülü almaz; karşılama şifreden sonra gösterilir).

## Geliştiriciler için

Kaynak kod: `src/main/java/net/skysurvival/skycore/`. Testler: `src/test/java/` (MockBukkit, 14 test).

- Eklenti gerçek **Paper 26.1.2 API** kaynağına karşı derlendi (`--release 21`, uyarısız).
- MockBukkit henüz 26.x'i desteklemediği için testler **Paper 1.21.11 API**'siyle çalıştırıldı; SkyCore'un kullandığı API iki sürümde aynı. 14 testin hepsi geçti.
- Kendin derlemek için `gradle build` (JDK 25 gerekir). `build.gradle.kts` kurulum ortamında çalıştırılmadı.
