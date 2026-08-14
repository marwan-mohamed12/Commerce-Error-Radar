package com.commerce.radar.adapter.tail;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Last-N lines of the console log, used as ERROR/WARN context.
 */
public final class LineRingBuffer {

    private final int capacity;
    private final ArrayDeque<String> lines;

    public LineRingBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.lines = new ArrayDeque<>(this.capacity);
    }

    public synchronized void add(String line) {
        if (lines.size() == capacity) {
            lines.removeFirst();
        }
        lines.addLast(line);
    }

    public synchronized List<String> snapshot() {
        return new ArrayList<>(lines);
    }

    /**
     * Lines immediately before the just-closed event, up to {@code count}.
     * The event's own lines are expected to still be in the buffer.
     */
    public synchronized String contextBefore(String rawEvent, int count) {
        List<String> snap = new ArrayList<>(lines);
        int eventLines = rawEvent == null ? 0 : rawEvent.split("\\R", -1).length;
        int end = Math.max(0, snap.size() - eventLines);
        int start = Math.max(0, end - Math.max(0, count));
        if (start >= end) {
            return "";
        }
        return String.join("\n", snap.subList(start, end));
    }
}
