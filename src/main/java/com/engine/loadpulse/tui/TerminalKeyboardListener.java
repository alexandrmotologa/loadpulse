package com.engine.loadpulse.tui;

import com.engine.loadpulse.engine.WorkerPool;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class TerminalKeyboardListener implements AutoCloseable {
    private final WorkerPool workerPool;
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private Thread listenerThread;

    public TerminalKeyboardListener(WorkerPool workerPool) {
        this.workerPool = workerPool;
    }

    public void start() {
        if (System.console() == null || workerPool == null) {
            return;
        }

        listening.set(true);
        listenerThread = Thread.ofVirtual().name("loadpulse-hotkey-listener").start(() -> {
            try {
                while (listening.get()) {
                    if (System.in.available() > 0) {
                        int ch = System.in.read();
                        if (ch == -1) break;
                        handleKey((char) ch);
                    } else {
                        Thread.sleep(50);
                    }
                }
            } catch (IOException | InterruptedException ignored) {
            }
        });
    }

    private void handleKey(char key) {
        switch (key) {
            case '+', '=' -> workerPool.adjustConcurrency(10);
            case '-', '_' -> workerPool.adjustConcurrency(-10);
            case 'p', 'P' -> workerPool.togglePause();
            case 'q', 'Q' -> workerPool.requestStop();
        }
    }

    @Override
    public void close() {
        listening.set(false);
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
    }
}
