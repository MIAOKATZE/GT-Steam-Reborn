// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.critical;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * Java 8, dependency-free lossless JSON token codec. Strings retain their original escapes; numbers
 * retain their exact JSON lexeme; object and array encounter order are never changed.
 */
public final class CriticalJsonData {

    private static final int MAGIC = 0x47434a42, VERSION = 1;
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public static final int MAX_DEPTH = 128, MAX_NODES = 2000000, MAX_DICT = 65536;
    private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private static final int NIL = 0, FALSE = 1, TRUE = 2, INTEGER = 3, NUMBER_REF = 4, STRING_REF = 5, ARRAY = 6,
        OBJECT = 7, ROWS = 8;

    private CriticalJsonData() {}

    public static byte[] compile(InputStream input) throws IOException {
        byte[] original = bounded(input, MAX_BYTES);
        Parser parser = new Parser(utf8(original));
        Node root = parser.value(0);
        parser.ws();
        if (parser.at != parser.s.length()) throw bad("Trailing JSON data");
        LinkedHashMap<String, Integer> dictionary = new LinkedHashMap<>();
        collect(root, dictionary);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(buffer);
        varint(body, dictionary.size());
        for (String token : dictionary.keySet()) {
            byte[] raw = token.getBytes(StandardCharsets.UTF_8);
            varint(body, raw.length);
            body.write(raw);
        }
        write(body, root, dictionary);
        byte[] payload = buffer.toByteArray();
        if (payload.length > MAX_BYTES) throw bad("Compiled payload too large");
        StringBuilder json = new StringBuilder();
        render(root, json);
        int renderedBytes = json.toString()
            .getBytes(StandardCharsets.UTF_8).length;
        CRC32 crc = new CRC32();
        crc.update(payload);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(result);
        out.writeInt(MAGIC);
        out.writeByte(VERSION);
        out.writeInt(payload.length);
        out.writeInt(renderedBytes);
        out.writeInt((int) crc.getValue());
        try {
            out.write(
                MessageDigest.getInstance("SHA-256")
                    .digest(original));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        out.write(payload);
        return result.toByteArray();
    }

    public static String read(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION) throw bad("Unsupported critical JSON format");
        int length = in.readInt(), expanded = in.readInt(), checksum = in.readInt();
        if (length < 1 || length > MAX_BYTES || expanded < 1 || expanded > MAX_BYTES)
            throw bad("Invalid payload limits");
        byte[] sha = new byte[32];
        in.readFully(sha);
        byte[] payload = new byte[length];
        in.readFully(payload);
        if (in.read() != -1) throw bad("Trailing compiled data");
        CRC32 crc = new CRC32();
        crc.update(payload);
        if ((int) crc.getValue() != checksum) throw bad("Critical JSON checksum mismatch");
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(payload));
        int size = varint(data);
        if (size > MAX_DICT || size > length) throw bad("Dictionary limit");
        String[] dict = new String[size];
        for (int i = 0; i < size; i++) {
            int bytes = varint(data);
            if (bytes < 1 || bytes > data.available()) throw bad("Dictionary token length");
            byte[] raw = new byte[bytes];
            data.readFully(raw);
            String token = utf8(raw);
            Parser parser = new Parser(token);
            Node n = parser.value(0);
            parser.ws();
            if (parser.at != token.length() || n.children != null || (n.kind != STRING_REF && n.kind != NUMBER_REF))
                throw bad("Invalid dictionary token");
            dict[i] = token;
        }
        Decoder decoder = new Decoder(data, dict, expanded);
        decoder.value(0, false);
        if (data.read() != -1) throw bad("Trailing token data");
        String result = decoder.out.toString();
        if (result.getBytes(StandardCharsets.UTF_8).length != expanded) throw bad("Decoded length mismatch");
        return result;
    }

    private static IOException bad(String message) {
        return new IOException(message);
    }

    private static String utf8(byte[] raw) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(raw))
                .toString();
        } catch (CharacterCodingException e) {
            throw bad("Invalid UTF-8");
        }
    }

    private static byte[] bounded(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            if ((long) out.size() + n > max) throw bad("Input size limit");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static void varint(DataOutputStream out, int n) throws IOException {
        while ((n & ~127) != 0) {
            out.writeByte((n & 127) | 128);
            n >>>= 7;
        }
        out.writeByte(n);
    }

    private static int varint(DataInputStream in) throws IOException {
        int n = 0;
        for (int shift = 0; shift <= 28; shift += 7) {
            int b = in.readUnsignedByte();
            if (shift == 28 && (b & 248) != 0) throw bad("Varint overflow");
            n |= (b & 127) << shift;
            if ((b & 128) == 0) {
                if (shift > 0 && b == 0) throw bad("Noncanonical varint");
                return n;
            }
        }
        throw bad("Varint overflow");
    }

    private static Integer compactInteger(String n) {
        try {
            int i = Integer.parseInt(n);
            return Integer.toString(i)
                .equals(n) && i >= -1073741824
                && i <= 1073741823 ? i : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void collect(Node n, LinkedHashMap<String, Integer> dict) throws IOException {
        if (n.children != null) {
            for (Node child : n.children) collect(child, dict);
        } else if (n.kind == STRING_REF || n.kind == NUMBER_REF && compactInteger(n.token) == null) {
            if (!dict.containsKey(n.token)) {
                if (dict.size() >= MAX_DICT) throw bad("Dictionary limit");
                dict.put(n.token, dict.size());
            }
        }
    }

    private static void write(DataOutputStream out, Node n, Map<String, Integer> dict) throws IOException {
        int[] columns = rowColumns(n);
        if (columns != null) {
            out.writeByte(ROWS);
            varint(out, n.children.size());
            varint(out, columns.length);
            for (int column = 0; column < columns.length; column++) {
                out.writeByte(columns[column]);
                int previous = 0;
                for (Node row : n.children) {
                    Node value = row.children.get(column);
                    if (columns[column] == INTEGER) {
                        int current = compactInteger(value.token), delta = current - previous;
                        varint(out, (delta << 1) ^ (delta >> 31));
                        previous = current;
                    } else if (columns[column] == NUMBER_REF || columns[column] == STRING_REF)
                        varint(out, dict.get(value.token));
                }
            }
            return;
        }
        Integer value = n.kind == NUMBER_REF ? compactInteger(n.token) : null;
        if (value != null) {
            out.writeByte(INTEGER);
            varint(out, (value << 1) ^ (value >> 31));
        } else {
            out.writeByte(n.kind);
            if (n.children != null) {
                varint(out, n.kind == OBJECT ? n.children.size() / 2 : n.children.size());
                for (Node child : n.children) write(out, child, dict);
            } else if (n.kind == STRING_REF || n.kind == NUMBER_REF) varint(out, dict.get(n.token));
        }
    }

    private static int[] rowColumns(Node n) {
        if (n.kind != ARRAY || n.children.size() < 16) return null;
        Node first = n.children.get(0);
        if (first.kind != ARRAY || first.children.isEmpty() || first.children.size() > 32) return null;
        int[] kinds = new int[first.children.size()], previous = new int[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            Node value = first.children.get(i);
            if (value.children != null) return null;
            kinds[i] = value.kind == NUMBER_REF && compactInteger(value.token) != null ? INTEGER : value.kind;
        }
        for (Node row : n.children) {
            if (row.kind != ARRAY || row.children.size() != kinds.length) return null;
            for (int i = 0; i < kinds.length; i++) {
                Node value = row.children.get(i);
                if (value.children != null) return null;
                int kind = value.kind == NUMBER_REF && compactInteger(value.token) != null ? INTEGER : value.kind;
                if (kind != kinds[i]) return null;
                if (kind == INTEGER) {
                    int current = compactInteger(value.token);
                    long delta = (long) current - previous[i];
                    if (delta < -1073741824L || delta > 1073741823L) return null;
                    previous[i] = current;
                }
            }
        }
        return kinds;
    }

    private static void render(Node n, StringBuilder out) {
        if (n.children == null) {
            out.append(n.token);
            return;
        }
        out.append(n.kind == OBJECT ? '{' : '[');
        for (int i = 0; i < n.children.size(); i++) {
            if (i > 0) out.append(n.kind == OBJECT && (i & 1) == 1 ? ':' : ',');
            render(n.children.get(i), out);
        }
        out.append(n.kind == OBJECT ? '}' : ']');
    }

    private static final class Node {

        final int kind;
        final String token;
        final List<Node> children;

        Node(int kind, String token, List<Node> children) {
            this.kind = kind;
            this.token = token;
            this.children = children;
        }
    }

    private static final class Parser {

        final String s;
        int at, nodes;

        Parser(String s) {
            this.s = s;
        }

        void ws() {
            while (at < s.length()
                && (s.charAt(at) == ' ' || s.charAt(at) == '\n' || s.charAt(at) == '\r' || s.charAt(at) == '\t')) at++;
        }

        Node value(int depth) throws IOException {
            ws();
            if (depth > MAX_DEPTH || ++nodes > MAX_NODES || at >= s.length()) throw bad("JSON structure limit");
            int start = at;
            char c = s.charAt(at++);
            if (c == '[' || c == '{') {
                List<Node> children = new ArrayList<>();
                ws();
                char end = c == '[' ? ']' : '}';
                if (at < s.length() && s.charAt(at) == end) {
                    at++;
                    return new Node(c == '[' ? ARRAY : OBJECT, null, children);
                }
                while (true) {
                    Node child = value(depth + 1);
                    if (c == '{') {
                        if (child.kind != STRING_REF) throw bad("Expected object key");
                        children.add(child);
                        ws();
                        if (at >= s.length() || s.charAt(at++) != ':') throw bad("Expected colon");
                        child = value(depth + 1);
                    }
                    children.add(child);
                    ws();
                    if (at >= s.length()) throw bad("Unclosed container");
                    char separator = s.charAt(at++);
                    if (separator == end) break;
                    if (separator != ',') throw bad("Expected comma");
                }
                return new Node(c == '[' ? ARRAY : OBJECT, null, children);
            }
            if (c == '"') {
                boolean closed = false;
                while (at < s.length()) {
                    char ch = s.charAt(at++);
                    if (ch == '"') {
                        closed = true;
                        break;
                    }
                    if (ch < 32) throw bad("Control in string");
                    if (ch == '\\') {
                        if (at >= s.length()) throw bad("Broken string escape");
                        char escape = s.charAt(at++);
                        if (escape == 'u') {
                            for (int i = 0; i < 4; i++)
                                if (at >= s.length() || "0123456789abcdefABCDEF".indexOf(s.charAt(at++)) < 0)
                                    throw bad("Broken unicode escape");
                        } else if ("\"\\/bfnrt".indexOf(escape) < 0) throw bad("Unknown escape");
                    }
                }
                if (!closed) throw bad("Unclosed string");
                return new Node(STRING_REF, s.substring(start, at), null);
            }
            while (at < s.length() && ",]} \r\n\t".indexOf(s.charAt(at)) < 0) at++;
            String token = s.substring(start, at);
            int kind;
            if ("null".equals(token)) kind = NIL;
            else if ("false".equals(token)) kind = FALSE;
            else if ("true".equals(token)) kind = TRUE;
            else if (NUMBER.matcher(token)
                .matches()) kind = NUMBER_REF;
            else throw bad("Invalid JSON token");
            return new Node(kind, token, null);
        }
    }

    private static final class Decoder {

        final DataInputStream in;
        final String[] dict;
        final int limit;
        final StringBuilder out = new StringBuilder();
        int nodes;

        Decoder(DataInputStream in, String[] dict, int limit) {
            this.in = in;
            this.dict = dict;
            this.limit = limit;
        }

        void append(String s) throws IOException {
            if ((long) out.length() + s.length() > limit) throw bad("Expanded size limit");
            out.append(s);
        }

        void value(int depth, boolean key) throws IOException {
            if (depth > MAX_DEPTH || ++nodes > MAX_NODES) throw bad("Decoded structure limit");
            int kind = in.readUnsignedByte();
            if (key && kind != STRING_REF) throw bad("Nonstring object key");
            if (kind == NIL) append("null");
            else if (kind == FALSE) append("false");
            else if (kind == TRUE) append("true");
            else if (kind == INTEGER) {
                int n = varint(in);
                append(Integer.toString((n >>> 1) ^ -(n & 1)));
            } else if (kind == NUMBER_REF || kind == STRING_REF) {
                int ref = varint(in);
                if (ref >= dict.length) throw bad("Dictionary reference limit");
                String s = dict[ref];
                if ((s.charAt(0) == '"') != (kind == STRING_REF)) throw bad("Dictionary token type");
                append(s);
            } else if (kind == ROWS) {
                int rows = varint(in), columns = varint(in);
                if (depth + 2 > MAX_DEPTH || rows < 16
                    || columns < 1
                    || columns > 32
                    || (long) rows * (columns + 1) > MAX_NODES - nodes
                    || (long) rows * columns > limit) throw bad("Row table limit");
                nodes += rows * (columns + 1);
                int[][] cells = new int[columns][];
                int[] kinds = new int[columns];
                for (int column = 0; column < columns; column++) {
                    int columnKind = in.readUnsignedByte(), previous = 0;
                    if (columnKind > STRING_REF) throw bad("Invalid row column type");
                    kinds[column] = columnKind;
                    if (columnKind >= INTEGER) cells[column] = new int[rows];
                    for (int row = 0; row < rows; row++) {
                        if (columnKind == INTEGER) {
                            int n = varint(in), delta = (n >>> 1) ^ -(n & 1);
                            long current = (long) previous + delta;
                            if (current < -1073741824L || current > 1073741823L) throw bad("Row integer overflow");
                            previous = (int) current;
                            cells[column][row] = previous;
                        } else if (columnKind == STRING_REF || columnKind == NUMBER_REF) {
                            int ref = varint(in);
                            if (ref >= dict.length) throw bad("Row dictionary reference");
                            String token = dict[ref];
                            if ((token.charAt(0) == '"') != (columnKind == STRING_REF))
                                throw bad("Row dictionary token type");
                            cells[column][row] = ref;
                        }
                    }
                }
                append("[");
                for (int row = 0; row < rows; row++) {
                    if (row > 0) append(",");
                    append("[");
                    for (int column = 0; column < columns; column++) {
                        if (column > 0) append(",");
                        int columnKind = kinds[column];
                        if (columnKind == INTEGER) append(Integer.toString(cells[column][row]));
                        else if (columnKind == STRING_REF || columnKind == NUMBER_REF) append(dict[cells[column][row]]);
                        else append(columnKind == NIL ? "null" : columnKind == FALSE ? "false" : "true");
                    }
                    append("]");
                }
                append("]");
            } else if (kind == ARRAY || kind == OBJECT) {
                int count = varint(in);
                if (count > MAX_NODES - nodes || count > in.available()) throw bad("Container count limit");
                append(kind == ARRAY ? "[" : "{");
                for (int i = 0; i < count; i++) {
                    if (i > 0) append(",");
                    value(depth + 1, kind == OBJECT);
                    if (kind == OBJECT) {
                        append(":");
                        value(depth + 1, false);
                    }
                }
                append(kind == ARRAY ? "]" : "}");
            } else throw bad("Unknown token tag");
        }
    }
}
