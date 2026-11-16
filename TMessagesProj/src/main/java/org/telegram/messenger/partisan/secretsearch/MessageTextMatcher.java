package org.telegram.messenger.partisan.secretsearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Not thread-safe: holds per-search stem caches.
final class MessageTextMatcher {
    private static final Pattern WORD_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}#$']+");
    private static final int MIN_PREFIX_STEM_LENGTH = 3;
    private static final int MAX_CACHED_STEMS_PER_LANGUAGE = 50_000;

    private final String normalizedQuery;
    private final List<String> queryWords;
    // The query is stemmed in the language of each message it is compared with.
    private final Map<CyrillicStemmerProfile, List<String>> queryStemsByLanguage = new HashMap<>();
    private final Map<CyrillicStemmerProfile, Map<String, String>> stemCacheByLanguage = new HashMap<>();

    MessageTextMatcher(String query) {
        normalizedQuery = normalize(query).trim();
        queryWords = splitWords(normalizedQuery);
    }

    boolean isQueryEmpty() {
        return normalizedQuery.isEmpty();
    }

    // Matches when the text contains the whole query, or when every query word matches some
    // word of the text by prefix or by stem.
    boolean matches(String text) {
        if (isQueryEmpty() || text == null || text.isEmpty()) {
            return false;
        }
        String normalizedText = normalize(text);
        if (normalizedText.contains(normalizedQuery)) {
            return true;
        }
        if (queryWords.isEmpty()) {
            return false;
        }
        CyrillicStemmerProfile language = CyrillicLanguageDetector.detect(normalizedText);
        List<String> queryStems = getQueryStems(language);
        List<String> textWords = splitWords(normalizedText);
        for (int i = 0; i < queryWords.size(); i++) {
            if (!containsMatchingWord(textWords, queryWords.get(i), queryStems.get(i), language)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsMatchingWord(List<String> textWords, String queryWord, String queryStem, CyrillicStemmerProfile language) {
        for (String word : textWords) {
            if (word.startsWith(queryWord)) {
                return true;
            }
            String wordStem = stem(word, language);
            if (wordStem.equals(queryStem) || queryStem.length() >= MIN_PREFIX_STEM_LENGTH && wordStem.startsWith(queryStem)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getQueryStems(CyrillicStemmerProfile language) {
        return queryStemsByLanguage.computeIfAbsent(language,
                lang -> queryWords.stream().map(word -> stem(word, lang)).collect(Collectors.toList()));
    }

    private String stem(String word, CyrillicStemmerProfile language) {
        return stemCacheByLanguage.computeIfAbsent(language, lang -> createStemCache())
                .computeIfAbsent(word, w -> stemWord(w, language));
    }

    // Least-recently-used: frequent words stay cached, rare ones are evicted and re-stemmed if seen again.
    private static Map<String, String> createStemCache() {
        return new LinkedHashMap<String, String>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > MAX_CACHED_STEMS_PER_LANGUAGE;
            }
        };
    }

    private static String stemWord(String word, CyrillicStemmerProfile language) {
        if (!isLettersAndApostrophes(word)) {
            return word;
        }
        Character.UnicodeBlock block = Character.UnicodeBlock.of(word.charAt(0));
        if (block == Character.UnicodeBlock.BASIC_LATIN) {
            return EnglishStemmer.stem(word);
        } else if (block == Character.UnicodeBlock.CYRILLIC) {
            return CyrillicStemmer.stem(word, language);
        }
        return word;
    }

    private static boolean isLettersAndApostrophes(String word) {
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (!Character.isLetter(c) && c != '\'') {
                return false;
            }
        }
        return true;
    }

    // Typographic (’) and modifier-letter (ʼ) apostrophes are folded to the plain one.
    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replace('’', '\'')
                .replace('ʼ', '\'');
    }

    private static List<String> splitWords(String normalizedText) {
        List<String> words = new ArrayList<>();
        for (String word : WORD_SEPARATOR.split(normalizedText)) {
            String trimmed = trimApostrophes(word);
            if (!trimmed.isEmpty()) {
                words.add(trimmed);
            }
        }
        return words;
    }

    // Apostrophes at the edges are quotation marks, not part of the word.
    private static String trimApostrophes(String word) {
        int start = 0;
        int end = word.length();
        while (start < end && word.charAt(start) == '\'') {
            start++;
        }
        while (end > start && word.charAt(end - 1) == '\'') {
            end--;
        }
        return word.substring(start, end);
    }
}
