// Sky Survival kurulum araci.
//
// Paper sunucu dosyasini (server.jar) ve setup/plugins.json icindeki eklentileri
// resmi kaynaklarindan (PaperMC, Modrinth, GitHub, GeyserMC) indirir. Indirilen
// surumler setup/lock.json dosyasina yazilir; boylece her calistirmada ayni
// surumler kullanilir ve yalnizca eksik dosyalar indirilir. Paper ayar dosyalari olustuktan
// sonraki ilk calistirmada Paper'in X-Ray korumasini da bir kez acar.
//
// Kullanim (server klasorunun icinden, ek bir program gerekmez - sadece Java):
//   java setup/Setup.java            eksik olanlari indir
//   java setup/Setup.java --update   Paper'i ve tum eklentileri en yeni surume guncelle
//
// Cikis kodlari: 0 = her sey tamam, 1 = bazi eklentiler indirilemedi (sunucu yine acilabilir),
//                2 = server.jar yok ya da Java surumu yetersiz (sunucu acilamaz).

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Setup {
    static final String USER_AGENT = "Nasow-SkySurvival-Setup/1.0 (+https://github.com/cangcmz0/Nasow)";
    static final Path SERVER_DIR = Path.of("").toAbsolutePath();
    static final Path MANIFEST = SERVER_DIR.resolve("setup").resolve("plugins.json");
    static final Path LOCK = SERVER_DIR.resolve("setup").resolve("lock.json");
    static final Path PLUGINS_DIR = SERVER_DIR.resolve("plugins");
    static final Path SERVER_JAR = SERVER_DIR.resolve("server.jar");
    static final String PAPER_ID = "paper";

    static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    static final HttpClient HTTP_NO_REDIRECT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    record Context(String minecraft, List<String> gameVersions) {}

    /** Indirilecek tek bir dosya. hashAlgorithm/expectedHash bos olabilir (GitHub hash vermez). */
    record Resolved(String version, String url, String fileName, String hashAlgorithm, String expectedHash) {}

    public static void main(String[] args) throws Exception {
        boolean update = Arrays.asList(args).contains("--update");

        if (!Files.exists(MANIFEST)) {
            System.out.println("[HATA] " + MANIFEST + " bulunamadi. Bu komutu server klasorunun icinden calistir.");
            System.exit(2);
        }
        Map<String, Object> manifest = obj(Json.parse(Files.readString(MANIFEST)));
        Map<String, Object> lock = Files.exists(LOCK) ? obj(Json.parse(Files.readString(LOCK))) : new LinkedHashMap<>();

        String minecraft = str(manifest, "minecraft");
        List<String> gameVersions = new ArrayList<>();
        for (Object v : list(manifest.getOrDefault("gameVersions", List.of(minecraft)))) {
            gameVersions.add((String) v);
        }
        Context ctx = new Context(minecraft, gameVersions);

        int requiredJava = ((Number) manifest.getOrDefault("java", 21L)).intValue();
        int currentJava = Runtime.version().feature();
        if (currentJava < requiredJava) {
            System.out.println();
            System.out.println("[HATA] Minecraft " + minecraft + " icin Java " + requiredJava + " veya daha yenisi gerekiyor.");
            System.out.println("       Su an kullanilan Java surumu: " + currentJava);
            System.out.println("       Indir: https://adoptium.net/  (JDK " + requiredJava + " secip kur, sonra tekrar dene)");
            System.exit(2);
        }

        Files.createDirectories(PLUGINS_DIR);
        System.out.println("== " + manifest.getOrDefault("server", "Sunucu") + " kurulumu (Minecraft " + minecraft + ")"
                + (update ? " - guncelleme modu" : "") + " ==");

        List<String> failed = new ArrayList<>();
        Map<String, Object> paperSpec = new LinkedHashMap<>();
        paperSpec.put("source", "paper");
        install(PAPER_ID, "Paper " + minecraft, paperSpec, lock, update, ctx, failed);

        List<String> wanted = new ArrayList<>();
        wanted.add(PAPER_ID);
        for (Object o : list(manifest.get("plugins"))) {
            Map<String, Object> plugin = obj(o);
            if (Boolean.FALSE.equals(plugin.get("enabled"))) {
                continue;
            }
            String id = str(plugin, "id");
            wanted.add(id);
            install(id, (String) plugin.getOrDefault("name", id), plugin, lock, update, ctx, failed);
        }

        // plugins.json'dan silinen ya da "enabled": false yapilan eklentileri kaldir.
        // "_" ile baslayan kayitlar eklenti degil, kurulum aracinin kendi notlaridir.
        for (String id : new ArrayList<>(lock.keySet())) {
            if (!id.startsWith("_") && !wanted.contains(id)) {
                Map<String, Object> entry = obj(lock.remove(id));
                Files.deleteIfExists(PLUGINS_DIR.resolve(safeFileName(str(entry, "file"))));
                System.out.println("  [SIL]   " + entry.getOrDefault("name", id) + " kaldirildi (plugins.json'da kapali)");
            }
        }

        enablePaperAntiXray(lock);
        Files.writeString(LOCK, Json.write(lock) + System.lineSeparator());

        System.out.println();
        if (!Files.exists(SERVER_JAR)) {
            System.out.println("[HATA] server.jar indirilemedi. Internet baglantini kontrol edip tekrar dene.");
            System.exit(2);
        }
        if (!failed.isEmpty()) {
            System.out.println("[UYARI] Su eklentiler indirilemedi: " + String.join(", ", failed));
            System.out.println("        Sunucu bunlar olmadan da acilir; sonra tekrar calistirinca yeniden denenir.");
            System.exit(1);
        }
        System.out.println("Her sey hazir.");
    }

    static void install(String id, String name, Map<String, Object> spec, Map<String, Object> lock,
                        boolean update, Context ctx, List<String> failed) {
        Map<String, Object> entry = lock.containsKey(id) ? obj(lock.get(id)) : null;
        Path current = entry == null ? null : target(id, str(entry, "file"));
        try {
            if (!update && entry != null && Files.exists(current)) {
                System.out.println("  [TAMAM] " + name + " " + entry.get("version"));
                return;
            }
            // Kilit dosyasinda kayit varsa ayni surumu tekrar indir; yoksa (ya da --update ile) en yenisini bul.
            Resolved resolved = (!update && entry != null) ? fromLock(entry) : resolve(spec, ctx);
            if (entry != null && current != null && Files.exists(current)
                    && resolved.version().equals(entry.get("version"))) {
                System.out.println("  [TAMAM] " + name + " " + resolved.version() + " (guncel)");
                return;
            }

            Path dest = target(id, resolved.fileName());
            System.out.println("  [INDIR] " + name + " " + resolved.version() + " ...");
            String sha256 = download(resolved, dest);
            if (current != null && !current.equals(dest)) {
                Files.deleteIfExists(current);
            }

            Map<String, Object> newEntry = new LinkedHashMap<>();
            newEntry.put("name", name);
            newEntry.put("version", resolved.version());
            newEntry.put("file", resolved.fileName());
            newEntry.put("url", resolved.url());
            newEntry.put("sha256", sha256);
            lock.put(id, newEntry);
        } catch (Exception e) {
            failed.add(name);
            System.out.println("  [HATA]  " + name + ": " + e.getMessage());
        }
    }

    /**
     * Paper'in ayar dosyalari sunucunun ilk acilisinda olusur. Olustuktan sonraki ilk calistirmada
     * Paper'in dahili X-Ray korumasini bir kez acar. lock.json'daki "_settings" kaydi sayesinde
     * admin bu ayari sonradan kapatirsa tekrar acilmaz.
     */
    static void enablePaperAntiXray(Map<String, Object> lock) throws IOException {
        Path file = SERVER_DIR.resolve("config").resolve("paper-world-defaults.yml");
        Map<String, Object> settings = lock.containsKey("_settings") ? obj(lock.get("_settings")) : new LinkedHashMap<>();
        if (settings.containsKey("anti-xray") || !Files.exists(file)) {
            return;
        }

        List<String> lines = new ArrayList<>(Files.readAllLines(file));
        String result = "bulunamadi";
        for (int block = 0; block < lines.size() && result.equals("bulunamadi"); block++) {
            if (!lines.get(block).trim().equals("anti-xray:")) {
                continue;
            }
            int blockIndent = indentOf(lines.get(block));
            int childIndent = -1;
            for (int i = block + 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }
                int indent = indentOf(line);
                if (indent <= blockIndent) {
                    break;
                }
                if (childIndent < 0) {
                    childIndent = indent;
                }
                if (indent == childIndent && line.trim().equals("enabled: false")) {
                    lines.set(i, line.replace("enabled: false", "enabled: true"));
                    result = "acildi";
                    break;
                }
                if (indent == childIndent && line.trim().equals("enabled: true")) {
                    result = "zaten-acik";
                    break;
                }
            }
        }

        if (result.equals("acildi")) {
            Files.write(file, lines);
            System.out.println("  [AYAR]  Paper X-Ray korumasi acildi (config/paper-world-defaults.yml)");
        } else if (result.equals("bulunamadi")) {
            System.out.println("  [UYARI] Paper X-Ray ayari bulunamadi. Elle acmak icin config/paper-world-defaults.yml"
                    + " icinde anticheat > anti-xray > enabled: true yap.");
        }
        settings.put("anti-xray", result);
        lock.put("_settings", settings);
    }

    static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    static Path target(String id, String fileName) {
        return id.equals(PAPER_ID) ? SERVER_JAR : PLUGINS_DIR.resolve(safeFileName(fileName));
    }

    static Resolved fromLock(Map<String, Object> entry) {
        return new Resolved(str(entry, "version"), str(entry, "url"), str(entry, "file"), "SHA-256", str(entry, "sha256"));
    }

    static Resolved resolve(Map<String, Object> spec, Context ctx) throws Exception {
        String source = str(spec, "source");
        switch (source) {
            case "paper": {
                List<Object> builds = list(getJson("https://fill.papermc.io/v3/projects/paper/versions/"
                        + enc(ctx.minecraft()) + "/builds"));
                if (builds.isEmpty()) {
                    throw new IOException("Paper " + ctx.minecraft() + " icin build bulunamadi");
                }
                // Liste en yeniden eskiye siralidir; varsa kararli (STABLE) build tercih edilir.
                Map<String, Object> build = obj(builds.get(0));
                for (Object b : builds) {
                    if ("STABLE".equals(obj(b).get("channel"))) {
                        build = obj(b);
                        break;
                    }
                }
                Map<String, Object> file = obj(obj(build.get("downloads")).get("server:default"));
                String sha256 = str(obj(file.get("checksums")), "sha256");
                return new Resolved(ctx.minecraft() + "-" + build.get("id"), str(file, "url"), str(file, "name"), "SHA-256", sha256);
            }
            case "modrinth": {
                String project = str(spec, "project");
                Object loaders = spec.getOrDefault("loaders", List.of("paper", "bukkit", "spigot"));
                String url = "https://api.modrinth.com/v2/project/" + enc(project) + "/version"
                        + "?loaders=" + enc(Json.write(loaders))
                        + "&game_versions=" + enc(Json.write(ctx.gameVersions()))
                        + "&include_changelog=false";
                List<Object> versions = list(getJson(url));
                if (versions.isEmpty()) {
                    throw new IOException("Modrinth'te Minecraft " + ctx.minecraft() + " icin uyumlu surum yok (" + project + ")");
                }
                Map<String, Object> chosen = obj(versions.get(0));
                for (Object v : versions) {
                    if ("release".equals(obj(v).get("version_type"))) {
                        chosen = obj(v);
                        break;
                    }
                }
                List<Object> files = list(chosen.get("files"));
                Map<String, Object> file = obj(files.get(0));
                for (Object f : files) {
                    if (Boolean.TRUE.equals(obj(f).get("primary"))) {
                        file = obj(f);
                        break;
                    }
                }
                String sha512 = str(obj(file.get("hashes")), "sha512");
                return new Resolved(str(chosen, "version_number"), str(file, "url"), str(file, "filename"), "SHA-512", sha512);
            }
            case "github": {
                String repo = str(spec, "repo");
                String tag = spec.containsKey("tag") ? str(spec, "tag") : latestGitHubTag(repo);
                String version = tag.startsWith("v") ? tag.substring(1) : tag;
                String asset = str(spec, "asset").replace("{tag}", tag).replace("{version}", version);
                String url = "https://github.com/" + repo + "/releases/download/" + tag + "/" + asset;
                return new Resolved(version, url, asset, null, null);
            }
            case "geysermc": {
                String project = str(spec, "project");
                String platform = (String) spec.getOrDefault("platform", "spigot");
                String base = "https://download.geysermc.org/v2/projects/" + enc(project);
                Map<String, Object> build = obj(getJson(base + "/versions/latest/builds/latest"));
                String version = str(build, "version");
                String buildNumber = String.valueOf(build.get("build"));
                Map<String, Object> file = obj(obj(build.get("downloads")).get(platform));
                String url = base + "/versions/" + enc(version) + "/builds/" + buildNumber + "/downloads/" + enc(platform);
                return new Resolved(version + "-b" + buildNumber, url, str(file, "name"), "SHA-256", str(file, "sha256"));
            }
            default:
                throw new IOException("bilinmeyen kaynak: " + source);
        }
    }

    /** GitHub API limitine takilmamak icin en son surumu "releases/latest/download" yonlendirmesinden okur. */
    static String latestGitHubTag(String repo) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://github.com/" + repo + "/releases/latest/download/setup-probe"))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<Void> response = HTTP_NO_REDIRECT.send(request, HttpResponse.BodyHandlers.discarding());
        String location = response.headers().firstValue("location").orElse("");
        String marker = "/releases/download/";
        int start = location.indexOf(marker);
        int end = location.lastIndexOf("/setup-probe");
        if (response.statusCode() / 100 != 3 || start < 0 || end <= start) {
            throw new IOException("GitHub'da yayinlanmis surum bulunamadi (" + repo + ", HTTP " + response.statusCode() + ")");
        }
        return URLDecoder.decode(location.substring(start + marker.length(), end), StandardCharsets.UTF_8);
    }

    /** Dosyayi indirir, dogrular ve hedefe tasir. Kilit dosyasina yazilacak SHA-256 degerini dondurur. */
    static String download(Resolved resolved, Path dest) throws Exception {
        Path part = dest.resolveSibling(dest.getFileName() + ".part");
        HttpRequest request = HttpRequest.newBuilder(URI.create(resolved.url()))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();
        HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(part));
        try {
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " (" + resolved.url() + ")");
            }
            if (!isZip(part)) {
                throw new IOException("indirilen dosya bir .jar degil (" + resolved.url() + ")");
            }
            if (resolved.hashAlgorithm() != null && resolved.expectedHash() != null) {
                String actual = hash(part, resolved.hashAlgorithm());
                if (!actual.equalsIgnoreCase(resolved.expectedHash())) {
                    throw new IOException("dosya dogrulanamadi, " + resolved.hashAlgorithm() + " uyusmuyor");
                }
            }
            String sha256 = hash(part, "SHA-256");
            try {
                Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            return sha256;
        } finally {
            Files.deleteIfExists(part);
        }
    }

    static Object getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " (" + url + ")");
        }
        return Json.parse(response.body());
    }

    static boolean isZip(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(2);
            return header.length == 2 && header[0] == 'P' && header[1] == 'K';
        }
    }

    static String hash(Path file, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Uzak kaynaktan gelen dosya adinin plugins klasorunun disina cikmasini engeller. */
    static String safeFileName(String name) {
        if (name == null || !name.endsWith(".jar") || name.contains("/") || name.contains("\\") || name.startsWith(".")) {
            throw new IllegalArgumentException("gecersiz dosya adi: " + name);
        }
        return name;
    }

    static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException("'" + key + "' alani eksik");
        }
        return s;
    }

    /** Harici kutuphane gerekmesin diye kucuk bir JSON okuyucu/yazici. */
    static final class Json {
        private final String text;
        private int pos;

        private Json(String text) {
            this.text = text.startsWith("\uFEFF") ? text.substring(1) : text; // Not Defteri'nin ekledigi BOM
        }

        static Object parse(String text) {
            Json json = new Json(text);
            json.skipWhitespace();
            Object value = json.readValue();
            json.skipWhitespace();
            if (json.pos != json.text.length()) {
                throw json.error("fazladan karakter");
            }
            return value;
        }

        private Object readValue() {
            if (pos >= text.length()) {
                throw error("beklenmedik dosya sonu");
            }
            char c = text.charAt(pos);
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                    readLiteral("true");
                    return Boolean.TRUE;
                case 'f':
                    readLiteral("false");
                    return Boolean.FALSE;
                case 'n':
                    readLiteral("null");
                    return null;
                default:
                    return readNumber();
            }
        }

        private Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, readValue());
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                    continue;
                }
                expect('}');
                return map;
            }
        }

        private List<Object> readArray() {
            List<Object> values = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return values;
            }
            while (true) {
                skipWhitespace();
                values.add(readValue());
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                    continue;
                }
                expect(']');
                return values;
            }
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= text.length()) {
                    throw error("kapanmamis metin");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char escaped = text.charAt(pos++);
                switch (escaped) {
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> sb.append(escaped);
                }
            }
        }

        private Object readNumber() {
            int start = pos;
            while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            String number = text.substring(start, pos);
            if (number.isEmpty()) {
                throw error("gecersiz deger");
            }
            try {
                return Long.parseLong(number);
            } catch (NumberFormatException e) {
                return Double.parseDouble(number);
            }
        }

        private void readLiteral(String literal) {
            if (!text.startsWith(literal, pos)) {
                throw error("gecersiz deger");
            }
            pos += literal.length();
        }

        private void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private boolean peek(char c) {
            return pos < text.length() && text.charAt(pos) == c;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw error("'" + c + "' bekleniyordu");
            }
            pos++;
        }

        private IllegalArgumentException error(String message) {
            int line = 1;
            for (int i = 0; i < Math.min(pos, text.length()); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                }
            }
            return new IllegalArgumentException("JSON hatasi, satir " + line + ": " + message);
        }

        static String write(Object value) {
            StringBuilder sb = new StringBuilder();
            write(sb, value, "");
            return sb.toString();
        }

        private static void write(StringBuilder sb, Object value, String indent) {
            if (value instanceof Map<?, ?> map) {
                if (map.isEmpty()) {
                    sb.append("{}");
                    return;
                }
                String inner = indent + "  ";
                sb.append("{\n");
                int i = 0;
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    sb.append(inner);
                    writeString(sb, String.valueOf(e.getKey()));
                    sb.append(": ");
                    write(sb, e.getValue(), inner);
                    sb.append(++i < map.size() ? ",\n" : "\n");
                }
                sb.append(indent).append('}');
            } else if (value instanceof List<?> items) {
                sb.append('[');
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    write(sb, items.get(i), indent);
                }
                sb.append(']');
            } else if (value instanceof String s) {
                writeString(sb, s);
            } else {
                sb.append(value);
            }
        }

        private static void writeString(StringBuilder sb, String s) {
            sb.append('"');
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            sb.append('"');
        }
    }
}
