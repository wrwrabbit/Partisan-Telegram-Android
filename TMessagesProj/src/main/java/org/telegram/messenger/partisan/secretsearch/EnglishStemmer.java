package org.telegram.messenger.partisan.secretsearch;

import java.util.Arrays;

// Light suffix stripper: it only needs to map inflected forms of one word to the same stem.
final class EnglishStemmer {
    private static final int MIN_STEM_LENGTH = 3;

    private static final String[] ENGLISH_SUFFIXES = sortedLongestFirst(
            "ingly", "edly", "ings", "ing", "ies", "ied", "es", "ed", "ly", "s"
    );
    private EnglishStemmer() {
    }

    static String stem(String word) {
        String stem = word;
        for (String suffix : ENGLISH_SUFFIXES) {
            if (canStrip(word, suffix) && !(suffix.equals("s") && word.endsWith("ss"))) {
                stem = word.substring(0, word.length() - suffix.length());
                if (suffix.equals("ies") || suffix.equals("ied")) {
                    stem += "i";
                } else if (suffix.startsWith("ing") || suffix.startsWith("ed")) {
                    stem = undoubleFinalConsonant(stem);
                }
                break;
            }
        }
        if (stem.endsWith("e") && stem.length() > MIN_STEM_LENGTH) {
            stem = stem.substring(0, stem.length() - 1);
        }
        if (stem.endsWith("y") && stem.length() > MIN_STEM_LENGTH) {
            stem = stem.substring(0, stem.length() - 1) + "i";
        }
        return stem;
    }

    private static String undoubleFinalConsonant(String stem) {
        int length = stem.length();
        if (length > MIN_STEM_LENGTH && stem.charAt(length - 1) == stem.charAt(length - 2) && "lsz".indexOf(stem.charAt(length - 1)) < 0) {
            return stem.substring(0, length - 1);
        }
        return stem;
    }

    private static boolean canStrip(String word, String suffix) {
        return word.endsWith(suffix) && word.length() - suffix.length() >= MIN_STEM_LENGTH;
    }

    private static String[] sortedLongestFirst(String... suffixes) {
        String[] sorted = suffixes.clone();
        Arrays.sort(sorted, (a, b) -> b.length() - a.length());
        return sorted;
    }
}
