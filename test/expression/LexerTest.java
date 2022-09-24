package expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import expression.ExpressionLexer.Token;

class LexerTest {
	static ExpressionLexer lex = new ExpressionLexer();
	
	@Test
	void lexNormalCase() {
		List<Token> act = lex.lexExpression("3 * (4.81 + x) / cos rads");
		List<Token> exp = List.of(tok(Token.Type.NUMBER, "3"),
								  tok(Token.Type.MULTIPLY),
								  tok(Token.Type.OPEN_PAREN),
								  tok(Token.Type.NUMBER, "4.81"),
								  tok(Token.Type.PLUS),
								  tok(Token.Type.IDENTIFIER, "x"),
								  tok(Token.Type.CLOSE_PAREN),
								  tok(Token.Type.DIVIDE),
								  tok(Token.Type.COS),
								  tok(Token.Type.IDENTIFIER, "rads"));
		assertEquals(exp, act);
	}
	
	@Test
	void badNumber() {
		assertThrows(RuntimeException.class, () -> {lex.lexExpression("3.1415.926");});
	}
	
	@Test
	void lexDualVarName() {
		List<Token> act = lex.lexExpression("2x - x2 round 5.bob r foo3.8");
		List<Token> exp = List.of(tok(Token.Type.NUMBER, "2"),
								  tok(Token.Type.IDENTIFIER, "x"),
								  tok(Token.Type.MINUS),
								  tok(Token.Type.IDENTIFIER, "x2"),
								  tok(Token.Type.ROUND),
								  tok(Token.Type.NUMBER, "5."),
								  tok(Token.Type.IDENTIFIER, "bob"),
								  tok(Token.Type.ROOT),
								  tok(Token.Type.IDENTIFIER, "foo3.8"));
		assertEquals(exp, act);
	}
	
	@Test
	void lexVarWithOp() {
		List<Token> act = lex.lexExpression("4 * cost + basin / mine");
		List<Token> exp = List.of(tok(Token.Type.NUMBER, "4"),
				  				  tok(Token.Type.MULTIPLY),
				  				  tok(Token.Type.IDENTIFIER, "cost"),
				  				  tok(Token.Type.PLUS),
				  				  tok(Token.Type.IDENTIFIER, "basin"),
								  tok(Token.Type.DIVIDE),
								  tok(Token.Type.IDENTIFIER, "mine"));
		assertEquals(exp, act);
	}
	
	@Test
	void lexSpace() {
		List<Token> act = lex.lexExpression(" cos      8 + G\n");
		List<Token> exp = List.of(tok(Token.Type.COS),
				  				  tok(Token.Type.NUMBER, "8"),
				  				  tok(Token.Type.PLUS),
				  				  tok(Token.Type.IDENTIFIER, "G"));
		assertEquals(exp, act);
	}
	
	Token tok(Token.Type type) {
		return new Token(type, null);
	}
	Token tok(Token.Type type, String val) {
		return new Token(type, val);
	}

}
