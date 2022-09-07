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
		if (optimize) {
			Valuable opt = seg.optimize(new ExpressionSolver(new String[] {}, new double[] {}));
			if (opt != null)
				seg = opt;
		}
		
		return seg;
	}
	
	protected static abstract class Valuable implements Expression {
		protected Op parent = null; // useful to keep track of the parent for optimization's sake
		
		public int getPrecedence() {
			return 0;
		}
		
		public void apply(List<Valuable> segments, int selfIndex) {}
		
		public boolean usable() {
			return true;
		}
		
		public Valuable optimize(ExpressionSolver s) {
			return null;
		}
	}
	
	protected static abstract class Op extends Valuable {
		protected Valuable rhs = null;
		
		@Override
		public void apply(List<Valuable> segments, int selfIndex) {
			setRhs(segments.remove(selfIndex + 1));
		}
		
		public void setRhs(Valuable rhs) {
			rhs.parent = this;
			if (!rhs.usable())
				throw new RuntimeException("Malformed expression! Missing right argument for " + this.getClass().getSimpleName() + ".");
			this.rhs = rhs;
		}
		
		@Override
		public boolean usable() {
			return rhs != null;
		}
		
		@Override
		public Valuable optimize(ExpressionSolver s) {
			// Optimize children, then optimize self
			optimizeChildren(s);
			
			// Call operation-specific optimizations
			Valuable got = optimize();
			Valuable newThis = this;
			if (got != null)
				newThis = got;
			
			// Lastly, try to fold this into a single constant
			try {
				double val = newThis.getValue(s);
				// Pass the constant up to the parent instead of itself
				return new Constant(val);
			}catch(UndefinedVarException e) {
				// Don't do anything, no more optimizations possible.
				if (newThis != this) {
					// update its parent
					newThis.parent = this.parent;
					return newThis;
				}
				return null;
			}
		}
		
		protected void optimizeChildren(ExpressionSolver s) {
			Valuable right = rhs.optimize(s);
			if (right != null)
				setRhs(right);
		}
		
		protected Valuable optimize() {
			return null;
		}
		
		protected void replaceChild(Valuable child, Valuable replaceWith) {
			if (child == rhs)
				setRhs(replaceWith);
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
			lhs.parent = this;
			if (!lhs.usable())
				throw new RuntimeException("Malformed expression! Missing left argument for " + this.getClass().getSimpleName() + ".");
			this.lhs = lhs;
		}
		
		@Override
		public boolean usable() {
			return super.usable() && lhs != null;
		}
		
		@Override
		protected void optimizeChildren(ExpressionSolver s) {
			super.optimizeChildren(s);
			Valuable left = lhs.optimize(s);
			if (left != null)
				setLhs(left);
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
		
		protected static class ConstantRef {
			public final Op parent;
			public final boolean rightChild;
			public final boolean negated;
			
			public ConstantRef(Op parent, boolean rightChild, boolean negated) {
				this.parent = parent;
				this.rightChild = rightChild;
				this.negated = negated;
			}
		}
		protected ConstantRef findConstant(Valuable start, boolean rightChild, boolean negated) {
			// We can work with addition and subtraction
			if (start instanceof Constant) {
				return new ConstantRef(start.parent, rightChild, negated);
			}else if (start instanceof Addition) {
				Addition add = (Addition)start;
				ConstantRef left = findConstant(add.lhs, false, negated);
				if (left != null)
					return left;
				return findConstant(add.rhs, true, negated);
			}else if (start instanceof Subtraction) {
				Subtraction sub = (Subtraction)start;
				ConstantRef left = findConstant(sub.lhs, false, negated);
				if (left != null)
					return left;
				return findConstant(sub.rhs, true, !negated);
			}else if (start instanceof Negation) {
				return findConstant(((Negation)start).rhs, true, !negated);
			}
			return null;
		}
		
		@Override
		protected Valuable optimize() {
			// Addition is commutative and associative, which gives us some flexibility
			// Since the children have already been optimized, we just need to focus on
			// combining constants from both sides.
			// In other words, there should not be multiple constants to combine on a
			// single side, since those checks were already done.
			ConstantRef left = findConstant(lhs, false, false);
			if (left == null)
				return null; // We need to find constants on both sides to perform this operation
			ConstantRef right = findConstant(rhs, true, false);
			if (right == null)
				return null;
			
			// Collapse the constants, bringing from left to right
			// Both parents must be BinOp, since negation already ran its constant collapse optimization
			Constant rightConst;
			if (right.rightChild) {
				rightConst = (Constant)right.parent.rhs;
			}else
				rightConst = (Constant)((BinOp)right.parent).lhs;
			
			double leftConst;
			BinOp parent = (BinOp)left.parent;
			if (left.rightChild)
				leftConst = ((Constant)parent.rhs).val;
			else
				// opposite logic to just above
				leftConst = ((Constant)parent.lhs).val;
			boolean thisParent = !replaceParent(parent, !left.rightChild);
			// Apply left to right
			if (left.negated ^ right.negated)
				leftConst *= -1;
			rightConst.val += leftConst;
			
			if (thisParent) // if this was the parent of the left child, 
				return rhs; // then we need to replace it with the right
			return null; // otherwise, changes were made in place
		}
		
		protected boolean replaceParent(BinOp parent, boolean rightReplace) {
			Valuable replaceWith;
			if (rightReplace) {
				// We can run into complications if the replace with is negated
				if (parent instanceof Subtraction) {
					Negation neg = new Negation();
					neg.setRhs(parent.rhs);
					replaceWith = neg;
				}else
					replaceWith = parent.rhs;
			}else
				replaceWith = parent.lhs; // left hand side cannot be negated
			
			Op grand = parent.parent;
			// If there is no grandparent, then the return is the parent
			if (grand == null)
				return false;
			if (grand.rhs == parent) // if the parent was its parent's right child
				grand.setRhs(replaceWith);
			else // parent was its parent's left child
				((BinOp)grand).setLhs(replaceWith);
			return true;
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

		@Override
		protected Valuable optimize() {
			// Try to reuse the logic from add (since subtract is very similar)
			Addition useAdd = new Addition();
			useAdd.setLhs(lhs);
			Negation neg = new Negation();
			neg.setRhs(rhs);
			Valuable right = neg.optimize();
			boolean optNeg; // We need to know if negation optimized in order to restore state
			if (right == null) {
				right = neg;
				optNeg = false;
			}else
				optNeg = true;
			
			useAdd.setRhs(right);
			Valuable opt = useAdd.optimize();
			// If an optimization was made, we use it instead of this
			if (opt != null)
				return opt;
			if (optNeg)
				return useAdd;
			
			// If we could not use either, we need to restore the operands of this
			setLhs(lhs);
			setRhs(rhs);
			return null;
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

		@Override
		protected Valuable optimize() {
			if (rhs instanceof Constant) {
				// negate the constant and remove this step
				Constant right = (Constant)rhs;
				right.val *= -1;
				return right;
			}else if (rhs instanceof Negation) {
				// cancel the two negations
				return ((Negation)rhs).rhs;
			}
			return null;
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

		@Override
		protected Valuable optimize() {
			// TODO Auto-generated method stub
			return null;
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

		@Override
		protected Valuable optimize() {
			// TODO Auto-generated method stub
			return null;
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

		@Override
		protected Valuable optimize() {
			// TODO Auto-generated method stub
			return null;
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

		@Override
		protected Valuable optimize() {
			// TODO Auto-generated method stub
			return null;
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

		@Override
		protected Valuable optimize() {
			// TODO Auto-generated method stub
			return null;
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

		@Override
		protected Valuable optimize() {
			// TODO Auto-generated method stub
			return null;
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

		@Override
		protected Valuable optimize() {
			// TODO Auto-generated method stub
			return null;
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

		@Override
		protected Valuable optimize() {
			return null;
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
