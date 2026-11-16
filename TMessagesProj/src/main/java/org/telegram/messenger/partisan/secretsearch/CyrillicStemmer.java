package org.telegram.messenger.partisan.secretsearch;

final class CyrillicStemmer {
    private CyrillicStemmer() {
    }

    static String stem(String word, CyrillicStemmerProfile profile) {
        int rv = findRvStart(word, profile);
        if (rv >= word.length()) {
            return word;
        }
        int r2 = Math.max(rv, findRegionStart(word, findRegionStart(word, 0, profile), profile));

        String result = removeInflection(word, rv, profile);
        result = cut(result, findSuffixLength(result, rv, null, profile.finalVowel));
        result = cut(result, findSuffixLength(result, r2, null, profile.derivational));
        return tidyUp(result, rv, profile);
    }

    private static String removeInflection(String word, int rv, CyrillicStemmerProfile profile) {
        int gerund = findSuffixLength(word, rv, profile.gerundAfterA, profile.gerund);
        if (gerund > 0) {
            return cut(word, gerund);
        }
        String result = cut(word, findSuffixLength(word, rv, null, profile.reflexive));
        int adjective = findSuffixLength(result, rv, null, profile.adjective);
        if (adjective > 0) {
            result = cut(result, adjective);
            return cut(result, findSuffixLength(result, rv, profile.participleAfterA, profile.participle));
        }
        int verb = findSuffixLength(result, rv, profile.verbAfterA, profile.verb);
        if (verb > 0) {
            return cut(result, verb);
        }
        return cut(result, findSuffixLength(result, rv, null, profile.noun));
    }

    private static String tidyUp(String word, int rv, CyrillicStemmerProfile profile) {
        int superlative = findSuffixLength(word, rv, null, profile.superlative);
        String result = cut(word, superlative);
        if (result.endsWith("нн") && result.length() - 2 >= rv) {
            return cut(result, 1);
        }
        if (superlative == 0 && result.endsWith("ь") && result.length() - 1 >= rv) {
            return cut(result, 1);
        }
        return result;
    }

    // Like Snowball's 'among': the longest suffix inside the region wins, and if its condition
    // (a preceding 'а'/'я' for the afterA group) fails, nothing is removed.
    private static int findSuffixLength(String word, int regionStart, String[] afterA, String[] plain) {
        String longest = null;
        boolean needsPrecedingA = false;
        if (afterA != null) {
            for (String suffix : afterA) {
                if (fitsInRegion(word, suffix, regionStart) && (longest == null || suffix.length() > longest.length())) {
                    longest = suffix;
                    needsPrecedingA = true;
                }
            }
        }
        for (String suffix : plain) {
            if (fitsInRegion(word, suffix, regionStart) && (longest == null || suffix.length() > longest.length())) {
                longest = suffix;
                needsPrecedingA = false;
            }
        }
        if (longest == null) {
            return 0;
        }
        if (needsPrecedingA) {
            int precedingIndex = word.length() - longest.length() - 1;
            if (precedingIndex < regionStart) {
                return 0;
            }
            char preceding = word.charAt(precedingIndex);
            if (preceding != 'а' && preceding != 'я') {
                return 0;
            }
        }
        return longest.length();
    }

    private static boolean fitsInRegion(String word, String suffix, int regionStart) {
        return word.endsWith(suffix) && word.length() - suffix.length() >= regionStart;
    }

    private static int findRvStart(String word, CyrillicStemmerProfile profile) {
        for (int i = 0; i < word.length(); i++) {
            if (profile.isVowel(word.charAt(i))) {
                return i + 1;
            }
        }
        return word.length();
    }

    private static int findRegionStart(String word, int from, CyrillicStemmerProfile profile) {
        for (int i = from + 1; i < word.length(); i++) {
            if (!profile.isVowel(word.charAt(i)) && profile.isVowel(word.charAt(i - 1))) {
                return i + 1;
            }
        }
        return word.length();
    }

    private static String cut(String word, int suffixLength) {
        return suffixLength == 0 ? word : word.substring(0, word.length() - suffixLength);
    }
}
