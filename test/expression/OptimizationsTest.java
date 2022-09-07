package expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import expression.ExpressionParser.Addition;
import expression.ExpressionParser.Constant;
import expression.ExpressionParser.Multiplication;
import expression.ExpressionParser.Negation;
import expression.ExpressionParser.Subtraction;
import expression.ExpressionParser.Variable;
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
		Expression e = solve.parseString("x - (z * (3 + y)");
		Subtraction base = new Subtraction();
		Variable x = new Variable("x");
		base.setLhs(x);
		Multiplication mult = new Multiplication();
		mult.setLhs(new Variable("z"));
		Addition add = new Addition();
		add.setLhs(new Constant(3));
		add.setRhs(new Variable("y"));
		mult.setRhs(add);
		base.setRhs(mult);
		assertEquals(base, e);
		assertTrue(x.parent == base);
		assertTrue(mult.parent == base);
	}
	
	@Test
	void minusToCorrectAdd() {
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

}
