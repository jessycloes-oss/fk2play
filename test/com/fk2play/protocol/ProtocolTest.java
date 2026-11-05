package com.fk2play.protocol;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

/** Tests sans materiel : un faux dongle repond comme un Carlinkit. Lancer : scripts/run_protocol_tests.sh */
public class ProtocolTest {
    static int failures = 0;
    static void check(boolean ok, String what) {
        System.out.println((ok ? "  OK   " : "  ECHEC ") + what);
        if (!ok) failures++;
    }

    /** Faux transport : ce que l'app ecrit est analyse, ce que le "dongle" envoie est lu. */
    static class FakeTransport implements Transport {
        final PipedInputStream fromDongle = new PipedInputStream(1 << 20);
        final PipedOutputStream dongleOut;
        final List<Msg> sent = Collections.synchronizedList(new ArrayList<Msg>());
        final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        FakeTransport() throws IOException { dongleOut = new PipedOutputStream(fromDongle); }
        public void readFully(byte[] b, int off, int len) throws IOException {
            int done = 0;
            while (done < len) { int n = fromDongle.read(b, off + done, len - done); if (n < 0) throw new EOFException(); done += n; }
        }
        public synchronized void write(byte[] d) throws IOException {
            try {
                int[] tl = Msg.decodeHeader(d);
                if (d.length != 16 + tl[1]) throw new IOException("longueur incoherente");
                sent.add(new Msg(tl[0], Arrays.copyOfRange(d, 16, d.length)));
            } catch (Msg.ProtocolException e) { throw new IOException(e); }
        }
        public void close() { try { dongleOut.close(); } catch (IOException ignored) {} }
        void dongleSends(Msg m) throws IOException { dongleOut.write(m.encode()); dongleOut.flush(); }
    }

    static class Recorder implements DongleListener {
        final BlockingQueue<String> events = new LinkedBlockingQueue<String>();
        byte[] lastVideo; int lastAudioType = -1; int lastAudioLen;
        public void onPhoneConnected(int t, boolean w) { events.add("connected:" + t + ":" + w); }
        public void onPhoneDisconnected() { events.add("disconnected"); }
        public void onVideo(int w, int h, byte[] d, int o, int l) { lastVideo = Arrays.copyOfRange(d, o, o + l); events.add("video:" + w + "x" + h + ":" + l); }
        public void onAudio(int t, byte[] d, int o, int l) { lastAudioType = t; lastAudioLen = l; events.add("audio:" + t + ":" + l); }
        public void onAudioCommand(int c) { events.add("audiocmd:" + c); }
        public void onStatus(String m) { }
        public void onError(Exception e) { events.add("error:" + e.getMessage()); }
        String next() throws InterruptedException { String s = events.poll(3, TimeUnit.SECONDS); return s == null ? "(timeout)" : s; }
    }

    static ByteBuffer le(int n) { return ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN); }

    public static void main(String[] a) throws Exception {
        System.out.println("== Encodage ==");
        byte[] hb = Messages.heartbeat().encode();
        check(hb.length == 16, "heartbeat = 16 octets");
        check(hb[0] == (byte)0xAA && hb[1] == 0x55 && hb[2] == (byte)0xAA && hb[3] == 0x55, "magic 0x55AA55AA little-endian");
        ByteBuffer h = ByteBuffer.wrap(hb).order(ByteOrder.LITTLE_ENDIAN);
        h.getInt(); h.getInt(); int t = h.getInt(); int c = h.getInt();
        check(t == 0xAA && c == ~0xAA, "type 0xAA et controle ~type");

        Msg touch = Messages.touch(Msg.TOUCH_DOWN, 0.5f, 1.2f);
        ByteBuffer tb = ByteBuffer.wrap(touch.payload).order(ByteOrder.LITTLE_ENDIAN);
        check(tb.getInt() == 14 && tb.getInt() == 5000 && tb.getInt() == 10000, "tactile mis a l'echelle 0..10000 et borne");

        Msg sf = Messages.sendNumber("/tmp/screen_dpi", 160);
        ByteBuffer sb = ByteBuffer.wrap(sf.payload).order(ByteOrder.LITTLE_ENDIAN);
        int nl = sb.getInt(); byte[] name = new byte[nl]; sb.get(name);
        check(new String(name, "US-ASCII").equals("/tmp/screen_dpi\0") && sb.getInt() == 4 && sb.getInt() == 160, "SendFile nom NUL-termine + contenu");

        boolean rejected = false;
        try { byte[] bad = hb.clone(); bad[12] = 0; Msg.decodeHeader(bad); } catch (Msg.ProtocolException e) { rejected = true; }
        check(rejected, "en-tete corrompu rejete");

        System.out.println("== Session avec faux dongle ==");
        FakeTransport tr = new FakeTransport();
        Recorder rec = new Recorder();
        DongleConfig cfg = new DongleConfig();
        DongleSession s = new DongleSession(tr, cfg, rec);
        s.start();
        Thread.sleep(1300);
        List<Integer> types = new ArrayList<Integer>();
        synchronized (tr.sent) { for (Msg m : tr.sent) types.add(m.type); }
        check(types.size() >= 12, "sequence d'init envoyee (" + types.size() + " messages)");
        check(types.get(0) == Msg.SEND_FILE && types.get(1) == Msg.OPEN, "DPI puis OPEN en tete");
        Msg open = tr.sent.get(1);
        ByteBuffer ob = ByteBuffer.wrap(open.payload).order(ByteOrder.LITTLE_ENDIAN);
        check(ob.getInt() == 800 && ob.getInt() == 480 && ob.getInt() == 30 && ob.getInt() == 5, "OPEN 800x480 30 i/s H.264");
        boolean hasConnect = false;
        synchronized (tr.sent) { for (Msg m : tr.sent) if (m.type == Msg.COMMAND && ByteBuffer.wrap(m.payload).order(ByteOrder.LITTLE_ENDIAN).getInt() == Msg.CMD_WIFI_CONNECT) hasConnect = true; }
        check(hasConnect, "commande wifiConnect envoyee apres 1 s");

        tr.dongleSends(new Msg(Msg.PLUGGED, le(8).putInt(3).putInt(1).array()));
        check(rec.next().equals("connected:3:true"), "PLUGGED -> CarPlay sans fil");

        byte[] h264 = {0,0,0,1,0x67,1,2,3,0,0,0,1,0x65,9,9,9};
        ByteBuffer vb = le(20 + h264.length); vb.putInt(800).putInt(480).putInt(0).putInt(0).putInt(0).put(h264);
        tr.dongleSends(new Msg(Msg.VIDEO_DATA, vb.array()));
        check(rec.next().equals("video:800x480:16") && Arrays.equals(rec.lastVideo, h264), "VIDEO_DATA -> H.264 intact");

        byte[] pcm = new byte[4096];
        ByteBuffer ab = le(12 + pcm.length); ab.putInt(4).putFloat(1f).putInt(1).put(pcm);
        tr.dongleSends(new Msg(Msg.AUDIO_DATA, ab.array()));
        check(rec.next().equals("audio:4:4096"), "AUDIO_DATA PCM 48 kHz");

        ByteBuffer cb = le(13); cb.putInt(5).putFloat(0f).putInt(3).put((byte) Msg.AUDIO_SIRI_START);
        tr.dongleSends(new Msg(Msg.AUDIO_DATA, cb.array()));
        check(rec.next().equals("audiocmd:8"), "commande audio SIRI_START");

        // Gros message (> 16 Ko) pour verifier la reassemblage
        byte[] big = new byte[300000]; new Random(1).nextBytes(big); big[0]=0;big[1]=0;big[2]=1;big[3]=0x65;
        ByteBuffer bb = le(20 + big.length); bb.putInt(800).putInt(480).putInt(0).putInt(0).putInt(0).put(big);
        tr.dongleSends(new Msg(Msg.VIDEO_DATA, bb.array()));
        check(rec.next().startsWith("video:800x480:300000") && Arrays.equals(rec.lastVideo, big), "trame video de 300 Ko reassemblee");

        tr.dongleSends(new Msg(Msg.HEARTBEAT, null));
        tr.dongleSends(new Msg(Msg.UNPLUGGED, null));
        check(rec.next().equals("disconnected"), "UNPLUGGED -> deconnexion (heartbeat ignore)");

        Thread.sleep(2300);
        int beats = 0;
        synchronized (tr.sent) { for (Msg m : tr.sent) if (m.type == Msg.HEARTBEAT) beats++; }
        check(beats >= 1, "heartbeat envoye toutes les 2 s (" + beats + ")");

        s.stop();
        System.out.println(failures == 0 ? "\nTOUS LES TESTS PASSENT" : "\n" + failures + " ECHEC(S)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
