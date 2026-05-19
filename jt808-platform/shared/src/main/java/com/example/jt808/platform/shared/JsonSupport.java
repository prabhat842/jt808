package com.example.jt808.platform.shared;

public final class JsonSupport {
    private JsonSupport() {
    }

    public static String field(String name, String value) {
        return "\"" + escape(name) + "\":\"" + escape(value == null ? "" : value) + "\"";
    }

    public static String numberField(String name, Number value) {
        return "\"" + escape(name) + "\":" + (value == null ? "0" : value);
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }
}
