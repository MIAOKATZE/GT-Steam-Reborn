// SPDX-License-Identifier: AGPL-3.0-only

package com.miaokatze.gtsr.common.machine.orecenter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

/** The ore-filter card syntax used by the preview, evaluated as a bounded nondeterministic automaton. */
final class CenterOreRule {

    static final int MAX_LENGTH = 1024;
    private static final int MAX_NAME = 192, MAX_DEPTH = 24, MAX_TRANSITIONS = 24000;
    private static final Pattern TAG = Pattern.compile("[A-Za-z0-9_]{1,192}");
    private static final Pattern ITEM = Pattern.compile("item:[A-Za-z0-9_.|/-]+:[A-Za-z0-9_.|/-]+@[0-9]{1,5}");
    private static final String PUNCTUATION = ".*+?^$()[]{}|\\-/";

    private final String exact, item;
    private final List<State> states;
    private final int entry;
    private final boolean anchored;
    private final Map<String, Byte> memo = new LinkedHashMap<>();

    static final class Limit extends RuntimeException {

        private Limit() {
            super(null, null, false, false);
        }
    }

    private static final Limit LIMIT = new Limit();

    CenterOreRule(String expression) {
        if (expression == null || expression.isEmpty()
            || expression.length() > MAX_LENGTH
            || expression.indexOf('\n') >= 0
            || expression.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Ore rule must have 1–1024 characters on one line");
        }
        if (TAG.matcher(expression)
            .matches()) {
            exact = expression;
            item = null;
            states = null;
            entry = 0;
            anchored = false;
        } else if (ITEM.matcher(expression)
            .matches()) {
                exact = null;
                item = expression.substring(5);
                states = null;
                entry = 0;
                anchored = false;
            } else {
                if (expression.startsWith("item:")) throw new IllegalArgumentException("Invalid registered item rule");
                exact = item = null;
                Compiler compiler = new Compiler(expression);
                List<List<Atom>> branches = compiler.branches(0);
                if (compiler.position != expression.length()) throw compiler.invalid();
                compiler.check(branches);
                states = new ArrayList<>();
                states.add(new State(Kind.ACCEPT));
                entry = emit(branches, 0);
                anchored = branches.stream()
                    .allMatch(sequence -> sequence.get(0).kind == Kind.START);
            }
    }

    boolean matches(ItemStack stack, String[] names) {
        if (item != null) {
            UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(stack.getItem());
            return id != null && item.equals(id + "@" + stack.getItemDamage());
        }
        for (String name : names) {
            if (exact == null ? matchesName(name) : exact.equals(name)) return true;
        }
        return false;
    }

    private boolean matchesName(String name) {
        if (name.length() > MAX_NAME) return false;
        Byte verdict = memo.get(name);
        if (verdict == null) {
            Search search = new Search(name);
            boolean matched = search.find(entry, 0, anchored ? 0 : name.length());
            verdict = (byte) (search.remaining < 0 ? -1 : matched ? 1 : 0);
            if (memo.size() >= 64) memo.remove(
                memo.keySet()
                    .iterator()
                    .next());
            memo.put(name, verdict);
        }
        if (verdict < 0) throw LIMIT;
        return verdict == 1;
    }

    private enum Kind {
        ACCEPT,
        CHAR,
        START,
        END,
        GROUP,
        ASSERT,
        FORK
    }

    private static final class Atom {

        final Kind kind;
        IntPredicate predicate;
        List<List<Atom>> branches;
        boolean positive;
        char repeat;

        Atom(Kind kind) {
            this.kind = kind;
        }
    }

    private static final class State {

        final Kind kind;
        IntPredicate predicate;
        int next, assertion;
        boolean positive;
        int[] targets;

        State(Kind kind) {
            this.kind = kind;
        }
    }

    private int add(State state) {
        states.add(state);
        return states.size() - 1;
    }

    private int emit(List<List<Atom>> branches, int successor) {
        int[] options = new int[branches.size()];
        int i = 0;
        for (List<Atom> sequence : branches) {
            int next = successor;
            for (int index = sequence.size() - 1; index >= 0; index--) {
                Atom atom = sequence.get(index);
                if (atom.repeat == 0) {
                    next = emit(atom, next);
                    continue;
                }
                State fork = new State(Kind.FORK);
                int alternative = add(fork);
                int use = emit(atom, atom.repeat == '?' ? next : alternative);
                fork.targets = new int[] { next, use };
                next = atom.repeat == '+' ? use : alternative;
            }
            options[i++] = next;
        }
        if (options.length == 1) return options[0];
        State fork = new State(Kind.FORK);
        fork.targets = options;
        return add(fork);
    }

    private int emit(Atom atom, int next) {
        if (atom.kind == Kind.GROUP) return emit(atom.branches, next);
        State state = new State(atom.kind);
        state.next = next;
        state.predicate = atom.predicate;
        if (atom.kind == Kind.ASSERT) {
            state.assertion = emit(atom.branches, 0);
            state.positive = atom.positive;
        }
        return add(state);
    }

    private final class Search {

        final String name;
        final int width;
        final Map<Integer, Boolean> assertions = new HashMap<>();
        int remaining = MAX_TRANSITIONS;

        Search(String name) {
            this.name = name;
            width = name.length() + 1;
        }

        boolean find(int start, int from, int to) {
            Set<Integer> seen = new HashSet<>();
            ArrayDeque<Integer> pending = new ArrayDeque<>();
            for (int i = to; i >= from; i--) pending.push(start * width + i);
            while (!pending.isEmpty()) {
                if (--remaining < 0) return false;
                int key = pending.pop();
                if (!seen.add(key)) continue;
                int offset = key % width;
                State state = states.get(key / width);
                int next = state.next * width + offset;
                switch (state.kind) {
                    case ACCEPT:
                        return true;
                    case FORK:
                        for (int target : state.targets) pending.push(target * width + offset);
                        break;
                    case ASSERT:
                        int assertion = state.assertion * width + offset;
                        Boolean match = assertions.get(assertion);
                        if (match == null) {
                            match = find(state.assertion, offset, offset);
                            assertions.put(assertion, match);
                        }
                        if (remaining < 0) return false;
                        if (match == state.positive) pending.push(next);
                        break;
                    case START:
                        if (offset == 0) pending.push(next);
                        break;
                    case END:
                        if (offset == name.length()) pending.push(next);
                        break;
                    case CHAR:
                        if (offset < name.length() && state.predicate.test(name.charAt(offset))) pending.push(next + 1);
                        break;
                    default:
                        throw new IllegalStateException("Uncompiled ore rule");
                }
            }
            return false;
        }
    }

    private static final class Compiler {

        final String source;
        int position;

        Compiler(String source) {
            this.source = source;
        }

        char peek() {
            return position < source.length() ? source.charAt(position) : 0;
        }

        IllegalArgumentException invalid() {
            return new IllegalArgumentException("Unsupported ore-rule expression at character " + (position + 1));
        }

        List<List<Atom>> branches(int depth) {
            if (depth > MAX_DEPTH) throw invalid();
            List<List<Atom>> result = new ArrayList<>();
            do {
                List<Atom> sequence = new ArrayList<>();
                while (peek() != 0 && peek() != '|' && peek() != ')') {
                    Atom atom = atom(depth);
                    char q = peek();
                    if (q == '?' || q == '*' || q == '+') {
                        if ((atom.kind != Kind.CHAR && atom.kind != Kind.GROUP)
                            || (atom.kind == Kind.GROUP && q != '?')) throw invalid();
                        atom.repeat = q;
                        position++;
                    }
                    sequence.add(atom);
                }
                if (sequence.isEmpty()) throw invalid();
                result.add(sequence);
                if (peek() != '|') break;
                position++;
            } while (true);
            return result;
        }

        private Atom atom(int depth) {
            char ch = source.charAt(position++);
            if (ch == '(') {
                char modifier = 0;
                if (peek() == '?') {
                    position++;
                    modifier = peek();
                    if (modifier != '=' && modifier != '!' && modifier != ':') throw invalid();
                    position++;
                }
                Atom group = new Atom(modifier == '=' || modifier == '!' ? Kind.ASSERT : Kind.GROUP);
                group.branches = branches(depth + 1);
                group.positive = modifier == '=';
                if (peek() != ')') throw invalid();
                position++;
                return group;
            }
            if (ch == '^') return new Atom(Kind.START);
            if (ch == '$') return new Atom(Kind.END);
            Atom atom = new Atom(Kind.CHAR);
            if (ch == '[') {
                int start = position - 1;
                while (peek() != 0 && peek() != ']') {
                    char member = source.charAt(position++);
                    if (member == '[' || member == '&' && peek() == '&') throw invalid();
                    if (member == '\\') {
                        if (peek() == 0 || PUNCTUATION.indexOf(peek()) < 0) throw invalid();
                        position++;
                    }
                }
                if (peek() != ']') throw invalid();
                Pattern single = Pattern.compile(source.substring(start, ++position));
                atom.predicate = c -> single.matcher(String.valueOf((char) c))
                    .matches();
            } else if (ch == '\\') {
                if (peek() == 0) throw invalid();
                char escape = source.charAt(position++);
                if (PUNCTUATION.indexOf(escape) >= 0) atom.predicate = c -> c == escape;
                else if ("dDwWsS".indexOf(escape) >= 0) {
                    Pattern single = Pattern.compile("\\" + escape);
                    atom.predicate = c -> single.matcher(String.valueOf((char) c))
                        .matches();
                } else throw invalid();
            } else if (ch == '.') atom.predicate = c -> "\n\r\u0085\u2028\u2029".indexOf(c) < 0;
            else {
                if ("*+?{}]".indexOf(ch) >= 0) throw invalid();
                atom.predicate = c -> c == ch;
            }
            return atom;
        }

        int[] check(List<List<Atom>> branches) {
            int optional = 0, loops = 0;
            for (List<Atom> sequence : branches) {
                int q = 0, r = 0;
                boolean assertion = false;
                for (Atom atom : sequence) {
                    if (atom.repeat == '?') q++;
                    if (atom.repeat == '*' || atom.repeat == '+') r++;
                    if (atom.branches != null) {
                        int[] nested = check(atom.branches);
                        if (atom.kind == Kind.GROUP) {
                            q += nested[0];
                            r += nested[1];
                        }
                    }
                    if (assertion && atom.kind == Kind.ASSERT) throw invalid();
                    assertion = atom.kind == Kind.ASSERT;
                }
                optional = Math.max(optional, q);
                loops = Math.max(loops, r);
            }
            if (optional > 1 || loops > 1) throw invalid();
            return new int[] { optional, loops };
        }
    }
}
