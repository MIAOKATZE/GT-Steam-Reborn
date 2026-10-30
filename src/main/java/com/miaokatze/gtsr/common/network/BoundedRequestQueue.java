package com.miaokatze.gtsr.common.network;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

/** Bounded admission for uncommitted network requests, never inventory/lease cleanup obligations. */
public final class BoundedRequestQueue<T> {

    public static final int MAX_PENDING = 256;
    public static final int MAX_PER_CONNECTION = 16;
    public static final int MAX_PER_TICK = 64;
    private final ArrayDeque<Entry<T>> requests = new ArrayDeque<>();
    private final Map<Object, Integer> counts = new IdentityHashMap<>();

    public synchronized boolean offer(Object connection, T request) {
        if (connection == null || request == null || requests.size() >= MAX_PENDING) return false;
        int count = counts.getOrDefault(connection, 0);
        if (count >= MAX_PER_CONNECTION) return false;
        requests.addLast(new Entry<>(connection, request));
        counts.put(connection, count + 1);
        return true;
    }

    public synchronized T poll() {
        Entry<T> entry = requests.pollFirst();
        if (entry == null) return null;
        int count = counts.get(entry.connection);
        if (count == 1) counts.remove(entry.connection);
        else counts.put(entry.connection, count - 1);
        return entry.request;
    }

    public synchronized void clear() {
        requests.clear();
        counts.clear();
    }

    public synchronized int size() {
        return requests.size();
    }

    private static final class Entry<T> {

        final Object connection;
        final T request;

        Entry(Object connection, T request) {
            this.connection = connection;
            this.request = request;
        }
    }
}
