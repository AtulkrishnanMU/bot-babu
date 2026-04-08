package com.example.discordbot.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class JsonUtil {

    public SimpleJsonObject parse(String json) {
        SimpleJsonParser parser = new SimpleJsonParser();
        return parser.parse(json);
    }

    private static class SimpleJsonParser {
        private int pos;
        private String json;

        public SimpleJsonObject parse(String json) {
            this.json = json.trim();
            this.pos = 0;
            return parseObject();
        }

        private SimpleJsonObject parseObject() {
            SimpleJsonObject obj = new SimpleJsonObject();
            skipWhitespace();
            expect('{');
            skipWhitespace();

            while (peek() != '}') {
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();

                Object value = parseValue();
                obj.put(key, value);

                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                    skipWhitespace();
                }
            }

            expect('}');
            return obj;
        }

        private Object parseValue() {
            skipWhitespace();
            char c = peek();

            if (c == '"') {
                return parseString();
            } else if (c == '{') {
                return parseObject();
            } else if (c == '[') {
                return parseArray();
            } else if (c == 't' || c == 'f') {
                return parseBoolean();
            } else if (c == 'n') {
                return parseNull();
            } else {
                return parseNumber();
            }
        }

        private SimpleJsonArray parseArray() {
            SimpleJsonArray arr = new SimpleJsonArray();
            expect('[');
            skipWhitespace();

            while (peek() != ']') {
                arr.add(parseValue());
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                    skipWhitespace();
                }
            }

            expect(']');
            return arr;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (peek() != '"') {
                if (peek() == '\\') {
                    pos++;
                    char escapeChar = json.charAt(pos++);
                    switch (escapeChar) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        default: sb.append(escapeChar); break;
                    }
                } else {
                    sb.append(json.charAt(pos++));
                }
            }
            expect('"');
            return sb.toString();
        }

        private Boolean parseBoolean() {
            if (json.startsWith("true", pos)) {
                pos += 4;
                return true;
            } else if (json.startsWith("false", pos)) {
                pos += 5;
                return false;
            }
            throw new RuntimeException("Expected boolean");
        }

        private Object parseNull() {
            expect("null");
            return null;
        }

        private Number parseNumber() {
            int start = pos;
            while (pos < json.length() && (Character.isDigit(json.charAt(pos)) || json.charAt(pos) == '-' || json.charAt(pos) == '.')) {
                pos++;
            }
            String num = json.substring(start, pos);
            if (num.contains(".")) {
                return Double.parseDouble(num);
            }
            return Integer.parseInt(num);
        }

        private void expect(String s) {
            if (!json.substring(pos).startsWith(s)) {
                throw new RuntimeException("Expected '" + s + "'");
            }
            pos += s.length();
        }

        private void expect(char c) {
            if (json.charAt(pos) != c) {
                throw new RuntimeException("Expected '" + c + "'");
            }
            pos++;
        }

        private char peek() {
            if (pos >= json.length()) return '\0';
            return json.charAt(pos);
        }

        private void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                pos++;
            }
        }
    }

    public static class SimpleJsonObject {
        private final Map<String, Object> data = new HashMap<>();

        public void put(String key, Object value) {
            data.put(key, value);
        }

        public boolean has(String key) {
            return data.containsKey(key) && data.get(key) != null;
        }

        public String getString(String key) {
            Object val = data.get(key);
            return val != null ? val.toString() : null;
        }

        public int getInt(String key) {
            return ((Number) data.get(key)).intValue();
        }

        public boolean getBoolean(String key) {
            return (Boolean) data.get(key);
        }

        public SimpleJsonObject getObject(String key) {
            return (SimpleJsonObject) data.get(key);
        }

        @SuppressWarnings("unchecked")
        public List<SimpleJsonObject> getArray(String key) {
            SimpleJsonArray arr = (SimpleJsonArray) data.get(key);
            return arr != null ? (List<SimpleJsonObject>) (List<?>) arr.data : new ArrayList<>();
        }
    }

    private static class SimpleJsonArray {
        private final List<Object> data = new ArrayList<>();

        public void add(Object obj) {
            data.add(obj);
        }
    }
}
