package com.voxelmind.mod.tunnel;

import com.voxelmind.mod.VoxelMindMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bridges a local TCP socket (localhost MC server) with the WSS tunnel.
 * Reads from localhost TCP → sends as binary frames to relay WSS.
 * Receives binary frames from relay WSS → writes to localhost TCP.
 *
 * One bridge per Agent TCP connection (MC protocol is stateful per connection).
 */
public class TunnelBridge {
    private final String host;
    private final int port;
    private Socket tcpSocket;
    private OutputStream tcpOut;
    private volatile boolean running = false;
    private Thread readerThread;

    /** Fired exactly once when the bridge closes (for any reason). */
    private volatile java.util.function.Consumer<String> onClose = null;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    /** Set once when the bridge starts closing — passed to the onClose callback. */
    private volatile String closeReason = "unknown";

    public TunnelBridge(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Register a callback fired exactly once when this bridge closes.
     * The reason argument tells the caller what flipped: "read_eof"
     * (server cleanly closed), "read_error:<msg>" (read IOException),
     * "write_error:<msg>" (write IOException), "external_close" (close()
     * called from outside), or "unknown" (not classifiable).
     */
    public void setOnClose(java.util.function.Consumer<String> onClose) {
        this.onClose = onClose;
    }

    /**
     * Connect SYNCHRONOUSLY to localhost MC server and start reading.
     * Synchronous because localhost TCP connect is instant (<1ms).
     * Must NOT be called from the MC main thread.
     */
    public void connectSync(BinarySender sendBinary) throws IOException {
        tcpSocket = new Socket(host, port);
        tcpOut = tcpSocket.getOutputStream();
        running = true;

        VoxelMindMod.LOGGER.info("TunnelBridge: Connected to {}:{}", host, port);

        // Read TCP → send WSS
        readerThread = new Thread(() -> {
            String reason = "read_eof";
            try {
                InputStream in = tcpSocket.getInputStream();
                byte[] buf = new byte[8192];
                int len;
                while (running && (len = in.read(buf)) != -1) {
                    sendBinary.send(ByteBuffer.wrap(buf, 0, len));
                }
                // read() returned -1 — the LAN/MC server cleanly closed the socket.
                // That's normally a server-side kick (anti-cheat, "received string
                // longer than maximum", protocol mismatch, etc.) or LAN being
                // closed. We don't know the exact MC kick reason here — Mineflayer
                // would log it on the AGENT side, not ours — but "read_eof" tells
                // the diagnostics it was a clean FIN, not our error.
            } catch (IOException e) {
                reason = "read_error:" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                if (running) {
                    VoxelMindMod.LOGGER.warn("TunnelBridge: Read error: {}", e.getMessage());
                }
            } finally {
                closeWithReason(reason);
            }
        }, "VoxelMind-TunnelBridge-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Write data received from the relay WSS to the local TCP socket.
     */
    public void writeToTcp(ByteBuffer data) {
        if (tcpOut == null || !running) return;
        try {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            tcpOut.write(bytes);
            tcpOut.flush();
        } catch (IOException e) {
            String reason = "write_error:" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            VoxelMindMod.LOGGER.warn("TunnelBridge: TCP write error: {}", e.getMessage());
            closeWithReason(reason);
        }
    }

    public boolean isConnected() {
        return running && tcpSocket != null && !tcpSocket.isClosed();
    }

    /** External close — no specific reason known by the caller. */
    public void close() {
        closeWithReason("external_close");
    }

    /** Close with a reason that will be passed to the onClose callback. */
    public void closeWithReason(String reason) {
        // Only fire the onClose callback once, even if close() is called from
        // multiple paths (reader-thread finally + explicit stopAllBridges).
        boolean firstClose = closed.compareAndSet(false, true);
        if (firstClose) closeReason = reason;
        running = false;
        try {
            if (tcpSocket != null) tcpSocket.close();
        } catch (IOException ignored) {}
        if (firstClose) {
            VoxelMindMod.LOGGER.info("TunnelBridge: Closed connection to {}:{} (reason={})", host, port, closeReason);
            java.util.function.Consumer<String> cb = onClose;
            if (cb != null) {
                try { cb.accept(closeReason); } catch (Exception e) {
                    VoxelMindMod.LOGGER.warn("TunnelBridge: onClose callback threw: {}", e.getMessage());
                }
            }
        }
    }

    @FunctionalInterface
    public interface BinarySender {
        void send(ByteBuffer data);
    }
}
