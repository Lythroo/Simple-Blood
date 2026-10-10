package com.simpleblood;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class SkinColours {

    public static final int MARKER_X = 16, MARKER_Y = 48;
    public static final int MARKER = 0xFFFF00FF;
    public static final int NONE = -1;

    private static final int MAX_BYTES = 1 << 20;
    private static final long RETRY_MS = TimeUnit.MINUTES.toMillis(5);

    private record Read(int colour, long retryAt) {}

    private static final Map<String, Read> READS = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService READER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Simple Blood skin reader");
        t.setDaemon(true);
        return t;
    });
    private static HttpClient http;

    private SkinColours() {}

    public static int colourOf(Entity player) {
        if (!(player instanceof AbstractClientPlayer p)) return NONE;
        String url;
        try {
            url = urlOf(p);
        } catch (RuntimeException e) {
            return NONE;
        }
        if (url == null) return NONE;
        int colour = colourAt(url);
        return colour == READING ? NONE : colour;
    }

    public static final int READING = -2;
    private static CompletableFuture<String> profileUrl;

    public static int ownColour() {
        Integer uploaded = uploadedColour;
        if (uploaded != null) return uploaded;
        Minecraft mc = Minecraft.getInstance();
        String url;
        try {
            if (mc.player != null) {
                url = urlOf(mc.player);
            } else {
                if (profileUrl == null) profileUrl = profileSkinUrl(mc);
                if (!profileUrl.isDone()) return READING;
                url = profileUrl.getNow(null);
            }
        } catch (RuntimeException e) {
            return NONE;
        }
        return url == null ? NONE : colourAt(url);
    }

    public static boolean justUploaded() {
        return uploadedColour != null;
    }

    private static boolean ownChecked;
    private static int ownCheckTick;

    public static void tickOwn() {
        if (ownChecked || ++ownCheckTick % 20 != 0) return;
        int carried = ownColour();
        if (carried == READING) return;
        ownChecked = true;
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (carried < 0 || carried == cfg.player.skinColourAdopted) return;
        cfg.player.skinColourAdopted = carried;
        cfg.player.clientPlayerBloodColor = carried;
        SimpleBloodConfig.save(cfg);
        SimpleBlood.LOGGER.info("Simple Blood: took the blood colour from your skin, #{}", String.format("%06X", carried));
    }

    private static void adopted(int colour) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        cfg.player.skinColourAdopted = colour;
        SimpleBloodConfig.save(cfg);
    }

    private static CompletableFuture<String> profileSkinUrl(Minecraft mc) {
        //? if 1.20.1 {
        /*com.mojang.authlib.GameProfile profile = mc.getUser().getGameProfile();
        return CompletableFuture.supplyAsync(() -> {
                    com.mojang.authlib.minecraft.MinecraftProfileTexture skin = legacySkin(mc, profile);
                    return skin != null ? skin.getUrl() : null;
                }, net.minecraft.Util.backgroundExecutor())
                .exceptionally(e -> null);
        *///?} elif 1.21.1 {
        /*return mc.getSkinManager().getOrLoad(mc.getGameProfile())
                .thenApply(net.minecraft.client.resources.PlayerSkin::textureUrl)
                .exceptionally(e -> null);
        *///?} else {
        return mc.getSkinManager().get(mc.getGameProfile())
                .thenApply(found -> found.map(skin -> skin.body() instanceof net.minecraft.core.ClientAsset.DownloadedTexture t ? t.url() : null).orElse(null))
                .exceptionally(e -> null);
        //?}
    }

    private static int colourAt(String url) {
        Read read = READS.get(url);
        if (read != null && (read.retryAt == 0 || System.currentTimeMillis() < read.retryAt)) return read.colour;
        if (PENDING.add(url)) {
            READER.execute(() -> {
                try {
                    READS.put(url, new Read(carried(download(url)), 0));
                } catch (Exception e) {
                    READS.put(url, new Read(NONE, System.currentTimeMillis() + RETRY_MS));
                    SimpleBlood.LOGGER.debug("Simple Blood could not read a skin ({}): {}", url, e.toString());
                } finally {
                    PENDING.remove(url);
                }
            });
        }
        return read == null ? READING : read.colour;
    }

    public enum Result {
        SAVED,
        UPLOADED,
        ALREADY_THERE,
        NO_COLOUR,
        NOT_LOGGED_IN,
        TOO_SOON,
        FAILED
    }

    private static final String UPLOAD_URL = "https://api.minecraftservices.com/minecraft/profile/skins";
    private static final long UPLOAD_GAP_MS = TimeUnit.SECONDS.toMillis(60);
    private static volatile long lastUpload;
    private static volatile Integer uploadedColour;

    public static Path savedSkin() {
        return savedSkin(false);
    }

    private static Path savedSkin(boolean without) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("simpleblood")
                .resolve(without ? "skin-without-blood.png" : "skin-with-blood.png");
    }

    public static void showSavedSkin() {
        //? if 1.20.1 {
        /*net.minecraft.Util.getPlatform().openFile(savedSkin().getParent().toFile());
        *///?} elif 1.21.1 {
        /*net.minecraft.Util.getPlatform().openPath(savedSkin().getParent());
        *///?} elif <26.3 {
        net.minecraft.util.Util.getPlatform().openPath(savedSkin().getParent());
        //?} else {
        /*com.mojang.blaze3d.Blaze3D.openPath(savedSkin().getParent());
        *///?}
    }

    public static void putInOwnSkin(int colour, boolean upload, Consumer<Result> done) {
        int rgb = colour == NONE ? NONE : colour & 0xFFFFFF;
        Integer uploaded = uploadedColour;
        if (upload && uploaded != null && uploaded == rgb) {
            done.accept(Result.ALREADY_THERE);
            return;
        }
        if (upload && System.currentTimeMillis() - lastUpload < UPLOAD_GAP_MS) {
            done.accept(Result.TOO_SOON);
            return;
        }
        Callable<OwnSkin> skin;
        String token;
        try {
            skin = ownSkin();
            token = upload ? Minecraft.getInstance().getUser().getAccessToken() : null;
        } catch (RuntimeException e) {
            SimpleBlood.LOGGER.warn("Simple Blood could not find your skin", e);
            done.accept(Result.FAILED);
            return;
        }
        if (upload) lastUpload = System.currentTimeMillis();
        READER.execute(() -> {
            Result result;
            try {
                OwnSkin own = skin.call();
                result = putInSkin(own.png, rgb);
                if (result == Result.SAVED && upload) {
                    result = upload(Files.readAllBytes(savedSkin(rgb == NONE)), own.slim, token);
                    if (result == Result.UPLOADED) {
                        uploadedColour = rgb;
                        Minecraft.getInstance().execute(() -> adopted(rgb));
                    }
                }
            } catch (Exception e) {
                SimpleBlood.LOGGER.warn("Simple Blood could not put your colour in your skin", e);
                result = Result.FAILED;
            }
            Result r = result;
            Minecraft.getInstance().execute(() -> done.accept(r));
        });
    }

    private static Result putInSkin(byte[] png, int colour) throws Exception {
        try (NativeImage image = NativeImage.read(png)) {
            if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                throw new IOException("unexpected skin size " + image.getWidth() + "x" + image.getHeight());
            }
            int[] argb = new int[64 * 64];
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < 64; x++) argb[y * 64 + x] = get(image, x, y);
            }
            if (image.getHeight() == 32) {
                if (colour == NONE) return Result.NO_COLOUR;
                widen(argb);
            } else if (carried(argb) == colour) {
                return colour == NONE ? Result.NO_COLOUR : Result.ALREADY_THERE;
            }
            if (colour == NONE) {
                argb[MARKER_Y * 64 + MARKER_X] = 0;
                argb[MARKER_Y * 64 + MARKER_X + 1] = 0;
            } else {
                argb[MARKER_Y * 64 + MARKER_X] = MARKER;
                argb[MARKER_Y * 64 + MARKER_X + 1] = 0xFF000000 | colour;
            }

            Path out = savedSkin(colour == NONE);
            Files.createDirectories(out.getParent());
            try (NativeImage copy = new NativeImage(64, 64, true)) {
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 64; x++) set(copy, x, y, argb[y * 64 + x]);
                }
                copy.writeToFile(out);
            }
            return Result.SAVED;
        }
    }

    private static Result upload(byte[] png, boolean slim, String token) throws IOException, InterruptedException {
        if (token == null || token.isBlank() || token.length() < 32) return Result.NOT_LOGGED_IN;
        String boundary = "SimpleBlood" + Long.toHexString(System.nanoTime());
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"variant\"\r\n\r\n"
                + (slim ? "slim" : "classic") + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n";
        body.write(head.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        body.write(png);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        HttpRequest request = HttpRequest.newBuilder(URI.create(UPLOAD_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status / 100 == 2) return Result.UPLOADED;
        SimpleBlood.LOGGER.warn("Simple Blood could not upload your skin: HTTP {} {}", status, response.body());
        if (status == 401 || status == 403) return Result.NOT_LOGGED_IN;
        if (status == 429) return Result.TOO_SOON;
        return Result.FAILED;
    }

    private record OwnSkin(byte[] png, boolean slim) {}

    private static Callable<OwnSkin> ownSkin() {
        Minecraft mc = Minecraft.getInstance();
        //? if 1.20.1 {
        /*com.mojang.authlib.GameProfile profile = mc.player != null ? mc.player.getGameProfile() : mc.getUser().getGameProfile();
        return () -> {
            com.mojang.authlib.minecraft.MinecraftProfileTexture skin = legacySkin(mc, profile);
            if (skin != null) return new OwnSkin(download(skin.getUrl()), "slim".equals(skin.getMetadata("model")));
            boolean slim = "slim".equals(net.minecraft.client.resources.DefaultPlayerSkin.getSkinModelName(profile.getId()));
            try (InputStream in = mc.getResourceManager().open(net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(profile.getId()))) {
                return new OwnSkin(in.readAllBytes(), slim);
            }
        };
        *///?} elif 1.21.1 {
        /*CompletableFuture<net.minecraft.client.resources.PlayerSkin> pending = mc.player != null
                ? CompletableFuture.completedFuture(mc.player.getSkin())
                : mc.getSkinManager().getOrLoad(mc.getGameProfile());
        return () -> {
            net.minecraft.client.resources.PlayerSkin skin = pending.get(20, TimeUnit.SECONDS);
            boolean slim = skin.model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
            if (skin.textureUrl() != null) return new OwnSkin(download(skin.textureUrl()), slim);
            try (InputStream in = mc.getResourceManager().open(skin.texture())) {
                return new OwnSkin(in.readAllBytes(), slim);
            }
        };
        *///?} else {
        com.mojang.authlib.GameProfile profile = mc.getGameProfile();
        CompletableFuture<net.minecraft.world.entity.player.PlayerSkin> pending = mc.player != null
                ? CompletableFuture.completedFuture(mc.player.getSkin())
                : mc.getSkinManager().get(profile).thenApply(found ->
                        found.orElseGet(() -> net.minecraft.client.resources.DefaultPlayerSkin.get(profile)));
        return () -> {
            net.minecraft.world.entity.player.PlayerSkin skin = pending.get(20, TimeUnit.SECONDS);
            boolean slim = skin.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
            if (skin.body() instanceof net.minecraft.core.ClientAsset.DownloadedTexture downloaded) {
                return new OwnSkin(download(downloaded.url()), slim);
            }
            try (InputStream in = mc.getResourceManager().open(skin.body().texturePath())) {
                return new OwnSkin(in.readAllBytes(), slim);
            }
        };
        //?}
    }

    private static void widen(int[] argb) {
        boolean solidHat = true;
        for (int y = 0; y < 32 && solidHat; y++) {
            for (int x = 32; x < 64; x++) {
                if ((argb[y * 64 + x] >>> 24) < 128) { solidHat = false; break; }
            }
        }
        if (solidHat) {
            for (int y = 0; y < 32; y++) {
                for (int x = 32; x < 64; x++) argb[y * 64 + x] &= 0xFFFFFF;
            }
        }
        copy(argb, 4, 16, 16, 32, 4, 4);
        copy(argb, 8, 16, 16, 32, 4, 4);
        copy(argb, 0, 20, 24, 32, 4, 12);
        copy(argb, 4, 20, 16, 32, 4, 12);
        copy(argb, 8, 20, 8, 32, 4, 12);
        copy(argb, 12, 20, 16, 32, 4, 12);
        copy(argb, 44, 16, -8, 32, 4, 4);
        copy(argb, 48, 16, -8, 32, 4, 4);
        copy(argb, 40, 20, 0, 32, 4, 12);
        copy(argb, 44, 20, -8, 32, 4, 12);
        copy(argb, 48, 20, -16, 32, 4, 12);
        copy(argb, 52, 20, -8, 32, 4, 12);
    }

    private static void copy(int[] argb, int x0, int y0, int dx, int dy, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                argb[(y0 + dy + y) * 64 + x0 + dx + (w - 1 - x)] = argb[(y0 + y) * 64 + x0 + x];
            }
        }
    }

    private static String urlOf(AbstractClientPlayer player) {
        //? if 1.20.1 {
        /*com.mojang.authlib.minecraft.MinecraftProfileTexture skin = Minecraft.getInstance().getSkinManager()
                .getInsecureSkinInformation(player.getGameProfile())
                .get(com.mojang.authlib.minecraft.MinecraftProfileTexture.Type.SKIN);
        return skin != null ? skin.getUrl() : null;
        *///?} elif 1.21.1 {
        /*return player.getSkin().textureUrl();
        *///?} else {
        return player.getSkin().body() instanceof net.minecraft.core.ClientAsset.DownloadedTexture t ? t.url() : null;
        //?}
    }

    //? if 1.20.1 {
    /*private static com.mojang.authlib.minecraft.MinecraftProfileTexture legacySkin(Minecraft mc, com.mojang.authlib.GameProfile profile) {
        java.util.Map<com.mojang.authlib.minecraft.MinecraftProfileTexture.Type, com.mojang.authlib.minecraft.MinecraftProfileTexture> textures =
                mc.getSkinManager().getInsecureSkinInformation(profile);
        if (textures.isEmpty()) {
            com.mojang.authlib.minecraft.MinecraftSessionService session = mc.getMinecraftSessionService();
            textures = session.getTextures(session.fillProfileProperties(profile, false), false);
        }
        return textures.get(com.mojang.authlib.minecraft.MinecraftProfileTexture.Type.SKIN);
    }
    *///?}

    private static int carried(byte[] png) throws IOException {
        try (NativeImage image = NativeImage.read(png)) {
            if (image.getWidth() != 64 || image.getHeight() != 64) return NONE;
            int[] argb = new int[64 * 64];
            argb[MARKER_Y * 64 + MARKER_X] = get(image, MARKER_X, MARKER_Y);
            argb[MARKER_Y * 64 + MARKER_X + 1] = get(image, MARKER_X + 1, MARKER_Y);
            return carried(argb);
        }
    }

    private static int carried(int[] argb) {
        int marker = argb[MARKER_Y * 64 + MARKER_X];
        int colour = argb[MARKER_Y * 64 + MARKER_X + 1];
        if (marker != MARKER || (colour >>> 24) != 0xFF) return NONE;
        return colour & 0xFFFFFF;
    }

    private static byte[] download(String url) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
        boolean web = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
        if (!web || !(host.endsWith(".minecraft.net") || host.endsWith(".mojang.com"))) {
            throw new IOException("not a skin server: " + host);
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<InputStream> response = http().send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
            byte[] png = in.readNBytes(MAX_BYTES + 1);
            if (png.length > MAX_BYTES) throw new IOException("skin too large");
            return png;
        }
    }

    private static synchronized HttpClient http() {
        if (http == null) {
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
        }
        return http;
    }

    private static int get(NativeImage image, int x, int y) {
        //? if <=1.21.1 {
        /*int abgr = image.getPixelRGBA(x, y);
        return (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >> 16) & 0xFF);
        *///?} else {
        return image.getPixel(x, y);
        //?}
    }

    private static void set(NativeImage image, int x, int y, int argb) {
        //? if <=1.21.1 {
        /*image.setPixelRGBA(x, y, (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF));
        *///?} else {
        image.setPixel(x, y, argb);
        //?}
    }
}
