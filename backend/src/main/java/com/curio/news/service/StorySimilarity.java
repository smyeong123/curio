package com.curio.news.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Decides whether two summaries report the same news story when their article URLs differ
 * (another outlet picked the story up a day later). Deterministic and cheap: no AI call.
 *
 * <p>Each story is reduced to its distinctive terms, from the headline and from the summary:
 * <ul>
 *   <li><b>specs</b> (weight {@value #SPEC_WEIGHT}) — tokens mixing letters and digits or version
 *       numbers ({@code 52b}, {@code ml4}, {@code k3.1}, {@code 2.1.312}), and sizes written out
 *       ("1 trillion" → {@code 1t}); these pin a story down far better than words do;</li>
 *   <li><b>product names</b> (weight 1) — a name followed by a version ({@code large 4},
 *       {@code gemini 4 argon}). Weighted low on purpose: a story that merely <em>mentions</em>
 *       a model is not the story about that model;</li>
 *   <li><b>words</b> (weight 1) — every other word except English function words, vendor names,
 *       and the vocabulary nearly every AI story uses ({@code model}, {@code release},
 *       {@code task}, {@code image}, {@code security}, ...).</li>
 * </ul>
 *
 * <p>Two stories are the same only when <em>all</em> of these hold:
 * <ol>
 *   <li>their headlines don't name different vendors (OpenAI vs Anthropic) or different products
 *       (Gemini 4 Argon vs Gemini 4 Carbon);</li>
 *   <li>each headline's subject shows up in the other story — a distinctive headline term of
 *       each appears somewhere in the other's headline or summary;</li>
 *   <li>they share at least {@value #MIN_EVIDENCE} points of distinctive terms.</li>
 * </ol>
 * Tuned on 310 production stories (2026-10-14 to 11-02): every match it made there was a true
 * repeat on manual review. It misses some repeats by design: dropping a genuinely new story costs
 * the reader more than an occasional repeat does.
 */
final class StorySimilarity {

    /** Shared distinctive weight at or above which two stories count as the same. */
    static final int MIN_EVIDENCE = 5;

    static final int SPEC_WEIGHT = 3;
    static final int NAME_WEIGHT = 1;
    static final int WORD_WEIGHT = 1;

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+(?:\\.\\p{N}+)*%?");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\p{N}.*");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("\\p{N}+(?:\\.\\p{N}{1,2})?");
    private static final Pattern VERSION_NUMBER = Pattern.compile("\\p{N}{1,2}|\\p{N}+\\.\\p{N}{1,2}");
    private static final Pattern LETTERS = Pattern.compile("\\p{L}+");

    /** English function words, filler, and dates. */
    private static final Set<String> FUNCTION_WORDS = words("""
            a an the and or but of to in on for with by at from as is are was were be been it its this that
            these those has have had can could will would now not no than then into over after before about up
            out more most less per via also which who what when where how why they their them you your we our
            us he she his her all any some other such so only just while across under between both each do does
            did if may might should must need anyone everyone instead one two three first get gets make makes s
            like own there here still even very much many well toward towards
            january february march april june july august september october november december jan feb mar apr
            jun jul aug sep sept oct nov dec monday tuesday wednesday thursday friday saturday sunday week today
            yesterday day""");

    /** The vocabulary of AI-industry news itself. */
    private static final Set<String> AI_NEWS_WORDS = words("""
            ai artificial intelligence model llm release launch announce unveil introduce ship new latest
            company startup lab user developer customer tool feature product version update available
            availability say said report according use help let allow support access open weight source api app
            platform service news people year month time way work team data system capability agent agentic
            reasoning benchmark performance free plan price pricing token context window parameter roughly
            around nearly almost approximately""");

    /**
     * Plain-language words the digest's summaries lean on ("text and images", "tasks", "think of it
     * as"). Picked from the most frequent terms across the production sample, keeping only words
     * that never identify a story.
     */
    private static final Set<String> PLAIN_WORDS = words("""
            task mean meaning call real without used show software faster using design being publish researcher
            build built code handle research run signal test security public step text directly business
            question world safety train take through beyond cost answer think detail major result standard spend
            high yet action problem cut push human grow assistant change image complex check rather separately
            protect chatbot power scientist scientific program top professional large kind behind share
            reportedly inside because cheaper understand content small aim specialize output powerful compute
            computer math target conversation similar partner off thousand million billion trillion everyday
            explain add control sign block behalf cloud video limit long expert manage reveal act include running
            big surface catch part better internet review early doing pay worker language expand incident stake
            information happen concern track write rival move capable private behavior specific massive far
            usage rule firm longer pattern download during multiple type aren look independently case find
            origin solve employee hit fix broader deal enterprise workflow personal every pro market growth made
            against essentially knowledge tell fast direct cover machine newest level exist generate hour
            technical hand surprise handling chat once best old method science example single decision thing
            widely full notable hidden require goal document development whether already compete basically
            process quietly stock suppose serious publicly response permission easier stay local alongside strong
            general analyze job previously suggest same read done something spot point figure genuine genuinely
            engineer stand digital accessible fewer roll focus soon input project confirm matter quickly sell
            identify automatically scale tier popular half file cheap lower freely knob smart smarter imagine
            give giving anywhere entirely end able""");

    /**
     * Vendor and product-line words, mapped to the vendor. They say nothing about which story it is
     * (every Anthropic story says "Claude"), but two headlines about different vendors are never the
     * same story.
     */
    private static final Map<String, String> VENDORS = vendors(
            "anthropic=anthropic claude=anthropic openai=openai chatgpt=openai gpt=openai google=google "
            + "gemini=google deepmind=google meta=meta llama=meta mistral=mistral microsoft=microsoft "
            + "copilot=microsoft xai=xai grok=xai nvidia=nvidia ibm=ibm granite=ibm deepseek=deepseek "
            + "alibaba=alibaba qwen=alibaba apple=apple amazon=amazon aws=amazon liquid=liquid tii=tii "
            + "falcon=tii cohere=cohere perplexity=perplexity kimi=moonshot moonshot=moonshot baidu=baidu "
            + "tencent=tencent samsung=samsung salesforce=salesforce adobe=adobe stability=stability "
            + "runway=runway higgsfield=higgsfield minimax=minimax zhipu=zhipu");

    /** Vendor words that also name a model line, so "Gemini 4" and "Claude 4.5" are product names. */
    private static final Set<String> PRODUCT_LINES = words("claude gpt gemini llama mistral grok qwen deepseek copilot granite");

    /** Never distinctive: function words, generic vocabulary and vendors, compared after stemming. */
    private static final Set<String> STOP_STEMS = Stream.of(FUNCTION_WORDS, AI_NEWS_WORDS, PLAIN_WORDS,
                    VENDORS.keySet(), Set.of("hugging", "face"))
            .flatMap(Set::stream).map(StorySimilarity::stem).collect(Collectors.toUnmodifiableSet());

    /** Words that may precede a version number without naming anything ("on October 29", "version 2"). */
    private static final Set<String> NOT_A_NAME = Stream.of(FUNCTION_WORDS, AI_NEWS_WORDS)
            .flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());

    /** A number before one of these is a quantity ("4 times"), not part of a product name. */
    private static final Set<String> QUANTITY = words("""
            thousand million billion trillion percent times x hours minutes seconds days weeks months years""");

    /** "1 trillion" and "1-trillion" become the spec token {@code 1t}, matching "~1T-parameter". */
    private static final Map<String, String> SCALE = Map.of("thousand", "k", "million", "m", "billion", "b", "trillion", "t");

    private StorySimilarity() {}

    /**
     * A story reduced to weighted distinctive terms.
     *
     * @param headline terms from the headline alone (its subject)
     * @param all      terms from headline and summary
     * @param vendors  the vendors its headline names
     */
    record Fingerprint(Map<String, Integer> headline, Map<String, Integer> all, Set<String> vendors) {
        static final Fingerprint EMPTY = new Fingerprint(Map.of(), Map.of(), Set.of());

        boolean isEmpty() { return all.isEmpty(); }

        /** Product names in the headline ("gemini 4 argon"); the only terms that contain a space. */
        Set<String> headlineProducts() {
            return headline.keySet().stream().filter(t -> t.indexOf(' ') >= 0).collect(Collectors.toSet());
        }
    }

    /** Builds the fingerprint of a story from its headline and summary (either may be null). */
    static Fingerprint fingerprint(String headline, String summary) {
        Map<String, Integer> head = terms(headline);
        Map<String, Integer> all = new HashMap<>(terms(summary));
        all.putAll(head);
        if (all.isEmpty()) return Fingerprint.EMPTY;
        return new Fingerprint(Collections.unmodifiableMap(head), Collections.unmodifiableMap(all), vendorsIn(headline));
    }

    /** True when the two fingerprints describe the same story; empty fingerprints never match. */
    static boolean sameStory(Fingerprint a, Fingerprint b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (aboutDifferentVendors(a, b) || aboutDifferentProducts(a, b)) return false;
        if (!headlineCoveredBy(a, b) || !headlineCoveredBy(b, a)) return false;
        return evidence(a, b) >= MIN_EVIDENCE;
    }

    /** Total weight of the distinctive terms the two stories share. */
    static int evidence(Fingerprint a, Fingerprint b) {
        int shared = 0;
        for (Map.Entry<String, Integer> term : a.all().entrySet()) {
            if (b.all().containsKey(term.getKey())) shared += term.getValue();
        }
        return shared;
    }

    /** "OpenAI acquires a drug-discovery firm" is not "Anthropic speeds up drug discovery". */
    private static boolean aboutDifferentVendors(Fingerprint a, Fingerprint b) {
        return !a.vendors().isEmpty() && !b.vendors().isEmpty() && Collections.disjoint(a.vendors(), b.vendors());
    }

    /** "Gemini 4 Argon launches" is not "Gemini 4 Carbon launches", however alike the rest reads. */
    private static boolean aboutDifferentProducts(Fingerprint a, Fingerprint b) {
        Set<String> productsA = a.headlineProducts();
        Set<String> productsB = b.headlineProducts();
        return !productsA.isEmpty() && !productsB.isEmpty() && Collections.disjoint(productsA, productsB);
    }

    /**
     * Whether some distinctive term of {@code story}'s headline appears in {@code other} at all.
     * Keeps a story whose summary only mentions an older one (in passing) from matching it.
     */
    private static boolean headlineCoveredBy(Fingerprint story, Fingerprint other) {
        for (String term : story.headline().keySet()) {
            if (other.all().containsKey(term)) return true;
        }
        return false;
    }

    private static Map<String, Integer> terms(String text) {
        Map<String, Integer> terms = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return terms;
        String source = text.replace("'s", "").replace("’s", "");
        List<Token> tokens = tokenize(source);
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            String nextWord = next == null ? null : next.lower();
            String t = token.lower();
            if (t.endsWith("%")) {
                continue; // "40% discount": a share, not a name
            } else if (HAS_DIGIT.matcher(t).matches() && !PLAIN_NUMBER.matcher(t).matches()) {
                terms.put(t, SPEC_WEIGHT);
            } else if (PLAIN_NUMBER.matcher(t).matches() && next != null && SCALE.containsKey(nextWord)
                    && gap(source, token, next).strip().matches("-?")) {
                terms.put(t + SCALE.get(nextWord), SPEC_WEIGHT);
            } else if (VERSION_NUMBER.matcher(t).matches() && i > 0 && isNameWord(tokens.get(i - 1).lower())
                    && gap(source, tokens.get(i - 1), token).isBlank() && !QUANTITY.contains(nextWord)) {
                String name = tokens.get(i - 1).lower() + " " + t;
                // "Gemini 4 Argon": a capitalised word straight after the version completes the name.
                if (next != null && gap(source, token, next).isBlank()
                        && Character.isUpperCase(next.text().charAt(0)) && isWord(nextWord)) {
                    name += " " + stem(nextWord);
                    i++;
                }
                terms.put(name, NAME_WEIGHT);
            } else if (isWord(t)) {
                terms.putIfAbsent(stem(t), WORD_WEIGHT);
            }
        }
        return terms;
    }

    private record Token(String text, int start, int end) {
        String lower() { return text.toLowerCase(Locale.ROOT); }
    }

    private static List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        Matcher m = TOKEN.matcher(text);
        while (m.find()) tokens.add(new Token(m.group(), m.start(), m.end()));
        return tokens;
    }

    private static String gap(String source, Token left, Token right) {
        return source.substring(left.end(), right.start());
    }

    private static boolean isNameWord(String token) {
        return token.length() >= 3 && LETTERS.matcher(token).matches()
                && (PRODUCT_LINES.contains(token) || !(NOT_A_NAME.contains(token) || VENDORS.containsKey(token)));
    }

    private static boolean isWord(String token) {
        return token.length() >= 3 && LETTERS.matcher(token).matches() && !STOP_STEMS.contains(stem(token));
    }

    /** Light suffix stripping so "extensions"/"extension", "coding"/"code" and "robotics"/"robot" meet. */
    static String stem(String word) {
        String w = word;
        if (w.length() > 5 && w.endsWith("ies")) return w.substring(0, w.length() - 3) + "y";
        if (w.length() > 5 && w.endsWith("ing")) {
            w = w.substring(0, w.length() - 3);
        } else if (w.length() > 4 && w.endsWith("ed")) {
            w = w.substring(0, w.length() - 2);
        } else if (w.length() > 5 && (w.endsWith("ches") || w.endsWith("shes") || w.endsWith("xes"))) {
            w = w.substring(0, w.length() - 2);
        } else if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) {
            w = w.substring(0, w.length() - 1);
        }
        if (w.length() > 3 && w.endsWith("e")) w = w.substring(0, w.length() - 1);
        if (w.length() > 6 && w.endsWith("ical")) {
            w = w.substring(0, w.length() - 4);
        } else if (w.length() > 6 && w.endsWith("ic")) {
            w = w.substring(0, w.length() - 2);
        }
        return w;
    }

    private static Set<String> vendorsIn(String headline) {
        Set<String> found = new HashSet<>();
        if (headline == null) return found;
        Matcher m = LETTERS.matcher(headline.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String vendor = VENDORS.get(m.group());
            if (vendor != null) found.add(vendor);
        }
        return Set.copyOf(found);
    }

    private static Set<String> words(String list) {
        return Set.copyOf(List.of(list.trim().split("\\s+")));
    }

    private static Map<String, String> vendors(String pairs) {
        Map<String, String> map = new HashMap<>();
        for (String pair : pairs.trim().split("\\s+")) {
            String[] kv = pair.split("=");
            map.put(kv[0], kv[1]);
        }
        return Map.copyOf(map);
    }
}
