package expression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import expression.ExpressionLexer.Token;
import expression.ExpressionSolver.Expression;
import expression.ExpressionSolver.UndefinedVarException;

public class ExpressionParser {
	public boolean optimize = true;
	
	/**
	 * Parses the given tokens into a Valuable. The result will likely be a tree
	 * of operations, since many Valuables have one or more operands.
	 * <p>
	 * Parsing will fold any constant operations encountered.
	 * @param tokens the tokens to parse
	 * @return the Valuable result
	 */
	public Expression parse(List<Token> tokens) {
		TokenIterator it = new TokenIterator(tokens);
		return parse(it);
	}
	
	protected static class TokenIterator {
		public List<Token> tokens;
		int index = 0;
		
		public TokenIterator(List<Token> tokens) {
			this.tokens = tokens;
		}
	}
	
	/**
	 * Recursive parsing method
	 * @param it the iterator to keep track of our index in the list
	 * @return the parsed expression
	 */
	protected Valuable parse(TokenIterator it) {
		ArrayList<Valuable> segments = new ArrayList<>();
		
		// Go through the token list and create stub operations.
		// Also, insert implicit multiplications where necessary.
		Token.Type prevLiteral = null;
		tokIt: for (; it.index < it.tokens.size(); it.index++) {
			Token.Type currLiteral = null;
			Token token = it.tokens.get(it.index);
			switch(token.type) {
			case CEIL:
				segments.add(new Ceiling());
				break;
			case CLOSE_PAREN:
				break tokIt; // end of this scope
			case COS:
				segments.add(new Cosine());
				break;
			case DIVIDE:
				segments.add(new Division());
				break;
			case EXPONENT:
				segments.add(new Exponentiation());
				break;
			case FLOOR:
				segments.add(new Floor());
				break;
			case IDENTIFIER:
				if (prevLiteral != null)
					segments.add(new Multiplication());
				segments.add(new Variable(token.value));
				currLiteral = token.type;
				break;
			case LN:
				segments.add(new NatLog());
				break;
			case LOG:
				segments.add(new Logarithm());
				break;
			case MAX:
				segments.add(new Max());
				break;
			case MIN:
				segments.add(new Min());
				break;
			case MINUS:
				// May be negation, may be subtraction.
				// We can find out by looking at the previous segment:
				// If there is none, or it is not a constant or variable,
				// then this is negation
				boolean negation = segments.isEmpty();
				if (!negation) {
					Valuable seg = segments.get(segments.size() - 1);
					negation = !(seg instanceof Constant || seg instanceof Variable);
				}
				segments.add(negation? new Negation() : new Subtraction());
				break;
			case MULTIPLY:
				segments.add(new Multiplication());
				break;
			case NUMBER:
				if (prevLiteral == Token.Type.NUMBER)
					throw new RuntimeException("Malformed expression! Two consecutive numbers found without a separating operation.");
				else if (prevLiteral != null)
					segments.add(new Multiplication());
				segments.add(new Constant(Double.parseDouble(token.value)));
				currLiteral = token.type;
				break;
			case OPEN_PAREN:
				if (prevLiteral != null)
					segments.add(new Multiplication());
				// Opens a new segment
				it.index++;
				segments.add(parse(it));
				currLiteral = Token.Type.CLOSE_PAREN;
				break;
			case PLUS:
				segments.add(new Addition());
				break;
			case ROOT:
				segments.add(new Root());
				break;
			case ROUND:
				segments.add(new Round());
				break;
			case SIN:
				segments.add(new Sine());
				break;
			case TAN:
				segments.add(new Tangent());
				break;
			default: // this should not happen unless I expand the types without updating this
				throw new RuntimeException("Malformed expression containing unrecognized token!");
			}
			prevLiteral = currLiteral;
		}
		
		if (segments.size() == 0)
			throw new RuntimeException("Malformed expression containing empty parentheses!");
		
		// Scan through and find the precedence levels to work with
		Set<Integer> precs = new TreeSet<>();
		for (Valuable seg: segments) {
			if (!seg.usable())
				precs.add(seg.getPrecedence());
		}
		
		// Now we follow precedence to evaluate segments:
		List<Integer> list = new ArrayList<>(precs);
		Collections.reverse(list);
		for (Integer prec: list) {
			// Iterate backwards through the segment list indices
			for (int i = segments.size() - 1; i >= 0; i--) {
				Valuable seg = segments.get(i);
				if (!seg.usable() && seg.getPrecedence() == prec.intValue())
					// matching precedence allows application
					seg.apply(segments, i);
			}
		}
		// If there is more than one segment remaining, we have an error
		if (segments.size() > 1)
			throw new RuntimeException("Malformed expression! Multiple unconnected segments.");
		Valuable seg = segments.get(0);
		if (!seg.usable())
			throw new RuntimeException("Malformed expression! Missing arguments for " + seg.getClass().getSimpleName() + ".");
		
		// Try to optimize if it is allowable
		if (optimize)
			seg.optimize(new ExpressionSolver(new String[] {}, new double[] {}));
		
		return seg;
	}
	
	protected static abstract class Valuable implements Expression {
		public int getPrecedence() {
			return 0;
		}
		
		public void apply(List<Valuable> segments, int selfIndex) {}
		
		public boolean usable() {
			return true;
		}
		
		public void optimize(ExpressionSolver s) {}
	}
	
	protected static abstract class Op extends Valuable {
		protected Valuable rhs = null;
		
		@Override
		public void apply(List<Valuable> segments, int selfIndex) {
			setRhs(segments.remove(selfIndex + 1));
		}
		
		public void setRhs(Valuable rhs) {
			if (!rhs.usable())
				throw new RuntimeException("Malformed expression! Missing right argument for " + this.getClass().getSimpleName() + ".");
			this.rhs = rhs;
		}
		
		@Override
		public boolean usable() {
			return rhs != null;
		}
		
		@Override
		public void optimize(ExpressionSolver s) {
			// The very simplest optimization is to try to evaluate the operand and
			// save the value if it resolves without any variables.
			try {
				double val = rhs.getValue(s);
				// If we made it this far, then it is constant
				rhs = new Constant(val);
			}catch(UndefinedVarException e) {
				// try to optimize the child element
				rhs.optimize(s);
			}
		}
		
		@Override
		public boolean equals(Object o) {
			if (o == null || !(o instanceof Op))
				return false;
			return rhs.equals(((Op)o).rhs);
		}
	}
	
	protected static abstract class BinOp extends Op {
		protected Valuable lhs = null;
		
		@Override
		public void apply(List<Valuable> segments, int selfIndex) {
			super.apply(segments, selfIndex);
			setLhs(segments.remove(selfIndex - 1));
		}
		
		public void setLhs(Valuable lhs) {
			if (!lhs.usable())
				throw new RuntimeException("Malformed expression! Missing left argument for " + this.getClass().getSimpleName() + ".");
			this.lhs = lhs;
		}
		
		@Override
		public boolean usable() {
			return super.usable() && lhs != null;
		}
		
		@Override
		public void optimize(ExpressionSolver s) {
			super.optimize(s);
			// Also try to optimize the left-hand side
			try {
				double val = lhs.getValue(s);
				// If we made it this far, then it is constant
				lhs = new Constant(val);
			}catch(UndefinedVarException e) {
				// try to optimize the child element
				lhs.optimize(s);
			}
		}
		
		@Override
		public boolean equals(Object o) {
			if (!(super.equals(o) && o instanceof BinOp))
				return false;
			return lhs.equals(((BinOp)o).lhs);
		}
	}
	
	protected static class Addition extends BinOp {
		@Override
		public double getValue(ExpressionSolver s) {
			return lhs.getValue(s) + rhs.getValue(s);
		}

		@Override
		public int getPrecedence() {
			return 2;
		}
	}
	
	protected static class Subtraction extends BinOp {
		@Override
		public double getValue(ExpressionSolver s) {
			return lhs.getValue(s) - rhs.getValue(s);
		}
		
		@Override
		public int getPrecedence() {
			return 2;
		}
	}
	
	protected static class Negation extends Op {
		@Override
		public double getValue(ExpressionSolver s) {
			return -rhs.getValue(s);
		}
		
		@Override
		public int getPrecedence() {
			return 5;
		}
	}
	
	protected static class Multiplication extends BinOp {
		@Override
		public double getValue(ExpressionSolver s) {
			return lhs.getValue(s) * rhs.getValue(s);
		}
		
		@Override
		public int getPrecedence() {
			return 3;
		}
	}
	
	protected static class Division extends BinOp {
		@Override
		public double getValue(ExpressionSolver s) {
			return lhs.getValue(s) / rhs.getValue(s);
		}
		
		@Override
		public int getPrecedence() {
			return 3;
		}
	}
	
	protected static class Exponentiation extends BinOp {
		@Override
		public double getValue(ExpressionSolver s) {
			return Math.pow(lhs.getValue(s), rhs.getValue(s));
		}
		
		@Override
		public int getPrecedence() {
			return 4;
		}
	}
	
	protected static class Root extends BinOp {
		@Override
		public double getValue(ExpressionSolver s) {
			return Math.pow(rhs.getValue(s), 1/lhs.getValue(s));
		}
		
		@Override
		public int getPrecedence() {
			return 4;
		}
	}
	
	protected static class Min extends BinOp {
		@Override
		public double getValue(ExpressionSolver s) {
			return Math.min(lhs.getValue(s), rhs.getValue(s));
		}
		
		@Override
		public int getPrecedence() {
			return 1;
		}
	}
	
	protected static class Max extends BinOp {
		@Override
		public double getValue(ExpressionSolver s) {
			return Math.max(lhs.getValue(s), rhs.getValue(s));
		}
		
		@Override
		public int getPrecedence() {
			return 1;
		}
	}
	
	protected static class Cosine extends Op {
		@Override
		public double getValue(ExpressionSolver s) {
			return Math.cos(rhs.getValue(s));
		}
		
		@Override
		public int getPrecedence() {
			return 5;
		}
	}
	
	protected static class Sine extends Op {
		@Override
		public double getValue(ExpressionSolver s) {
			return Math.sin(rhs.getValue(s));
		}
		
		@Override
		public int getPrecedence() {
			return 5;
		}
	}
	
	protected static class Tangent extends Op {
		@Override
		public double getValue(ExpressionSolver s) {
			return Math.tan(rhs.getValue(s));
		}
		
		@Override
		public int getPrecedence() {
			return 5;
		}
	}
	
	protected static class Logarithm extends BinOp {
		@Override
		public double getValue(ExpressionSolver s) {
			return Math.log(rhs.getValue(s)) / Math.log(lhs.getValue(s));
		}
		
		@Override
		public int getPrecedence() {
			return 4;
		}
	}
	
	protected static class NatLog extends Op {
		@Override
		public double getValue(ExpressionSolver s) {
			return Math.log(rhs.getValue(s));
		}
		
		@Override
		public int getPrecedence() {
			return 5;
		}
	}
	
	protected static class Round extends Op {
		@Override
		public double getValue(ExpressionSolver s) {
			return Math.round(rhs.getValue(s));
		}
		
		@Override
		public int getPrecedence() {
			return 5;
		}
	}
	
	protected static class Ceiling extends Op {
		@Override
		public double getValue(ExpressionSolver s) {
			return Math.ceil(rhs.getValue(s));
		}
		
		@Override
		public int getPrecedence() {
			return 5;
		}
	}
	
	protected static class Floor extends Op {
		@Override
		public double getValue(ExpressionSolver s) {
			return Math.floor(rhs.getValue(s));
		}
		
		@Override
		public int getPrecedence() {
			return 5;
		}
	}
	
	protected static class Constant extends Valuable {
		protected double val;
		
		public Constant(double val) {
			this.val = val;
		}
		
		@Override
		public double getValue(ExpressionSolver s) {
			return val;
		}
		
		@Override
		public boolean equals(Object o) {
			if (o == null || !(o instanceof Constant))
				return false;
			Constant other = (Constant)o;
			final double ETA = 0.001;
			return val + ETA > other.val && other.val + ETA > val;
		}
	}
	
	protected static class Variable extends Valuable {
		protected String name;
		
		public Variable(String name) {
			this.name = name;
		}
		
		@Override
		public double getValue(ExpressionSolver s) {
			return s.get(name);
		}
		
		@Override
		public boolean equals(Object o) {
			if (o == null || !(o instanceof Variable))
				return false;
			return name.equals(((Variable)o).name);
		}
	}

}
