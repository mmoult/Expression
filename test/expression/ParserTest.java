package expression;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import expression.ExpressionLexer.Token;
import expression.ExpressionSolver.Expression;
import expression.ExpressionParser.*;

class ParserTest {
	static ExpressionParser parse;
	
	@BeforeAll
	static void setup() {
		parse = new ExpressionParser();
		parse.optimize = false; // disable optimizations for these tests
	}
	
	@Test
	void parseNormalCase() { // 3 * (4.81 + x) / cos rads
		List<Token> inp = List.of(tok(Token.Type.NUMBER, "3"),
								  tok(Token.Type.MULTIPLY),
								  tok(Token.Type.OPEN_PAREN),
								  tok(Token.Type.NUMBER, "4.81"),
								  tok(Token.Type.PLUS),
								  tok(Token.Type.IDENTIFIER, "x"),
								  tok(Token.Type.CLOSE_PAREN),
								  tok(Token.Type.DIVIDE),
								  tok(Token.Type.COS),
								  tok(Token.Type.IDENTIFIER, "rads"));
		Expression act = parse.parse(inp);
		Addition add = new Addition();
		add.setLhs(new Constant(4.81));
		add.setRhs(new Variable("x"));
		Multiplication mult = new Multiplication();
		mult.setLhs(new Constant(3));
		mult.setRhs(add);
		Division exp = new Division();
		exp.setLhs(mult);
		Cosine cos = new Cosine();
		cos.setRhs(new Variable("rads"));
		exp.setRhs(cos);
		assertEquals(exp, act);
	}
	
	@Test
	void parseImplicitMult() { // -3x max 5(foo 45)
		List<Token> inp = List.of(tok(Token.Type.MINUS),
				  				  tok(Token.Type.NUMBER, "3"),
				  				  tok(Token.Type.IDENTIFIER, "x"),
				  				  tok(Token.Type.MAX),
				  				  tok(Token.Type.NUMBER, "5"),
				  				  tok(Token.Type.OPEN_PAREN),
				  				  tok(Token.Type.IDENTIFIER, "foo"),
				  				  tok(Token.Type.NUMBER, "45"),
				  				  tok(Token.Type.CLOSE_PAREN));
		Expression act = parse.parse(inp);
		Negation neg = new Negation();
		neg.setRhs(new Constant(3));
		Multiplication coef = new Multiplication();
		coef.setLhs(neg);
		coef.setRhs(new Variable("x"));
		Max exp = new Max();
		exp.setLhs(coef);
		Multiplication mult = new Multiplication();
		mult.setLhs(new Constant(5));
		Multiplication par = new Multiplication();
		par.setLhs(new Variable("foo"));
		par.setRhs(new Constant(45));
		mult.setRhs(par);
		exp.setRhs(mult);
		assertEquals(exp, act);
	}
	
	@Test
	void parsePrecedence() { // sin -10 r (2 + 4 * 3 + 1)
		List<Token> inp = List.of(tok(Token.Type.SIN),
				  				  tok(Token.Type.MINUS),
				  				  tok(Token.Type.NUMBER, "10"),
				  				  tok(Token.Type.ROOT),
				  				  tok(Token.Type.OPEN_PAREN),
				  				  tok(Token.Type.NUMBER, "2"),
				  				  tok(Token.Type.PLUS),
				  				  tok(Token.Type.NUMBER, "4"),
				  				  tok(Token.Type.MULTIPLY),
				  				  tok(Token.Type.NUMBER, "3"),
				  				  tok(Token.Type.PLUS),
				  				  tok(Token.Type.NUMBER, "1"),
				  				  tok(Token.Type.CLOSE_PAREN));
		Expression act = parse.parse(inp);
		Root exp = new Root();
		Negation neg = new Negation();
		neg.setRhs(new Constant(10));
		Sine sin = new Sine();
		sin.setRhs(neg);
		exp.setLhs(sin);
		Multiplication mult = new Multiplication();
		mult.setLhs(new Constant(4));
		mult.setRhs(new Constant(3));
		Addition left = new Addition();
		left.setLhs(new Constant(2));
		left.setRhs(mult);
		Addition right = new Addition();
		right.setLhs(left);
		right.setRhs(new Constant(1));
		exp.setRhs(right);
		assertEquals(exp, act);
	}
	
	@Test
	void complexParen() { // (7 + x) - (z * (3 + y))
		List<Token> inp = List.of(tok(Token.Type.OPEN_PAREN),
								  tok(Token.Type.NUMBER, "7"),
				  				  tok(Token.Type.PLUS),
				  				  tok(Token.Type.IDENTIFIER, "x"),
				  				  tok(Token.Type.CLOSE_PAREN),
				  				  tok(Token.Type.MINUS),
				  				  tok(Token.Type.OPEN_PAREN),
				  				  tok(Token.Type.IDENTIFIER, "z"),
				  				  tok(Token.Type.MULTIPLY),
				  				  tok(Token.Type.OPEN_PAREN),
				  				  tok(Token.Type.NUMBER, "3"),
				  				  tok(Token.Type.PLUS),
				  				  tok(Token.Type.IDENTIFIER, "y"),
				  				  tok(Token.Type.CLOSE_PAREN),
				  				  tok(Token.Type.CLOSE_PAREN));
		Expression act = parse.parse(inp);
		Subtraction exp = new Subtraction();
		Addition left = new Addition();
		left.setLhs(new Constant(7));
		left.setRhs(new Variable("x"));
		exp.setLhs(left);
		Multiplication right = new Multiplication();
		right.setLhs(new Variable("z"));
		Addition add = new Addition();
		add.setLhs(new Constant(3));
		add.setRhs(new Variable("y"));
		right.setRhs(add);
		exp.setRhs(right);
		assertEquals(exp, act);
	}
	
	@Test
	void leftAssociative() { // a - b + c - d
		List<Token> inp = List.of(tok(Token.Type.IDENTIFIER, "a"),
								  tok(Token.Type.MINUS),
								  tok(Token.Type.IDENTIFIER, "b"),
								  tok(Token.Type.PLUS),
								  tok(Token.Type.IDENTIFIER, "c"),
								  tok(Token.Type.MINUS),
								  tok(Token.Type.IDENTIFIER, "d"));
		// To get the desired result, this needs to be evaluated with left-evaluation.
		// Otherwise, we get a - (b + c - d), which is not equal to the expected
		// (a - b) + (c - d) or ((a - b) + c) - d
		Expression act = parse.parse(inp);
		Subtraction exp = new Subtraction();
		exp.setRhs(new Variable("d"));
		Addition add = new Addition();
		add.setRhs(new Variable("c"));
		Subtraction sub = new Subtraction();
		sub.setLhs(new Variable("a"));
		sub.setRhs(new Variable("b"));
		add.setLhs(sub);
		exp.setLhs(add);
		assertEquals(exp, act);
	}
	
	@Test
	void badImplicit() { // 3 4
		List<Token> inp = List.of(tok(Token.Type.NUMBER, "3"), tok(Token.Type.NUMBER, "4"));
		assertThrows(RuntimeException.class, () -> {parse.parse(inp);});
	}
	
	@Test
	void badArguments() { // baz + / T
		List<Token> inp = List.of(tok(Token.Type.IDENTIFIER, "baz"),
								  tok(Token.Type.PLUS),
								  tok(Token.Type.DIVIDE),
								  tok(Token.Type.IDENTIFIER, "T"));
		assertThrows(RuntimeException.class, () -> {parse.parse(inp);});
	}

	
	Token tok(Token.Type type) {
		return new Token(type, null);
	}
	Token tok(Token.Type type, String val) {
		return new Token(type, val);
	}

}
