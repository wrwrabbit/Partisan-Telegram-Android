package org.telegram.messenger.partisan.secretsearch;

// Vowels and ending tables for CyrillicStemmer. RUSSIAN is the Snowball algorithm as published.
// UKRAINIAN and BELARUSIAN are our own tables with no reference test vocabulary: they skip the
// participle step (stripping participle suffixes split adjective families) and list past-tense and
// infinitive endings together with their stem vowel, so "писав", "писала" and "писати" share a stem.
final class CyrillicStemmerProfile {
    private static final String[] NONE = {};

    static final CyrillicStemmerProfile RUSSIAN = new Builder("аеиоуыэюя")
            .gerund(new String[]{"вшись", "вши", "в"},
                    "ывшись", "ившись", "ывши", "ивши", "ыв", "ив")
            .reflexive("ся", "сь")
            .adjective("ими", "ыми", "его", "ого", "ему", "ому",
                    "ее", "ие", "ые", "ое", "ей", "ий", "ый", "ой", "ем", "им", "ым", "ом",
                    "их", "ых", "ую", "юю", "ая", "яя", "ою", "ею")
            .participle(new String[]{"ем", "нн", "вш", "ющ", "щ"},
                    "ивш", "ывш", "ующ")
            .verb(new String[]{"ете", "йте", "ешь", "нно", "ла", "на", "ли", "ем", "ло", "но", "ет", "ют", "ны", "ть", "й", "л", "н"},
                    "ейте", "уйте", "ила", "ыла", "ена", "ите", "или", "ыли", "ило", "ыло", "ено", "ует", "уют", "ены",
                    "ить", "ыть", "ишь", "ей", "уй", "ил", "ыл", "им", "ым", "ен", "ят", "ит", "ыт", "ую", "ю")
            .noun("иями", "ями", "ами", "ией", "иям", "ием", "иях",
                    "ев", "ов", "ие", "ье", "еи", "ии", "ей", "ой", "ий", "ям", "ем", "ам", "ом", "ах", "ях", "ию", "ью", "ия", "ья",
                    "а", "е", "и", "й", "о", "у", "ы", "ь", "ю", "я")
            .finalVowel("и")
            .superlative("ейше", "ейш")
            .derivational("ость", "ост")
            .build();

    static final CyrillicStemmerProfile UKRAINIAN = new Builder("аеиіоуяюєї")
            .gerund(NONE,
                    "вшись", "вши", "ючись", "учись", "ячись", "ачись", "ючи", "учи", "ячи", "ачи")
            .reflexive("ся", "сь")
            .adjective("ими", "іми", "ього", "ого", "ьому", "ому", "ьої", "ої", "ьою",
                    "ий", "ій", "им", "ім", "их", "іх")
            .verb(NONE,
                    "ати", "яти", "ити", "іти", "ути", "оти",
                    "ав", "яв", "ив", "ів", "ув",
                    "ала", "яла", "ила", "іла", "ула", "ало", "яло", "ило", "іло", "уло",
                    "али", "яли", "или", "іли", "ули",
                    "ємо", "емо", "имо", "імо", "єте", "ете", "ите", "іте",
                    "єш", "еш", "иш", "іш", "ить", "іть", "ать", "ять", "ють", "уть",
                    "ймо", "йте")
            .noun("ами", "ями", "ові", "еві", "єві",
                    "ах", "ях", "ам", "ям", "ом", "ем", "єм", "ою", "ею", "єю", "ей", "ії", "ія", "ію", "ій",
                    "а", "я", "о", "е", "є", "у", "ю", "і", "и", "ї", "ь", "й")
            .superlative("іш")
            .derivational("ість", "іст")
            .build();

    static final CyrillicStemmerProfile BELARUSIAN = new Builder("аеіоуыэюя")
            .gerund(NONE,
                    "ўшыся", "ўшы", "ючыся", "учыся", "ячыся", "ачыся", "ючы", "учы", "ячы", "ачы")
            .reflexive("ся")
            .adjective("ага", "яга", "аму", "яму", "ымі", "імі",
                    "ых", "іх", "ым", "ім", "ай", "яй", "ой", "ую", "юю", "ае", "яе", "ое", "ыя", "ія", "ая", "яя")
            .verb(NONE,
                    "ецца", "іцца", "ацца", "яцца", "ыцца", "уцца", "цца",
                    "аць", "яць", "іць", "ыць", "уць", "юць", "ець", "ці",
                    "аў", "яў", "іў", "ыў", "еў",
                    "ала", "яла", "іла", "ыла", "ела", "ало", "яло", "іло", "ыло", "ело",
                    "алі", "ялі", "ілі", "ылі", "елі",
                    "аце", "яце", "іце", "еце", "аш", "яш", "іш", "еш")
            .noun("амі", "ямі",
                    "аў", "яў", "оў", "еў", "ам", "ям", "ах", "ях", "ой", "ою", "ей", "ею", "аю", "яю",
                    "а", "я", "о", "е", "у", "ю", "і", "ы", "ь", "й")
            .superlative("ейш")
            .derivational("асць", "асц")
            .build();

    final String vowels;
    final String[] gerundAfterA;
    final String[] gerund;
    final String[] reflexive;
    final String[] adjective;
    final String[] participleAfterA;
    final String[] participle;
    final String[] verbAfterA;
    final String[] verb;
    final String[] noun;
    final String[] finalVowel;
    final String[] superlative;
    final String[] derivational;

    private CyrillicStemmerProfile(Builder builder) {
        vowels = builder.vowels;
        gerundAfterA = builder.gerundAfterA;
        gerund = builder.gerund;
        reflexive = builder.reflexive;
        adjective = builder.adjective;
        participleAfterA = builder.participleAfterA;
        participle = builder.participle;
        verbAfterA = builder.verbAfterA;
        verb = builder.verb;
        noun = builder.noun;
        finalVowel = builder.finalVowel;
        superlative = builder.superlative;
        derivational = builder.derivational;
    }

    boolean isVowel(char c) {
        return vowels.indexOf(c) >= 0;
    }

    private static class Builder {
        private final String vowels;
        private String[] gerundAfterA = NONE;
        private String[] gerund = NONE;
        private String[] reflexive = NONE;
        private String[] adjective = NONE;
        private String[] participleAfterA = NONE;
        private String[] participle = NONE;
        private String[] verbAfterA = NONE;
        private String[] verb = NONE;
        private String[] noun = NONE;
        private String[] finalVowel = NONE;
        private String[] superlative = NONE;
        private String[] derivational = NONE;

        Builder(String vowels) {
            this.vowels = vowels;
        }

        Builder gerund(String[] afterA, String... plain) {
            gerundAfterA = afterA;
            gerund = plain;
            return this;
        }

        Builder reflexive(String... endings) {
            reflexive = endings;
            return this;
        }

        Builder adjective(String... endings) {
            adjective = endings;
            return this;
        }

        Builder participle(String[] afterA, String... plain) {
            participleAfterA = afterA;
            participle = plain;
            return this;
        }

        Builder verb(String[] afterA, String... plain) {
            verbAfterA = afterA;
            verb = plain;
            return this;
        }

        Builder noun(String... endings) {
            noun = endings;
            return this;
        }

        Builder finalVowel(String... endings) {
            finalVowel = endings;
            return this;
        }

        Builder superlative(String... endings) {
            superlative = endings;
            return this;
        }

        Builder derivational(String... endings) {
            derivational = endings;
            return this;
        }

        CyrillicStemmerProfile build() {
            return new CyrillicStemmerProfile(this);
        }
    }
}
