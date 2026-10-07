package com.bitifyware.control;

/** All operations are performed under this object's monitor. */
interface TextSource {
    int length();
    long version();
    String read(int start, int end);
    void checkOpen();
}
