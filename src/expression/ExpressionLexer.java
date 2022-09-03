package expression;

import java.util.ArrayList;
import java.util.List;

public class ExpressionLexer {
	
	/**
	 * Tokens that will be constructed from the parsed string expression.
	 */
	public static class Token {
		public static enum Type {
			IDENTIFIER,
			NUMBER,
			PLUS,
			MINUS,
			MULTIPLY,
			DIVIDE,
			OPEN_PAREN,
			CLOSE_PAREN,
			EXPONENT,
			ROOT,
			COS,
			SIN,
			TAN,
			LOG,
			LN,
			MAX,
			MIN,
			ROUND,
			CEIL,
			FLOOR,
			SPACE
		}
		public Type type;
		public String value = null; // only used for identifiers and numbers
		
		public Token(Type type, String value) {
			this.type = type;
			this.value = value;
		}
		
		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Token))
				return false;
			Token other = (Token)o;
			if (other == null || this.type != other.type)
				return false;
			return !(this.type == Type.NUMBER || this.type == Type.IDENTIFIER) ||
					this.type.equals(other.type);
		}
	}
	
	public List<Token> lexExpression(String expression) {
		ArrayList<Token> tokens = new ArrayList<>();
		StringBuilder value = new StringBuilder();
		
		Token.Type current = null; // numbers and identifiers may span multiple characters
		for (int i = 0; i <= expression.length(); i++) {
			Token.Type found = null;
			
			// On the final round, we cannot see any new chars
			char c = 0;
			if (i < expression.length()) {
				c = expression.charAt(i);
				if (c == '+')
					found = Token.Type.PLUS;
				else if (c == '-')
					found = Token.Type.MINUS;
				else if (c == '*')
					found = Token.Type.MULTIPLY;
				else if (c == '/')
					found = Token.Type.DIVIDE;
				else if (c == '^')
					found = Token.Type.EXPONENT;
				else if (c == '(')
					found = Token.Type.OPEN_PAREN;
				else if (c == ')')
					found = Token.Type.CLOSE_PAREN;
				else if (Character.isAlphabetic(c) || // identifiers begin with an alphabetic character
						(current == Token.Type.IDENTIFIER && // after they begin, rules are less strict
						(c == '.' || (c >= '0' && c <= '9') || c == '_')))
					found = Token.Type.IDENTIFIER;
				else if (c == '.' || (c >= '0' && c <= '9'))
					found = Token.Type.NUMBER;
				else if (Character.isWhitespace(c))
					found = Token.Type.SPACE;
				else
					throw new RuntimeException("Malformed expression! Unrecognized character: " + c);
			}else
				found = Token.Type.SPACE; // push anything held
			
			// We have a set type and the found is different
			if (current != null && current != found) {
				// This will trigger pushing what we have seen so far
				String push = value.toString();
				value = new StringBuilder();
				Token.Type pushType = current;
				
				if (current == Token.Type.NUMBER) {
					// verify that what we have is a valid number
					boolean isNumber = true;
					boolean seenDec = false;
					for (int j = 0; j < push.length() && isNumber; j++) {
						char d = push.charAt(j);
						if (d == '.') {
							if (seenDec)
								isNumber = false;
							seenDec = true;
						}
					}
					if (isNumber)
						pushType = Token.Type.NUMBER;
					else
						throw new RuntimeException("Malformed expression! \"" + push + "\" is not a valid number.");
				}else if (current == Token.Type.IDENTIFIER) {
					// check against keywords
					if (push.equals("r"))
						pushType = Token.Type.ROOT;
					else if (push.equals("cos"))
						pushType = Token.Type.COS;
					else if (push.equals("sin"))
						pushType = Token.Type.SIN;
					else if (push.equals("tan"))
						pushType = Token.Type.TAN;
					else if (push.equals("log"))
						pushType = Token.Type.LOG;
					else if (push.equals("ln"))
						pushType = Token.Type.LN;
					else if (push.equals("max"))
						pushType = Token.Type.MAX;
					else if (push.equals("min"))
						pushType = Token.Type.MIN;
					else if (push.equals("round"))
						pushType = Token.Type.ROUND;
					else if (push.equals("ceil"))
						pushType = Token.Type.CEIL;
					else if (push.equals("floor"))
						pushType = Token.Type.FLOOR;
				}
				// If we made it here, we push out the value with the type
				tokens.add(new Token(pushType, push));
				current = null;
			}
			
			switch(found) {
			case NUMBER:
			case IDENTIFIER:
				current = found;
				value.append(c);
				break;
			case SPACE:
				continue; // don't handle whitespace
			default:
				// push out what we found just now
				tokens.add(new Token(found, null));
			}
		}
		
		return tokens;
	}

}
