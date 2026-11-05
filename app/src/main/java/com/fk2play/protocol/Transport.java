package com.fk2play.protocol;

import java.io.IOException;

/** Abstraction du lien USB (permet de tester le protocole sans materiel). */
public interface Transport {
    /** Lit exactement len octets dans buf[off..] ; leve IOException si le lien est coupe. */
    void readFully(byte[] buf, int off, int len) throws IOException;

    void write(byte[] data) throws IOException;

    void close();
}
