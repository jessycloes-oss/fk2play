package com.fk2play.usb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import com.fk2play.protocol.Transport;

import java.io.IOException;

/** Lien USB host vers le dongle Carlinkit (API 12+, compatible Android 4.0.4). */
public class UsbTransport implements Transport {
    /** Identifiants USB connus des dongles Carlinkit « Autokit » (CPC200-CCPA, etc.). */
    public static final int VENDOR_ID = 0x1314;
    public static final int[] PRODUCT_IDS = {0x1520, 0x1521};

    /** Avant Android 9, bulkTransfer est limite a 16 Ko par appel. */
    private static final int MAX_CHUNK = 16384;

    private final UsbDeviceConnection conn;
    private final UsbInterface intf;
    private final UsbEndpoint in;
    private final UsbEndpoint out;
    private volatile boolean closed;

    public static boolean isDongle(UsbDevice d) {
        if (d == null || d.getVendorId() != VENDOR_ID) return false;
        for (int p : PRODUCT_IDS) if (d.getProductId() == p) return true;
        return false;
    }

    public UsbTransport(UsbManager manager, UsbDevice device) throws IOException {
        UsbInterface found = null;
        UsbEndpoint epIn = null, epOut = null;
        for (int i = 0; i < device.getInterfaceCount() && found == null; i++) {
            UsbInterface itf = device.getInterface(i);
            UsbEndpoint a = null, b = null;
            for (int e = 0; e < itf.getEndpointCount(); e++) {
                UsbEndpoint ep = itf.getEndpoint(e);
                if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) a = ep; else b = ep;
            }
            if (a != null && b != null) { found = itf; epIn = a; epOut = b; }
        }
        if (found == null) throw new IOException("Aucune interface bulk trouvee sur le dongle");
        UsbDeviceConnection c = manager.openDevice(device);
        if (c == null) throw new IOException("Impossible d'ouvrir le dongle (permission USB ?)");
        if (!c.claimInterface(found, true)) {
            c.close();
            throw new IOException("claimInterface a echoue");
        }
        conn = c; intf = found; in = epIn; out = epOut;
    }

    /**
     * Delai max d'une lecture USB. JAMAIS 0 (infini) : sur le noyau Linux 3.x du Honda Connect,
     * une lecture infinie est une attente non interruptible ; si le dongle se tait, le thread
     * (et donc le processus) resterait bloque jusqu'au redemarrage de l'autoradio.
     */
    private static final int READ_TIMEOUT_MS = 500;
    /** Echecs « instantanes » consecutifs (pas un simple delai depasse) avant d'abandonner. */
    private static final int MAX_FAST_FAILURES = 5;

    @Override
    public void readFully(byte[] buf, int off, int len) throws IOException {
        int done = 0;
        int fastFailures = 0;
        // bulkTransfer(endpoint, buffer, length, timeout) n'a pas d'offset avant l'API 18 :
        // on passe par un tampon temporaire si necessaire.
        byte[] tmp = null;
        while (done < len) {
            // Session arretee (stop() interrompt ce thread) : on sort au plus tard apres un delai de lecture.
            if (closed || Thread.currentThread().isInterrupted()) throw new IOException("Transport ferme");
            int want = Math.min(MAX_CHUNK, len - done);
            long t0 = System.nanoTime();
            int n;
            if (off == 0 && done == 0) {
                n = conn.bulkTransfer(in, buf, want, READ_TIMEOUT_MS);
            } else {
                if (tmp == null || tmp.length < want) tmp = new byte[want];
                n = conn.bulkTransfer(in, tmp, want, READ_TIMEOUT_MS);
                if (n > 0) System.arraycopy(tmp, 0, buf, off + done, n);
            }
            if (n < 0) {
                // Avant Android 4.3, -1 = delai depasse OU erreur. Un delai depasse prend ~500 ms,
                // une vraie erreur (dongle arrache) revient immediatement.
                long ms = (System.nanoTime() - t0) / 1000000L;
                if (ms < READ_TIMEOUT_MS / 2) {
                    if (++fastFailures >= MAX_FAST_FAILURES) {
                        throw new IOException("Lecture USB echouee (dongle debranche ?)");
                    }
                } else {
                    fastFailures = 0;
                }
                continue;
            }
            fastFailures = 0;
            done += n;
        }
    }

    @Override
    public void write(byte[] data) throws IOException {
        int done = 0;
        byte[] tmp = null;
        while (done < data.length) {
            if (closed) throw new IOException("Transport ferme");
            int want = Math.min(MAX_CHUNK, data.length - done);
            int n;
            if (done == 0) {
                n = conn.bulkTransfer(out, data, want, 1000);
            } else {
                if (tmp == null) tmp = new byte[MAX_CHUNK];
                System.arraycopy(data, done, tmp, 0, want);
                n = conn.bulkTransfer(out, tmp, want, 1000);
            }
            if (n < 0) throw new IOException("Ecriture USB echouee");
            done += n;
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try { conn.releaseInterface(intf); } catch (Exception ignored) { }
        try { conn.close(); } catch (Exception ignored) { }
    }
}
