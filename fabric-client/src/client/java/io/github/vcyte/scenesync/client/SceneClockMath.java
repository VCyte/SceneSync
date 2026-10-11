package io.github.vcyte.scenesync.client;

/** NTP-style offset and network RTT estimate for aligning scene timelines across clients. */
public final class SceneClockMath {
    private SceneClockMath() { }

    public static long estimateOffset(long clientSent, long clientReceived,
                                      long serverReceived, long serverSent) {
        return Math.round(((serverReceived - clientSent) + (serverSent - clientReceived)) / 2.0);
    }

    public static long networkRoundTrip(long clientSent, long clientReceived,
                                        long serverReceived, long serverSent) {
        return (clientReceived - clientSent) - (serverSent - serverReceived);
    }

    public static boolean isBetterSample(long candidateRtt, long bestRtt) {
        return candidateRtt >= 0 && candidateRtt < bestRtt;
    }
}
