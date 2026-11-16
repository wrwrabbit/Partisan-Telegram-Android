package org.telegram.messenger.partisan.secretsearch;

// Picks the language of a whole message from letters each alphabet has or lacks:
// Russian has no і/ї/є/ґ/ў, Ukrainian has no ы/э/ё/ъ, Belarusian has no и/щ/ъ.
final class CyrillicLanguageDetector {
    private static final String UKRAINIAN_ONLY_LETTERS = "їєґ";
    private static final String LETTERS_ABSENT_IN_BELARUSIAN = "ищ";
    private static final String LETTERS_ABSENT_IN_UKRAINIAN = "ыэ";

    private CyrillicLanguageDetector() {
    }

    // Expects lowercase text with 'ё' folded to 'е'. Defaults to Russian.
    static CyrillicStemmerProfile detect(String normalizedText) {
        if (containsAnyOf(normalizedText, UKRAINIAN_ONLY_LETTERS)) {
            return CyrillicStemmerProfile.UKRAINIAN;
        }
        if (normalizedText.indexOf('ў') >= 0 || normalizedText.contains("шч")) {
            return CyrillicStemmerProfile.BELARUSIAN;
        }
        if (normalizedText.indexOf('і') >= 0 || normalizedText.contains("ць")) {
            return countUkrainianClues(normalizedText) > countBelarusianClues(normalizedText)
                    ? CyrillicStemmerProfile.UKRAINIAN
                    : CyrillicStemmerProfile.BELARUSIAN;
        }
        return CyrillicStemmerProfile.RUSSIAN;
    }

    private static int countUkrainianClues(String text) {
        return countLettersOf(text, LETTERS_ABSENT_IN_BELARUSIAN) + countOccurrences(text, "ьо");
    }

    private static int countBelarusianClues(String text) {
        return countLettersOf(text, LETTERS_ABSENT_IN_UKRAINIAN) + countOccurrences(text, "ць");
    }

    private static int countLettersOf(String text, String letters) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (letters.indexOf(text.charAt(i)) >= 0) {
                count++;
            }
        }
        return count;
    }

    private static int countOccurrences(String text, String pair) {
        int count = 0;
        for (int i = text.indexOf(pair); i >= 0; i = text.indexOf(pair, i + 1)) {
            count++;
        }
        return count;
    }

    private static boolean containsAnyOf(String text, String letters) {
        return countLettersOf(text, letters) > 0;
    }
}
