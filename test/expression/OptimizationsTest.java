package expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import expression.ExpressionParser.*;
import expression.ExpressionSolver.Expression;

class OptimizationsTest {
	// It is more convenient to specify expressions as strings, so that is what we will use
	ExpressionSolver solve = new ExpressionSolver(new String[] {}, new double[] {});
	
	@Test
	void foldConstantsSimpleAdd() {
		Expression e = solve.parseString("x + (3 + 2)");
		// 3 + 2 should have been folded to 5
		Addition add = new Addition();
		add.setLhs(new Variable("x"));
		add.setRhs(new Constant(5));
		assertEquals(add, e);
	}
	
	@Test
	void foldConstantsTopAdd() {
		Expression e = solve.parseString("8 + 9");
		assertEquals(new Constant(17), e);
	}
	
	@Test
	void foldConstantsSimpleMinus() {
		Expression e = solve.parseString("3x + (8 - 3)");
		Addition add = new Addition();
		Multiplication mult = new Multiplication();
		mult.setLhs(new Constant(3));
		mult.setRhs(new Variable("x"));
		add.setLhs(mult);
		add.setRhs(new Constant(5));
		assertEquals(add, e);
	}
	
	@Test
	void minusNoOptimizations() {
		// Verify that trying for optimizations does not change the state of subtraction
		Expression e = solve.parseString("x - y");
		Subtraction sub = new Subtraction();
		Variable x = new Variable("x");
		sub.setLhs(x);
		Variable y = new Variable("y");
		sub.setRhs(y);
		assertEquals(sub, e);
		assertTrue(x.parent == sub);
		assertTrue(y.parent == sub);
	}
	
	@Test
	void minusNoOptimizationsComplex() {
		// Verify again with a little more complicated example that the state gets rolled
		// back properly.
		Expression e = solve.parseString("(a + x) - (z + (3 + y))");
		Subtraction base = new Subtraction();
		Addition left = new Addition();
		left.setLhs(new Variable("a"));
		left.setRhs(new Variable("x"));
		base.setLhs(left);
		Addition right = new Addition();
		right.setLhs(new Variable("z"));
		Addition add = new Addition();
		add.setLhs(new Constant(3));
		add.setRhs(new Variable("y"));
		right.setRhs(add);
		base.setRhs(right);
		assertEquals(base, e);
		assertTrue(left.parent == base);
		assertTrue(right.parent == base);
	}
	
	@Test
	void minusOptTransform() {
		// Verify again with a little more complicated example that the state gets rolled
		// back properly.
		Expression e = solve.parseString("(7 + x) - (z + (3 + y)");
		Addition base = new Addition();
		base.setLhs(new Variable("x"));
		Negation right = new Negation();
		Addition zAdd = new Addition();
		zAdd.setLhs(new Variable("z"));
		Addition add = new Addition();
		add.setLhs(new Constant(-4));
		add.setRhs(new Variable("y"));
		zAdd.setRhs(add);
		right.setRhs(zAdd);
		base.setRhs(right);
		assertEquals(base, e);
	}
	
	@Test
	void minusRightTransform() {
		Expression e = solve.parseString("x - (-y)");
		// Since there is an "optimization" (collapsing the negation), the add form
		// is treated as more optimized, and the change is kept.
		// We test this to verify that the state is correct, and does not get corrupted
		// by other checks.
		Addition add = new Addition();
		add.setLhs(new Variable("x"));
		add.setRhs(new Variable("y"));
		assertEquals(add, e);
	}
	
	@Test
	void foldMultilevelAdd() {
		Expression e = solve.parseString("3 + (x + 2)");
		Addition add = new Addition();
		add.setLhs(new Variable("x"));
		add.setRhs(new Constant(5));
		assertEquals(add, e);
	}
	
	@Test
	void foldMultilevelNegatedAdd() {
		Expression e = solve.parseString("(x - (z + 3)) + (4 + y)");
		Addition base = new Addition();
		Subtraction sub = new Subtraction();
		sub.setLhs(new Variable("x"));
		sub.setRhs(new Variable("z"));
		base.setLhs(sub);
		Addition right = new Addition();
		right.setLhs(new Constant(1));
		right.setRhs(new Variable("y"));
		base.setRhs(right);
		assertEquals(base, e);
	}
	
	@Test
	void foldMultilevelDoubleNegationAdd() {
		Expression e = solve.parseString("-(x - 3) + (y + 6)");
		Addition base = new Addition();
		Negation neg = new Negation();
		neg.setRhs(new Variable("x"));
		base.setLhs(neg);
		Addition right = new Addition();
		right.setLhs(new Variable("y"));
		right.setRhs(new Constant(9));
		base.setRhs(right);
		assertEquals(base, e);
	}
	
	@Test
	void foldMultilevelBothNegated() {
		Expression e = solve.parseString("-(3 + x) + (y - (z + 6)");
		Addition base = new Addition();
		Negation neg = new Negation();
		neg.setRhs(new Variable("x"));
		base.setLhs(neg);
		Subtraction sub = new Subtraction();
		sub.setLhs(new Variable("y"));
		Addition right = new Addition();
		right.setLhs(new Variable("z"));
		right.setRhs(new Constant(9));
		sub.setRhs(right);
		base.setRhs(sub);
		assertEquals(base, e);
	}
	
	@Test
	void redundantAddRight() {
		Expression e = solve.parseString("x + 0");
		Variable x = new Variable("x");
		assertEquals(x, e);
	}
	
	@Test
	void redundantAddLeft() {
		Expression e = solve.parseString("0 + ln y");
		NatLog ln = new NatLog();
		ln.setRhs(new Variable("y"));
		assertEquals(ln, e);
	}
	
	@Test
	void redundantSubRight() {
		Expression e = solve.parseString("3x - 0");
		Multiplication mult = new Multiplication();
		mult.setLhs(new Constant(3));
		mult.setRhs(new Variable("x"));
		assertEquals(mult, e);
	}
	
	@Test
	void redundantMultLeft() {
		Expression e = solve.parseString("1 * (2 log x)");
		Logarithm log = new Logarithm();
		log.setLhs(new Constant(2));
		log.setRhs(new Variable("x"));
		assertEquals(log, e);
	}
	
	@Test
	void redundantMultRight() {
		Expression e = solve.parseString("(x max y) * 1");
		Max max = new Max();
		max.setLhs(new Variable("x"));
		max.setRhs(new Variable("y"));
		assertEquals(max, e);
	}
	
	@Test
	void redundantDivideRight() {
		Expression e = solve.parseString("x / 1");
		Variable x = new Variable("x");
		assertEquals(x, e);
	}
	
	@Test
	void cancelMultiplicationRight() {
		Expression e = solve.parseString("(cos x + sin y) * 0");
		assertEquals(new Constant(0), e);
	}
	
	@Test
	void cancelMultiplicationLeft() {
		Expression e = solve.parseString("0 * sin(cos x)");
		assertEquals(new Constant(0), e);
	}
	
	@Test
	void cancelDivisionLeft() {
		Expression e = solve.parseString("0 / (2 ^ x)");
		assertEquals(new Constant(0), e);
	}
	
	@Test
	void cancelExponentiation() {
		Expression e = solve.parseString("(x + 2log y ^ z) ^ 0");
		assertEquals(new Constant(1), e);
	}
	
	@Test
	void foldConstantsTopDivide() {
		// Some complications may arise from having the constant at the top level, since
		// there is no parent to reference
		Expression e = solve.parseString("4 / 2");
		assertEquals(new Constant(2), e);
	}
	
	@Test
	void foldGeneratedConstants() {
		Expression e = solve.parseString("3 + cos 0");
		// Folds twice, once from cos 0 -> 1
		// Then from 3 + 1 -> 4
		assertEquals(new Constant(4), e);
	}
	
	@Test
	void applyNegation() {
		Expression e = solve.parseString("-7");
		assertEquals(new Constant(-7), e);
	}
	
	@Test
	void collapseNegations() {
		Expression e = solve.parseString("--x");
		assertEquals(new Variable("x"), e);
	}
	
	@Test
	void applyNegationNestedConstant() {
		Expression act = solve.parseString("-(x * ((y / -3) * z))");
		Multiplication exp = new Multiplication();
		exp.setLhs(new Variable("x"));
		Multiplication right = new Multiplication();
		right.setRhs(new Variable("z"));
		Division div = new Division();
		div.setLhs(new Variable("y"));
		div.setRhs(new Constant(3)); // not negative 3
		right.setLhs(div);
		exp.setRhs(right);
		assertEquals(exp, act);
	}
	
	@Test
	void logExponentiation() {
		Expression e = solve.parseString("(y + 1) log ((y+1)^x)");
		assertEquals(new Variable("x"), e);
	}
	
	@Test
	void logRoot() {
		Expression e = solve.parseString("z log (y r z)");
		//z log (y r z) = z log(z ^ (1/y)) = 1/y
		Division div = new Division();
		div.setLhs(new Constant(1));
		div.setRhs(new Variable("y"));
		assertEquals(div, e);
	}
	
	@Test
	void natLogExponentiation() {
		double e = 2.7182818284590452353602874713527;
		Expression exp = solve.parseString("ln (" + e + "^x)");
		assertEquals(new Variable("x"), exp);
	}
	
	@Test
	void combineExponentiations() {
		Expression act = solve.parseString("(x^y)^z");
		// (x^y)^z = x^(yz)
		Exponentiation base = new Exponentiation();
		base.setLhs(new Variable("x"));
		Multiplication exp = new Multiplication();
		exp.setLhs(new Variable("y"));
		exp.setRhs(new Variable("z"));
		base.setRhs(exp);
		assertEquals(base, act);
	}
	
	@Test
	void combineExponentiationAndRoot() {
		Expression act = solve.parseString("(z r y) ^ x");
		// (z r y) ^ x = (y ^ 1/z) ^ x = y ^(x/z)
		Exponentiation exp = new Exponentiation();
		exp.setLhs(new Variable("y"));
		Division div = new Division();
		div.setLhs(new Variable("x"));
		div.setRhs(new Variable("z"));
		exp.setRhs(div);
		assertEquals(exp, act);
	}
	
	@Test
	void combineRootAndExponentiation() {
		Expression act = solve.parseString("z r (x^y)");
		// z r (x^y) = (x^y)^(1/z) = x^(y/z)
		Exponentiation exp = new Exponentiation();
		exp.setLhs(new Variable("x"));
		Division div = new Division();
		div.setLhs(new Variable("y"));
		div.setRhs(new Variable("z"));
		exp.setRhs(div);
		assertEquals(exp, act);
	}
	
	@Test
	void combineRoots() {
		Expression act = solve.parseString("z r (y r x)");
		// z r (y r x) = (y r x) ^ 1/z = (x^(1/y))^(1/z) = x^(1/y * 1/z) = x^(1/(zy))
		Exponentiation exp = new Exponentiation();
		exp.setLhs(new Variable("x"));
		Division div = new Division();
		div.setLhs(new Constant(1));
		Multiplication mult = new Multiplication();
		mult.setLhs(new Variable("z"));
		mult.setRhs(new Variable("y"));
		div.setRhs(mult);
		exp.setRhs(div);
		assertEquals(exp, act);
	}
	
	@Test
	void combineInverseAndMultiplication() {
		Expression act = solve.parseString("x * (1 / y)");
		// -> (x * 1)/y -> x/y
		Division div = new Division();
		div.setLhs(new Variable("x"));
		div.setRhs(new Variable("y"));
		assertEquals(div, act);
	}
	
	@Test
	void combineMultipliedDenominators() {
		Expression act = solve.parseString("(2 / x) * (3 / y)");
		// -> (2 * 3)/(x * y) -> 6/xy
		Division div = new Division();
		div.setLhs(new Constant(6));
		Multiplication denom = new Multiplication();
		denom.setLhs(new Variable("x"));
		denom.setRhs(new Variable("y"));
		div.setRhs(denom);
		assertEquals(div, act);
	}
	
	@Test
	void sumLogsSimple() {
		// ylogx + ylogy + lnx + lny = 5log(x*y) + ln(x*y)
		fail("Not implemented yet!");
	}
	
	@Test
	void sumLogsShuffled() {
		// lnx + ylogy + ylogx + lny = ln(x*y) + 5log(x*y)
		fail("Not implemented yet!");
	}
	
	@Test
	void subtractLogs() {
		// ylogx - ylogz + lnx - lny = 
		fail("Not implemented yet!");
	}
	
	@Test
	void multiplyExponentiations() {
		fail("Not implemented yet!");
	}
	
	@Test
	void divideExponentiations() {
		fail("Not implemented yet!");
	}

}
