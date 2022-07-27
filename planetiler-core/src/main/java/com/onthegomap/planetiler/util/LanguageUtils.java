package com.onthegomap.planetiler.util;

import static com.onthegomap.planetiler.util.Utils.nullIfEmpty;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class LanguageUtils {
  // Name tags that should be eligible for finding a latin name.
  // See https://wiki.openstreetmap.org/wiki/Multilingual_names
  private static final Predicate<String> VALID_NAME_TAGS =
    Pattern
      .compile("^name:[a-z]{2,3}(-[a-z]{4})?([-_](x-)?[a-z]{2,})?(-([a-z]{2}|[0-9]{3}))?$", Pattern.CASE_INSENSITIVE)
      .asMatchPredicate();
  // See https://github.com/onthegomap/planetiler/issues/86
  // Match strings that only contain latin characters.
  private static final Predicate<String> ONLY_LATIN = Pattern
    .compile("^[\\P{IsLetter}[\\p{IsLetter}&&\\p{IsLatin}]]+$")
    .asMatchPredicate();
  // Match only latin letters
  private static final Pattern LATIN_LETTER = Pattern.compile("[\\p{IsLetter}&&\\p{IsLatin}]+");
  private static final Pattern EMPTY_PARENS = Pattern.compile("(\\([ -.]*\\)|\\[[ -.]*])");
  private static final Pattern LEADING_TRAILING_JUNK = Pattern.compile("((^[\\s./-]*)|([\\s./-]*$))");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Set<String> EN_DE_NAME_KEYS = Set.of("name:en", "name:de");

  private LanguageUtils() {
    throw new IllegalStateException("Utility class");
  }

  public static void putIfNotEmpty(Map<String, Object> dest, String key, Object value) {
    if (value != null && !value.equals("")) {
      dest.put(key, value);
    }
  }

  public static String string(Object obj) {
    return nullIfEmpty(obj == null ? null : obj.toString());
  }

  public static boolean containsOnlyLatinCharacters(String string) {
    return string != null && ONLY_LATIN.test(string);
  }

  public static String transliteratedName(Map<String, Object> tags) {
    return Translations.transliterate(string(tags.get("name")));
  }

  public static String removeLatinCharacters(String name) {
    if (name == null) {
      return null;
    }
    var matcher = LATIN_LETTER.matcher(name);
    if (matcher.find()) {
      String result = matcher.replaceAll("");
      // if the name was "<nonlatin text> (<latin description)"
      // or "<nonlatin text> - <latin description>"
      // then remove any of those extra characters now
      result = EMPTY_PARENS.matcher(result).replaceAll("");
      result = LEADING_TRAILING_JUNK.matcher(result).replaceAll("");
      result = WHITESPACE.matcher(result).replaceAll(" ").trim();
      return result.isBlank() ? null : result;
    }
    return name.trim();
  }

  public static boolean isValidOsmNameTag(String tag) {
    return VALID_NAME_TAGS.test(tag);
  }

  public static String getLatinName(Map<String, Object> tags, boolean transliterate) {
    String name = string(tags.get("name"));
    if (containsOnlyLatinCharacters(name)) {
      return name;
    } else {
      return getNameTranslations(tags)
        .filter(LanguageUtils::containsOnlyLatinCharacters)
        .findFirst()
        .orElse(transliterate ? Translations.transliterate(name) : null);
    }
  }


  private static Stream<String> getNameTranslations(Map<String, Object> tags) {
    return Stream.concat(
      Stream.of("name:en", "int_name", "name:de").map(tag -> string(tags.get(tag))),
      tags.entrySet().stream()
        .filter(e -> !EN_DE_NAME_KEYS.contains(e.getKey()) && VALID_NAME_TAGS.test(e.getKey()))
        .map(Map.Entry::getValue)
        .map(LanguageUtils::string)
    );
  }
}
