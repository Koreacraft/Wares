package io.github.mortuusars.wares.util;

import io.github.mortuusars.wares.Wares;
import io.github.mortuusars.wares.data.LangKeys;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TranslatableComponent;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class TextUtil {
    /**
     * Creates TranslatableComponent from a given key prefixed with the MOD ID.
     */
    public static MutableComponent translate(String key, Object... args) {
        return new TranslatableComponent(Wares.ID + "." + key, args);
    }

    public static MutableComponent timeFromTicks(long ticks) {
        long minutes = ticks / 20 / 60;

        if (minutes >= 60) {
            int hours = (int)(minutes / 60);
            return hours >= 24 ?
                    Wares.translate(LangKeys.GUI_TIME_DAYS, hours / 24)
                    : Wares.translate(LangKeys.GUI_TIME_HOURS, hours);
        }
        else {
            return Wares.translate(LangKeys.GUI_TIME_MINUTES, minutes < 1 ? "<1" : minutes);
        }
    }


    private static final NavigableMap<Long, String> suffixes = new TreeMap<>();
    static {
        suffixes.put(1_000L, "k");
        suffixes.put(1_000_000L, "M");
        suffixes.put(1_000_000_000L, "G");
        suffixes.put(1_000_000_000_000L, "T");
        suffixes.put(1_000_000_000_000_000L, "P");
        suffixes.put(1_000_000_000_000_000_000L, "E");
    }

    /**
     * Formats big numbers (>999) to a number with a suffix. eg: 1.2K, 2.6M.
     * Shamelessly stolen from StackOverflow.
     */
    public static String shortenNumber(long value) {
        //Long.MIN_VALUE == -Long.MIN_VALUE, so we need an adjustment here
        if (value == Long.MIN_VALUE) return shortenNumber(Long.MIN_VALUE + 1);
        if (value < 0) return "-" + shortenNumber(-value);
        if (value < 1000) return Long.toString(value); //deal with easy case

        Map.Entry<Long, String> e = suffixes.floorEntry(value);
        Long divideBy = e.getKey();
        String suffix = e.getValue();

        long truncated = value / (divideBy / 10); //the number part of the output times 10
        boolean hasDecimal = truncated < 100 && (truncated / 10d) != (truncated / 10);
        return hasDecimal ? (truncated / 10d) + suffix : (truncated / 10) + suffix;
    }
}