package io.github.leontecristian.workflow.testhost.service.console;

/**
 * UTF-16 validity at host-owned text boundaries; core validates its own contracts independently.
 */
public final class ConsoleText {
    private ConsoleText() {}

    public static boolean unicode(String value) {
        if (value == null) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index)))
                    return false;
            } else if (Character.isLowSurrogate(character)) return false;
        }
        return true;
    }
}
