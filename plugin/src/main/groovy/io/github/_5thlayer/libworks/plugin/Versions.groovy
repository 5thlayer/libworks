// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.plugin

import org.gradle.api.GradleException

/** The dotted versions a Module names, and the ranges it requires them in (ADR 0001). */
final class Versions {
    private Versions() {}

    static List<Integer> parse(String version) {
        if (!(version ==~ /\d+(\.\d+)*/)) throw new GradleException("libworks: '$version' is not a version, such as 0.5.4")
        version.tokenize('.')*.toInteger()
    }

    static int compare(String a, String b) {
        def (x, y) = [parse(a), parse(b)]
        def i = (0..<Math.max(x.size(), y.size())).find { (x[it] ?: 0) != (y[it] ?: 0) }
        i == null ? 0 : (x[i] ?: 0) <=> (y[i] ?: 0)
    }

    /** The next breaking version, ADR 0001: the next minor below 1.0, the next major from it. */
    static String nextBreaking(String version) {
        def parts = parse(version)
        parts[0] == 0 ? "0.${(parts[1] ?: 0) + 1}" : "${parts[0] + 1}.0"
    }

    /** A Maven range with an inclusive lower bound, such as {@code [0.5.4,0.6)}, parsed once. */
    static final class Range {
        final String text
        final String lowerBound
        private final String upper
        private final boolean upperInclusive

        private Range(String text, String lowerBound, String upper, boolean upperInclusive) {
            this.text = text
            this.lowerBound = lowerBound
            this.upper = upper
            this.upperInclusive = upperInclusive
        }

        /** Null when {@code text} is not such a range. */
        static Range parse(String text) {
            def m = text =~ /^\[([^,\]]+),([^\])]*)([\])])$/
            if (!m.matches()) return null
            Versions.parse(m.group(1))
            if (m.group(2)) Versions.parse(m.group(2))
            new Range(text, m.group(1), m.group(2), m.group(3) == ']')
        }

        boolean contains(String version) {
            if (compare(version, lowerBound) < 0) return false
            if (!upper) return true
            upperInclusive ? compare(version, upper) <= 0 : compare(version, upper) < 0
        }

        @Override
        String toString() { text }
    }
}
