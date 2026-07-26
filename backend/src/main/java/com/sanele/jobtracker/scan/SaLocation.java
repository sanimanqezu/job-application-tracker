package com.sanele.jobtracker.scan;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether a job's location is in South Africa, reading the job's own
 * location string (never the company name).
 *
 * <p>South Africa has well over ten thousand named places, so instead of a
 * hand-written list this matches against the full GeoNames gazetteer for ZA
 * (every city, town, suburb, district and township), bundled as
 * {@code /sa-places.txt}. Tiered precedence resolves the tricky collisions —
 * SA has towns named Berlin, Paris and Wellington, and "East London" must not
 * be read as London, UK:
 * <ol>
 *   <li>explicit SA country / province / ZA  → keep (decisive);</li>
 *   <li>explicit foreign <b>country</b>       → drop (beats a colliding town name
 *       like "Berlin, Germany");</li>
 *   <li>a known SA place from the gazetteer   → keep;</li>
 *   <li>a foreign city                        → drop;</li>
 *   <li>remote / anywhere, no country         → keep (assume SA-remote);</li>
 *   <li>unrecognised, no foreign signal       → keep (see {@link #KEEP_UNRECOGNISED}).</li>
 * </ol>
 * To keep only listings with a confirmed SA place or explicit remote, set
 * {@link #KEEP_UNRECOGNISED} to {@code false}.
 */
public final class SaLocation {

    private SaLocation() {}

    /** Every named place in South Africa (lower-cased), loaded from GeoNames. */
    private static final Set<String> PLACES = loadGazetteer();

    private static Set<String> loadGazetteer() {
        Set<String> set = new HashSet<>(20_000);
        try (InputStream in = SaLocation.class.getResourceAsStream("/sa-places.txt")) {
            if (in != null) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String s = line.trim();
                        if (!s.isEmpty()) set.add(s);
                    }
                }
            }
        } catch (Exception ignore) {
            // Degrade gracefully: the province/country/foreign regexes below still work.
        }
        return set;
    }

    // Country + all nine provinces + common abbreviations (decisive positive signal).
    private static final Pattern SA_ADMIN = Pattern.compile(
            "south africa|republic of south africa|\\brsa\\b|\\bza\\b|mzansi|"
            + "gauteng|western cape|eastern cape|northern cape|north[\\s-]?west|"
            + "kwazulu[\\s-]?natal|\\bkzn\\b|free state|limpopo|mpumalanga",
            Pattern.CASE_INSENSITIVE);

    // Informal names / city abbreviations that won't be in the formal gazetteer.
    private static final Pattern SA_ALIAS = Pattern.compile(
            "\\bcapetown\\b|cape[\\s-]town|\\bjoburg\\b|jo'burg|\\bjhb\\b|\\bjnb\\b|\\bjozi\\b|egoli|"
            + "\\bpta\\b|\\bcpt\\b|\\bdbn\\b|durbs|\\bpmb\\b|nelson mandela bay|ekurhuleni|ethekwini|\\bbloem\\b",
            Pattern.CASE_INSENSITIVE);

    // Foreign countries — decisive negative signal, overrides colliding SA town names.
    private static final Pattern FOREIGN_COUNTRY = Pattern.compile(
            "united states|\\bu\\.?s\\.?a?\\.?\\b|\\bamerica|canada|united kingdom|\\bu\\.?k\\.?\\b|"
            + "\\bengland\\b|scotland|\\bwales\\b|ireland|germany|france|netherlands|holland|spain|"
            + "portugal|\\bitaly\\b|poland|sweden|norway|denmark|finland|switzerland|austria|belgium|"
            + "greece|romania|ukraine|russia|india|pakistan|bangladesh|philippines|singapore|malaysia|"
            + "indonesia|thailand|vietnam|\\bchina\\b|\\bjapan\\b|korea|hong kong|australia|new zealand|"
            + "brazil|mexico|argentina|chile|colombia|egypt|morocco|\\bkenya\\b|nigeria|ghana|ethiopia|"
            + "uganda|tanzania|zambia|zimbabwe|botswana|namibia|mozambique|mauritius|angola|rwanda|"
            + "united arab emirates|\\buae\\b|qatar|saudi|kuwait|bahrain|\\boman\\b|israel|turkey",
            Pattern.CASE_INSENSITIVE);

    // Foreign cities — checked only after SA places, so an SA "Berlin" wins.
    private static final Pattern FOREIGN_CITY = Pattern.compile(
            "london|manchester|birmingham|dublin|new york|san francisco|austin|seattle|boston|chicago|"
            + "toronto|vancouver|berlin|munich|frankfurt|amsterdam|\\bparis\\b|madrid|barcelona|lisbon|"
            + "warsaw|stockholm|zurich|sydney|melbourne|bangalore|bengaluru|mumbai|\\bdelhi\\b|hyderabad|"
            + "chennai|\\bpune\\b|lagos|abuja|nairobi|cairo|accra|harare|gaborone|windhoek|tel aviv|"
            + "dubai|abu dhabi",
            Pattern.CASE_INSENSITIVE);

    // World-famous foreign cities that also exist as obscure SA namesakes (SA has
    // towns called Milan, Berlin, Amsterdam…). Bare, these mean the foreign city,
    // so they override the gazetteer. A province qualifier ("…, Gauteng") still wins.
    private static final Set<String> FOREIGN_MAJOR = Set.of(
            "milan", "rome", "naples", "venice", "florence", "turin", "genoa", "bologna",
            "lyon", "marseille", "brussels", "antwerp", "rotterdam", "vienna", "geneva",
            "prague", "budapest", "krakow", "athens", "oslo", "helsinki", "copenhagen",
            "moscow", "istanbul", "ankara", "riyadh", "jeddah", "doha", "tehran", "baghdad",
            "karachi", "lahore", "dhaka", "shanghai", "beijing", "guangzhou", "shenzhen",
            "bangkok", "jakarta", "manila", "seoul", "osaka", "kyoto", "casablanca",
            "tunis", "algiers", "kampala", "kigali", "luanda", "maputo", "lusaka",
            "bulawayo", "kinshasa", "addis ababa", "dar es salaam");

    private static final Pattern REMOTE = Pattern.compile(
            "\\bremote\\b|work from home|\\bwfh\\b|anywhere|home[\\s-]?based|\\bhybrid\\b|virtual",
            Pattern.CASE_INSENSITIVE);

    /** @return true if the location is (or is plausibly) South African. */
    public static boolean isSouthAfrican(String location) {
        if (location == null || location.isBlank()) return true; // no location given → SA-based company
        String norm = normalize(location);
        if (SA_ADMIN.matcher(norm).find()) return true;          // 1. SA province/country wins
        if (SA_ALIAS.matcher(norm).find()) return true;          //    informal SA name / abbreviation
        if (FOREIGN_COUNTRY.matcher(norm).find()) return false;  // 2. foreign country is decisive
        if (namesPlace(norm, FOREIGN_MAJOR, 4)) return false;    // 3. world-famous foreign city
        if (namesPlace(norm, PLACES, 5)) return true;            // 4. named SA place (13k gazetteer)
        if (FOREIGN_CITY.matcher(norm).find()) return false;     // 5. foreign city
        if (REMOTE.matcher(norm).find()) return true;            // 6. remote, no country
        return false;                                            // 7. unrecognised place → foreign
    }

    /** True if any comma/slash-separated part (or 1–3 word run of length ≥ minLen) is in {@code set}. */
    private static boolean namesPlace(String norm, Set<String> set, int minLen) {
        if (set.isEmpty()) return false;
        for (String part : norm.split("[,/|;()\\n·•]+")) {
            String p = part.trim();
            if (p.length() >= minLen && set.contains(p)) return true;
            String[] w = p.split("\\s+");
            for (int i = 0; i < w.length; i++) {
                StringBuilder sb = new StringBuilder();
                for (int n = 0; n < 3 && i + n < w.length; n++) {
                    if (n > 0) sb.append(' ');
                    sb.append(w[i + n]);
                    String cand = sb.toString();
                    if (cand.length() >= minLen && set.contains(cand)) return true;
                }
            }
        }
        return false;
    }

    /** Lower-case and strip accents so input matches the ASCII gazetteer. */
    private static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase();
    }

    /** Number of SA places loaded (for diagnostics/logging). */
    public static int gazetteerSize() {
        return PLACES.size();
    }
}
