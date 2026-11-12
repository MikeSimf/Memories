package ru.dndmemory.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Безопасный разбор условий карточек: {@code type = 'ARTEFACT' and magic = true}.
 *
 * <p>Поддержаны сравнения {@code =} и {@code !=}, {@code and}, {@code or},
 * {@code not}, скобки, строки в кавычках, числа, true/false/null. Любая ошибка
 * разбора считается ложью — формула не может уронить приложение.</p>
 */
public final class CardFormula {

    private CardFormula() {
    }

    public static boolean matches(String formula, Map<String, Object> fields) {
        try {
            Parser parser = new Parser(tokens(formula), fields);
            boolean result = parser.expression();
            return parser.atEnd() && result;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private enum Kind { IDENT, STRING, NUMBER, TRUE, FALSE, NULL, EQ, NEQ, AND, OR, NOT, LP, RP, END }

    private static final class Token {
        final Kind kind;
        final String text;

        Token(Kind kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }

    private static List<Token> tokens(String source) {
        List<Token> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '(') { out.add(new Token(Kind.LP, "(")); i++; continue; }
            if (c == ')') { out.add(new Token(Kind.RP, ")")); i++; continue; }
            if (c == '=') { out.add(new Token(Kind.EQ, "=")); i++; continue; }
            if (c == '!' && i + 1 < source.length() && source.charAt(i + 1) == '=') {
                out.add(new Token(Kind.NEQ, "!=")); i += 2; continue;
            }
            if (c == '\'' || c == '"') {
                char quote = c;
                StringBuilder value = new StringBuilder();
                i++;
                while (i < source.length() && source.charAt(i) != quote) {
                    value.append(source.charAt(i++));
                }
                if (i >= source.length()) throw new IllegalArgumentException("unterminated string");
                i++;
                out.add(new Token(Kind.STRING, value.toString()));
                continue;
            }
            if (Character.isDigit(c) || (c == '-' && i + 1 < source.length()
                    && Character.isDigit(source.charAt(i + 1)))) {
                int start = i++;
                while (i < source.length() && (Character.isDigit(source.charAt(i))
                        || source.charAt(i) == '.')) i++;
                out.add(new Token(Kind.NUMBER, source.substring(start, i)));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i++;
                while (i < source.length() && (Character.isLetterOrDigit(source.charAt(i))
                        || source.charAt(i) == '_')) i++;
                String word = source.substring(start, i);
                String lower = word.toLowerCase(Locale.ROOT);
                Kind kind;
                if ("and".equals(lower)) kind = Kind.AND;
                else if ("or".equals(lower)) kind = Kind.OR;
                else if ("not".equals(lower)) kind = Kind.NOT;
                else if ("true".equals(lower)) kind = Kind.TRUE;
                else if ("false".equals(lower)) kind = Kind.FALSE;
                else if ("null".equals(lower)) kind = Kind.NULL;
                else kind = Kind.IDENT;
                out.add(new Token(kind, word));
                continue;
            }
            throw new IllegalArgumentException("unexpected char");
        }
        out.add(new Token(Kind.END, ""));
        return out;
    }

    private static final class Parser {
        private final List<Token> tokens;
        private final Map<String, Object> fields;
        private int index;

        Parser(List<Token> tokens, Map<String, Object> fields) {
            this.tokens = tokens;
            this.fields = fields;
        }

        boolean atEnd() {
            return peek().kind == Kind.END;
        }

        private Token peek() {
            return tokens.get(index);
        }

        private Token next() {
            return tokens.get(index++);
        }

        boolean expression() {
            return or();
        }

        private boolean or() {
            boolean value = and();
            while (peek().kind == Kind.OR) {
                next();
                boolean right = and();
                value = value || right;
            }
            return value;
        }

        private boolean and() {
            boolean value = unary();
            while (peek().kind == Kind.AND) {
                next();
                boolean right = unary();
                value = value && right;
            }
            return value;
        }

        private boolean unary() {
            if (peek().kind == Kind.NOT) {
                next();
                return !unary();
            }
            return comparison();
        }

        private boolean comparison() {
            if (peek().kind == Kind.LP) {
                next();
                boolean value = expression();
                if (next().kind != Kind.RP) throw new IllegalArgumentException("expected )");
                return value;
            }
            Object left = value();
            Token op = peek();
            if (op.kind != Kind.EQ && op.kind != Kind.NEQ) {
                return truthy(left);
            }
            next();
            Object right = value();
            boolean equal = equal(left, right);
            return op.kind == Kind.EQ ? equal : !equal;
        }

        private Object value() {
            Token token = next();
            switch (token.kind) {
                case IDENT:
                    return fields.get(token.text);
                case STRING:
                    return token.text;
                case NUMBER:
                    return Double.parseDouble(token.text);
                case TRUE:
                    return Boolean.TRUE;
                case FALSE:
                    return Boolean.FALSE;
                case NULL:
                    return null;
                default:
                    throw new IllegalArgumentException("expected value");
            }
        }

        private boolean equal(Object left, Object right) {
            if (left == null || right == null) return left == right;
            if (left instanceof Number || right instanceof Number) {
                return Double.compare(asDouble(left), asDouble(right)) == 0;
            }
            return String.valueOf(left).equalsIgnoreCase(String.valueOf(right));
        }

        private double asDouble(Object value) {
            if (value instanceof Number) return ((Number) value).doubleValue();
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }

        private boolean truthy(Object value) {
            if (value == null) return false;
            if (value instanceof Boolean) return (Boolean) value;
            if (value instanceof Number) return ((Number) value).doubleValue() != 0;
            return !String.valueOf(value).isEmpty();
        }
    }
}
