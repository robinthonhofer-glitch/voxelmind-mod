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
    private volatile Runnable onClose = null;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public TunnelBridge(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Register a callback fired exactly once when this bridge closes.
     * The caller uses this to remove the bridge from any routing map AND
     * notify the relay so the Agent-side TCP connection gets torn down too.
     */
    public void setOnClose(Runnable onClose) {
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
            try {
                InputStream in = tcpSocket.getInputStream();
                byte[] buf = new byte[8192];
                int len;
                while (running && (len = in.read(buf)) != -1) {
                    sendBinary.send(ByteBuffer.wrap(buf, 0, len));
                }
            } catch (IOException e) {
                if (running) {
                    VoxelMindMod.LOGGER.warn("TunnelBridge: Read error: {}", e.getMessage());
                }
            } finally {
                close();
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
            VoxelMindMod.LOGGER.warn("TunnelBridge: TCP write error: {}", e.getMessage());
            close();
        }
    }

    public boolean isConnected() {
        return running && tcpSocket != null && !tcpSocket.isClosed();
    }

    public void close() {
        // Only fire the onClose callback once, even if close() is called from
        // multiple paths (reader-thread finally + explicit stopAllBridges).
        boolean firstClose = closed.compareAndSet(false, true);
        running = false;
        try {
            if (tcpSocket != null) tcpSocket.close();
        } catch (IOException ignored) {}
        if (firstClose) {
            VoxelMindMod.LOGGER.info("TunnelBridge: Closed connection to {}:{}", host, port);
            Runnable cb = onClose;
            if (cb != null) {
                try { cb.run(); } catch (Exception e) {
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
