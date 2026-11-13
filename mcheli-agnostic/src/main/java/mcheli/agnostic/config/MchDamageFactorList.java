package mcheli.agnostic.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One of the reference's global damage-factor lists ({@code MCH_Config.DamageFactor}: {@code DamageVsEntity},
 * {@code DamageVsPlayer}, {@code DamageMCHeliAircraftByExternal}, ...), parsed from its config lines.
 *
 * <p>Each line is {@code "factor"} or {@code "factor, classNameFragment"} (reference {@code newDamageEntity},
 * MCH_Config:586-593). {@link #apply} multiplies the damage by EVERY entry that matches, in order: an entry with no
 * fragment matches everything, an entry with a fragment matches when one of the given class names contains it
 * (reference {@code applyDamageVsEntity}/{@code applyDamageByExternal}, MCH_Config:595-651). Which list applies to
 * which target is the dependent layer's call, since it needs the entity classes.
 *
 * <p>Two deliberate departures: a line whose factor does not parse is skipped (the reference's
 * {@code Double.parseDouble} threw out of config loading and stopped the game), and the fragment is matched against
 * the plain binary class name rather than {@code Class.toString()}'s {@code "class "}-prefixed form with
 * {@code indexOf(...) > 0}. The two agree for every fragment that is part of a class name.
 */
public final class MchDamageFactorList {

    /** One parsed line: multiply by {@code factor} when {@code name} is empty or contained in the class name. */
    public record Entry(double factor, String name) {}

    public static final MchDamageFactorList EMPTY = new MchDamageFactorList(Collections.emptyList());

    private final List<Entry> entries;

    private MchDamageFactorList(List<Entry> entries) {
        this.entries = entries;
    }

    /** Parse config lines; blank or malformed lines are skipped (see {@link #isValidLine}). */
    public static MchDamageFactorList parse(List<? extends String> lines) {
        if (lines == null || lines.isEmpty()) {
            return EMPTY;
        }
        List<Entry> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            Entry e = parseLine(line);
            if (e != null) {
                out.add(e);
            }
        }
        return out.isEmpty() ? EMPTY : new MchDamageFactorList(Collections.unmodifiableList(out));
    }

    /** True if {@code o} is a line {@link #parse} accepts: a String whose factor parses. For a config validator. */
    public static boolean isValidLine(Object o) {
        return o instanceof String s && parseLine(s) != null;
    }

    /**
     * Port of {@code newDamageEntity}: one field is a bare factor, two are factor + fragment, and any other field
     * count is the neutral {@code (1.0, "")} the reference fell back to. Null if the factor is not a number.
     */
    static Entry parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] s = line.trim().split("\\s*,\\s*");
        if (s.length != 1 && s.length != 2) {
            return new Entry(1.0, "");
        }
        double factor;
        try {
            factor = Double.parseDouble(s[0]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (Double.isNaN(factor) || Double.isInfinite(factor)) {
            return null;
        }
        return new Entry(factor, s.length == 2 ? s[1].trim() : "");
    }

    public List<Entry> entries() {
        return this.entries;
    }

    /**
     * Apply every matching entry to {@code damage}. {@code classNames} are the candidate entity class names (the
     * target for a "DamageVs" list; the attacker and the direct damage source for a "ByExternal" list); null
     * elements are ignored. Matching is case-sensitive, like the reference.
     */
    public float apply(float damage, String... classNames) {
        for (Entry e : this.entries) {
            if (e.name().isEmpty() || anyContains(classNames, e.name())) {
                damage = (float) (damage * e.factor());
            }
        }
        return damage;
    }

    private static boolean anyContains(String[] names, String fragment) {
        if (names == null) {
            return false;
        }
        for (String n : names) {
            if (n != null && n.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reference {@code IgnoreBulletHit} match (MCH_EntityBaseBullet:735-739): true when the lower-cased class name
     * contains any lower-cased fragment. Lives here so the rule is unit-tested beside its sibling lists.
     */
    public static boolean classNameMatchesAny(String className, List<? extends String> fragments) {
        if (className == null || fragments == null) {
            return false;
        }
        String lower = className.toLowerCase(Locale.ROOT);
        for (String f : fragments) {
            if (f != null && !f.isBlank() && lower.contains(f.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
