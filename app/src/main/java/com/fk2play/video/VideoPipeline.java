package com.fk2play.video;

import android.os.Build;
import android.os.SystemClock;
import android.view.Surface;

import com.fk2play.Stats;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * File de trames + thread de decodage, pense pour un autoradio lent et partage
 * (Honda Connect : Tegra 3, Android 4.0.4, logiciels Honda qui tournent en meme temps).
 *
 * - Aucune allocation par image (tampons recycles) : moins de pauses du ramasse-miettes.
 * - Decodage et affichage dans deux threads (deux coeurs du Tegra 3) : l'attente de l'ecran
 *   et la conversion des couleurs ne retardent plus le decodage de l'image suivante.
 * - Rattrapage : si l'affichage prend du retard, l'image en attente est remplacee par la plus
 *   recente ; si le decodage prend du retard, seule la plus recente est affichee.
 * - Mode allege automatique tant que l'autoradio n'arrive pas a suivre.
 * - Le decodeur survit quand on quitte l'ecran CarPlay : au retour, la derniere image
 *   est reaffichee instantanement. Pendant un appel, le decodage continue en arriere-plan
 *   pour que le retour sur CarPlay soit immediat et a jour.
 * - Si l'image cle demandee n'arrive pas, elle est redemandee.
 */
public class VideoPipeline {
    public interface KeyframeRequester {
        void requestKeyframe();
    }

    public interface StatusSink {
        void onDecoderStatus(String s);
    }

    /** Images en attente au maximum (~200 ms) avant de tout jeter et repartir d'une image cle. */
    private static final int POOL = 6;
    private static final long KEYFRAME_RETRY_MS = 400;
    private static final int KEYFRAME_MAX_RETRIES = 15;
    /** Meme en rattrapage, on affiche au moins une image toutes les 150 ms. */
    private static final long MAX_HIDDEN_MS = 150;
    /** Sortie du mode allege apres 3 s sans retard. */
    private static final long FAST_EXIT_MS = 3000;
    /** Au retour, on reaffiche la derniere image si elle a moins de 10 s. */
    private static final long LAST_IMAGE_MAX_AGE_MS = 10000;

    private static final class Frame {
        byte[] d = new byte[64 * 1024];
        int len, w, h;
        long t;
    }

    private final ArrayBlockingQueue<Frame> queue = new ArrayBlockingQueue<Frame>(POOL);
    private final ArrayBlockingQueue<Frame> free = new ArrayBlockingQueue<Frame>(POOL);
    private final KeyframeRequester requester;
    private final StatusSink status;
    /** Protege le decodeur et l'ecran (thread de decodage / thread UI). */
    private final Object lock = new Object();
    /** Protege le decodeur pendant l'affichage (thread d'affichage). Ordre : lock puis renderLock. */
    private final Object renderLock = new Object();
    private final Object displaySignal = new Object();
    private boolean displayPending;
    private volatile long pendingArrival, pendingDecodeNs;
    private Thread displayThread;

    private volatile Surface surface;
    private volatile boolean running;
    private volatile boolean backgroundDecode;
    private volatile boolean adaptive = true;
    /**
     * Affichage dans un thread a part : seulement si le processeur a plusieurs coeurs
     * (Tegra 3 : 4). Sur un seul coeur, les deux threads se disputeraient le processeur.
     */
    private volatile boolean useDisplayThread = cpuCores() > 1;
    /** Reglage de diagnostic : null = automatique, sinon force (pris en compte au prochain demarrage du service). */
    public static volatile Boolean forceDisplayThread;
    /** Reglage « Decodeur video » : true = materiel (Tegra) si possible, false = FFmpeg uniquement. */
    public static volatile boolean preferHardware = true;
    /** Decodeur en service, pour l'ecran Diagnostic. */
    private static volatile VideoDecoder current;
    /** Retour a l'ecran pendant que FFmpeg decodait : passer au materiel a la prochaine image cle. */
    private volatile boolean switchToHardware;
    /** Des images ont ete ignorees : l'etat du decodeur ne correspond plus a l'iPhone. */
    private volatile boolean stale = true;
    private volatile boolean waitingKeyframe = true;
    private Thread thread;
    private VideoDecoder decoder;
    private int width, height;
    private boolean fast;
    private long lastBacklog, lastDecoded;
    private volatile long lastShown;
    private volatile long lastKeyframeRequest;
    private int keyframeRetries;
    private int frames;
    private long lastFpsTime;

    public VideoPipeline(KeyframeRequester requester, StatusSink status) {
        this.requester = requester;
        this.status = status;
        for (int i = 0; i < POOL; i++) free.offer(new Frame());
    }

    /** Thread UI : nouvel ecran (ou null quand l'ecran CarPlay disparait). */
    public void setSurface(Surface s) {
        synchronized (lock) {
          synchronized (renderLock) {
            surface = s;
            ensureThread();
            if (s != null) Stats.screenResumed();
            if (decoder != null) {
                if (decoder.keepsStateWithoutSurface()) {
                    boolean recent = SystemClock.uptimeMillis() - lastDecoded < LAST_IMAGE_MAX_AGE_MS;
                    if (decoder.setSurface(s, recent) && s != null) {
                        lastShown = SystemClock.uptimeMillis();
                        Stats.imageReshown();
                    }
                    if (s != null && preferHardware && OmxDecoder.isAvailable()) {
                        // FFmpeg a reaffiche la derniere image ; le materiel reprend a la prochaine image cle.
                        switchToHardware = true;
                        askKeyframe();
                    }
                } else {
                    rememberLast(decoder); decoder.release();
                    decoder = null;
                    current = null;
                    waitingKeyframe = true;
                }
            }
            if (s != null && (stale || waitingKeyframe)) {
                stale = false;
                if (decoder != null) decoder.forgetLast();
                waitingKeyframe = true;
                keyframeRetries = 0;
                askKeyframe();
            }
          }
        }
    }

    /** Pendant un appel : continuer a decoder sans ecran pour un retour instantane. */
    public void setBackgroundDecode(boolean on) {
        backgroundDecode = on;
        if (on) ensureThread();
    }

    public void setAdaptive(boolean on) {
        adaptive = on;
    }

    /** Appele depuis le thread USB. */
    public void submit(int w, int h, byte[] data, int off, int len) {
        // Dimensions aberrantes (trame corrompue) : on ignore plutot que d'allouer un tampon geant.
        if (w < 16 || h < 16 || w > 1920 || h > 1088 || len <= 0) return;
        if (surface == null && !backgroundDecode) {
            // Sans ecran, les images sont ignorees (economie de calcul pour le Honda).
            stale = true;
            return;
        }
        Frame f = free.poll();
        if (f == null) {
            // Plus de ~200 ms de retard malgre le rattrapage : on repart d'une image cle.
            int n = 0;
            Frame q;
            while ((q = queue.poll()) != null) { free.offer(q); n++; }
            Stats.frameDropped(n);
            waitingKeyframe = true;
            askKeyframe();
            f = free.poll();
            if (f == null) return;
        }
        if (f.d.length < len) f.d = new byte[len + 32 * 1024];
        System.arraycopy(data, off, f.d, 0, len);
        f.len = len;
        f.w = w;
        f.h = h;
        f.t = System.nanoTime();
        if (!queue.offer(f)) free.offer(f);
    }

    /** Nouvelle session avec le dongle : on repart d'une image cle, l'ancienne image est perimee. */
    public void reset() {
        Frame q;
        while ((q = queue.poll()) != null) free.offer(q);
        synchronized (lock) {
            waitingKeyframe = true;
            keyframeRetries = 0;
            if (decoder != null) decoder.forgetLast();
        }
    }

    /** Arret definitif (fin du service). */
    public void release() {
        Thread t, dt;
        synchronized (lock) {
            running = false;
            t = thread;
            dt = displayThread;
            thread = null;
            displayThread = null;
        }
        for (Thread x : new Thread[]{t, dt}) {
            if (x == null) continue;
            x.interrupt();
            try { x.join(1000); } catch (InterruptedException ignored) { }
        }
        synchronized (lock) {
            synchronized (renderLock) {
                if (decoder != null) { rememberLast(decoder); decoder.release(); decoder = null; current = null; }
            }
        }
    }

    private void ensureThread() {
        synchronized (lock) {
            if (thread != null) return;
            running = true;
            Boolean f = forceDisplayThread;
            useDisplayThread = f != null ? f : cpuCores() > 1;
            android.util.Log.i("FK2Play", "Video : " + (useDisplayThread ? "decodage et affichage sur deux coeurs"
                    : "decodage et affichage dans le meme thread") + " (" + cpuCores() + " coeur(s))");
            thread = new Thread(new Runnable() {
                public void run() { loop(); }
            }, "video-decoder");
            // Priorite normale : le decodage ne doit jamais affamer les services du Honda Connect
            // (camera de recul, radio, Bluetooth). Une image en retard est preferable.
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.start();
            if (useDisplayThread) {
                displayThread = new Thread(new Runnable() {
                    public void run() { displayLoop(); }
                }, "video-display");
                displayThread.setPriority(Thread.NORM_PRIORITY);
                displayThread.start();
            }
        }
    }

    /** Thread d'affichage : conversion des couleurs + envoi a l'ecran de la derniere image decodee. */
    private void displayLoop() {
        try {
            while (running) {
                synchronized (displaySignal) {
                    while (running && !displayPending) displaySignal.wait(200);
                    displayPending = false;
                }
                long arrival = pendingArrival, dec = pendingDecodeNs;
                boolean shown;
                synchronized (renderLock) {
                    shown = decoder != null && surface != null && decoder.renderPending();
                }
                if (shown) {
                    lastShown = SystemClock.uptimeMillis();
                    Stats.frameShown(arrival, dec);
                    countFps();
                }
            }
        } catch (InterruptedException ignored) {
        }
    }

    private void askKeyframe() {
        lastKeyframeRequest = SystemClock.uptimeMillis();
        requester.requestKeyframe();
    }

    private void loop() {
        try {
            while (running) {
                long now = SystemClock.uptimeMillis();
                if (waitingKeyframe && (surface != null || backgroundDecode)
                        && keyframeRetries < KEYFRAME_MAX_RETRIES && now - lastKeyframeRequest > KEYFRAME_RETRY_MS) {
                    keyframeRetries++;
                    askKeyframe();
                }
                if (fast && now - lastBacklog > FAST_EXIT_MS) setFast(false);
                Frame f = queue.poll(100, TimeUnit.MILLISECONDS);
                if (f == null) continue;
                try {
                    process(f);
                } finally {
                    free.offer(f);
                }
            }
        } catch (InterruptedException ignored) {
        }
    }

    private void process(Frame f) {
        synchronized (lock) {
            if (waitingKeyframe) {
                if (!containsIdrOrSps(f.d, f.len)) { Stats.frameDropped(1); return; }
                waitingKeyframe = false;
                keyframeRetries = 0;
            }
            if (switchToHardware && surface != null && containsIdrOrSps(f.d, f.len)) {
                switchToHardware = false;
                if (decoder instanceof FfmpegDecoder && preferHardware && OmxDecoder.isAvailable()) {
                    synchronized (renderLock) {
                        rememberLast(decoder); decoder.release();
                        decoder = null;
                    }
                }
            }
            if (decoder == null || width != f.w || height != f.h) {
              synchronized (renderLock) {
                if (decoder != null) { rememberLast(decoder); decoder.release(); }
                width = f.w;
                height = f.h;
                decoder = createDecoder(surface, width, height);
                current = decoder;
              }
                if (decoder == null) {
                    status.onDecoderStatus("Aucun decodeur video disponible !");
                    running = false;
                    return;
                }
                fast = false;
                status.onDecoderStatus("Decodeur : " + decoder.name() + " " + width + "x" + height);
            }
            long now = SystemClock.uptimeMillis();
            int backlog = queue.size();
            if (backlog > 0) {
                lastBacklog = now;
                if (backlog >= 2 && adaptive && !fast) setFast(true);
            }
            // Rattrapage si le decodage prend du retard : seule l'image la plus recente part a l'affichage
            // (sauf si l'ecran attend depuis trop longtemps).
            boolean show = surface != null && (backlog == 0 || now - lastShown > MAX_HIDDEN_MS);
            long t0 = System.nanoTime();
            int r = decoder.decode(f.d, 0, f.len, show);
            long dt = System.nanoTime() - t0;
            if (r < 0) {
                if (decoder instanceof OmxDecoder) {
                    // Decodeur materiel en panne : repli sur FFmpeg a la prochaine image cle.
                    synchronized (renderLock) {
                        rememberLast(decoder); decoder.release();
                        decoder = null;
                        current = null;
                    }
                    status.onDecoderStatus("Decodeur materiel indisponible : retour au logiciel");
                }
                waitingKeyframe = true;
                askKeyframe();
                return;
            }
            if (decoder instanceof OmxDecoder && ((OmxDecoder) decoder).keyframeWanted) {
                ((OmxDecoder) decoder).keyframeWanted = false;
                askKeyframe();
            }
            if (r != VideoDecoder.NOTHING) lastDecoded = now;
            if ((r == VideoDecoder.QUEUED || r == VideoDecoder.REPLACED) && !useDisplayThread) {
                // Un seul coeur : affichage tout de suite, dans ce thread.
                if (decoder.renderPending()) {
                    lastShown = SystemClock.uptimeMillis();
                    Stats.frameShown(f.t, dt);
                    countFps();
                }
            } else if (r == VideoDecoder.QUEUED || r == VideoDecoder.REPLACED) {
                if (r == VideoDecoder.REPLACED) Stats.frameSkipped();
                pendingArrival = f.t;
                pendingDecodeNs = dt;
                synchronized (displaySignal) {
                    displayPending = true;
                    displaySignal.notify();
                }
            } else if (r == VideoDecoder.SHOWN) {
                lastShown = SystemClock.uptimeMillis();
                Stats.frameShown(f.t, dt);
                countFps();
            } else if (r == VideoDecoder.DECODED && surface != null) {
                Stats.frameSkipped();
            }
        }
    }

    private void setFast(boolean on) {
        fast = on;
        if (decoder != null) decoder.setFast(on);
        android.util.Log.i("FK2Play", on ? "Autoradio charge : decodage allege" : "Decodage normal");
    }

    private synchronized void countFps() {
        frames++;
        long now = System.currentTimeMillis();
        if (now - lastFpsTime > 5000) {
            if (lastFpsTime != 0) {
                status.onDecoderStatus(String.format("%.1f i/s", frames * 1000f / (now - lastFpsTime)));
            }
            frames = 0;
            lastFpsTime = now;
        }
    }

    /**
     * Coeurs presents (et pas seulement allumes : le Tegra 3 eteint ses coeurs au repos,
     * availableProcessors() peut alors repondre 1).
     */
    static int cpuCores() {
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.FileReader("/sys/devices/system/cpu/possible"));
            String s = r.readLine();
            r.close();
            int n = 0;
            for (String part : s.trim().split(",")) {
                String[] ab = part.split("-");
                n += ab.length == 2 ? Integer.parseInt(ab[1]) - Integer.parseInt(ab[0]) + 1 : 1;
            }
            return n;
        } catch (Exception e) {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    /** Dernier decodeur utilise (l'ecran Reglages cache CarPlay, donc aucun decodeur n'y tourne). */
    private static volatile String lastUsed;
    private static volatile long lastUsedAt;

    private static String describe(VideoDecoder d) {
        if (d instanceof OmxDecoder) return "Materiel : " + ((OmxDecoder) d).info();
        return "Logiciel : " + d.name();
    }

    private static void rememberLast(VideoDecoder d) {
        if (d == null) return;
        try {
            lastUsed = describe(d);
            lastUsedAt = SystemClock.uptimeMillis();
        } catch (Throwable ignored) { }
    }

    /** Texte pour l'ecran Diagnostic. */
    public static String decoderDiagnostic() {
        VideoDecoder d = current;
        StringBuilder b = new StringBuilder();
        if (d != null) {
            b.append("En cours : ").append(describe(d));
            if (lastUsed != null && !(d instanceof OmxDecoder)) b.append("\nAvant : ").append(lastUsed);
        } else if (lastUsed != null) {
            long s = (SystemClock.uptimeMillis() - lastUsedAt) / 1000;
            b.append("Dernier utilise (il y a ").append(s < 60 ? s + " s" : (s / 60) + " min").append(") : ")
                    .append(lastUsed);
        } else b.append("aucun pour l'instant (afficher CarPlay une fois)");
        if (!preferHardware) b.append("\nMateriel desactive dans les reglages");
        else if (OmxDecoder.unavailableReason() != null) b.append("\nMateriel : ").append(OmxDecoder.unavailableReason());
        return b.toString();
    }

    private static VideoDecoder createDecoder(Surface s, int w, int h) {
        if (preferHardware && s != null && Build.VERSION.SDK_INT < 16 && OmxDecoder.isAvailable()) {
            VideoDecoder d = new OmxDecoder();
            if (d.init(s, w, h)) return d;
        }
        if (Build.VERSION.SDK_INT >= 16 && s != null) {
            VideoDecoder d = new MediaCodecDecoder();
            if (d.init(s, w, h)) return d;
        }
        if (FfmpegDecoder.isAvailable()) {
            VideoDecoder d = new FfmpegDecoder();
            if (d.init(s, w, h)) return d;
        }
        return null;
    }

    /** Cherche une NAL SPS (7) ou IDR (5) dans un flux Annex-B. */
    static boolean containsIdrOrSps(byte[] d, int len) {
        for (int i = 0; i + 3 < len; i++) {
            if (d[i] == 0 && d[i + 1] == 0 && d[i + 2] == 1) {
                int t = d[i + 3] & 0x1F;
                if (t == 5 || t == 7) return true;
                i += 2;
            }
        }
        return false;
    }

    static boolean containsIdrOrSps(byte[] d) {
        return containsIdrOrSps(d, d.length);
    }
}
