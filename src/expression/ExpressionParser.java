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
	public double maxErr = 0.0001;
	
	/**
	 * Parses the given tokens into a Valuable. The result will likely be a tree
	 * of operations, since many Valuables have one or more operands.
	 * <p>
	 * Parsing will fold any constant operations encountered.
	 * @param tokens the tokens to parse
	 * @param varNames an array of recognized identifiers. If null, all variables are accepted
	 * @param optimize whether the result expression should be optimized
	 * @return the Valuable result
	 */
	public Expression parse(List<Token> tokens, String[] varNames, boolean optimize) {
		TokenIterator it = new TokenIterator(tokens);
		return parse(it, varNames, optimize);
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
	 * @param optimize whether the result expression should be optimized
	 * @return the parsed expression
	 */
	protected Valuable parse(TokenIterator it, String[] varNames, boolean optimize) {
		ArrayList<Valuable> segments = new ArrayList<>();
		boolean mustFinish = it.index == 0;
		
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
				// Verify that this identifier exists with the parsing solver
				if (varNames != null && !List.of(varNames).contains(token.value))
					throw new RuntimeException("Unrecognized variable: \"" + token.value + "\"!");
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
				// We can find out by remembering the previous token
				segments.add(prevLiteral == null? new Negation() : new Subtraction());
				break;
			case MULTIPLY:
				segments.add(new Multiplication());
				break;
			case NUMBER:
				if (prevLiteral == Token.Type.NUMBER)
					throw new RuntimeException("Malformed expression! Two consecutive numbers found without a separating operation.");
				else if (prevLiteral != null)
					segments.add(new Multiplication());
				segments.add(new Constant(Double.parseDouble(token.value), maxErr));
				currLiteral = token.type;
				break;
			case OPEN_PAREN:
				if (prevLiteral != null)
					segments.add(new Multiplication());
				// Opens a new segment
				it.index++;
				segments.add(parse(it, varNames, optimize));
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
		
		if (it.index < it.tokens.size() && mustFinish)
			throw new RuntimeException("Malformed expression containing too many close parentheses!");
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
			// Iterate through the segment list indices
			for (int i = 0; i < segments.size(); i++) {
				Valuable seg = segments.get(i);
				if (!seg.usable() && seg.getPrecedence() == prec.intValue()) {
					// matching precedence allows application
					if (seg.apply(segments, i))
						// if the segment took a left argument, redo the current index
						i--;
				}
			}
		}
		// If there is more than one segment remaining, we have an error
		if (segments.size() > 1) {
			StringBuilder build = new StringBuilder();
			boolean first = true;
			for (Valuable seg: segments) {
				if (first)
					first = false;
				else
					build.append(", ");
				build.append(seg.getClass().getSimpleName());
			}
			throw new RuntimeException("Malformed expression! Multiple unconnected segments: " + build.toString());
		}
		Valuable seg = segments.get(0);
		if (!seg.usable())
			throw new RuntimeException("Malformed expression! Missing arguments for " + seg.getClass().getSimpleName() + ".");
		
		// Try to optimize if it is allowable
		if (optimize) {
			ExpressionSolver opter = new ExpressionSolver(new String[] {});
			opter.setValues(new double[] {});
			opter.parse = this; // use the same max error as this
			Valuable opt = seg.optimize(opter);
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
		
		/**
		 * Apply necessary arguments.
		 * @param segments the list of all contiguous segments
		 * @param selfIndex the index of this
		 * @return whether this took a left argument, or in other words, whether the application
		 * caused necessitates another analysis of the same list index
		 */
		public boolean apply(List<Valuable> segments, int selfIndex) {
			return false;
		}
		
		public boolean usable() {
			return true;
		}
		
		public Valuable optimize(ExpressionSolver s) {
			return null;
		}
	}
	
	protected static abstract class Op extends Valuable {
		protected Valuable rhs = null;
		protected boolean optimized = false;
		
		@Override
		public boolean apply(List<Valuable> segments, int selfIndex) {
			setRhs(segments.remove(selfIndex + 1));
			return false;
		}
		
		public void setRhs(Valuable rhs) {
			rhs.parent = this;
			if (!rhs.usable())
				throw new RuntimeException("Malformed expression! Missing right argument for " + this.getClass().getSimpleName() + ".");
			this.rhs = rhs;
			alter(); // since it was changed, we may need to re-optimize
		}
		
		protected void alter() {
			if (optimized) {
				optimized = false;
				if (parent != null && parent instanceof Op)
					((Op)parent).alter();
			}
			// If we were not optimized before, don't do anything...
		}
		
		@Override
		public boolean usable() {
			return rhs != null;
		}
		
		@Override
		public Valuable optimize(ExpressionSolver s) {
			if (optimized)
				return null;
			
			// Optimize children, then optimize self
			optimizeChildren(s);
			
			// Call operation-specific optimizations
			Valuable got = optimizeSpec(s);
			Valuable newThis = this;
			if (got != null)
				newThis = got;
			else
				optimized = true;
			
			// Lastly, try to fold this into a single constant
			try {
				double val = newThis.getValue(s);
				// Pass the constant up to the parent instead of itself
				return new Constant(val, s.parse.maxErr);
			}catch(UndefinedVarException e) {
				// Don't do anything, no more optimizations possible.
				if (newThis != this) {
					// update its parent
					newThis.parent = this.parent;
					// It may be optimizable, so try to optimize again (until we reach null or this)
					Valuable opted = newThis.optimize(s);
					// We still need to tell the caller of this if any changes were made
					if (opted == null)
						return newThis;
					else // otherwise return the refined result
						return opted;
				}
				return got; // if got was updated, we should inform the caller
			}
		}
		
		protected void optimizeChildren(ExpressionSolver s) {
			Valuable right = rhs.optimize(s);
			if (right != null)
				setRhs(right);
		}
		
		/**
		 * Run specific optimizations for this operation. Note that when this is called,
		 * child operations are assumed to have already been optimized. Also, after this
		 * method, if this operation has constant argument(s), it will be folded. As
		 * such, constant folding should <i>not</i> be checked here with operation-specific
		 * optimizations.
		 * <p>
		 * By default, this does nothing more than return null. However, it is made concrete
		 * (instead of included as abstract) to reduce redundancy for operations without
		 * specific optimizations.
		 * @param s an expression solver instance, typically a trivial one used exclusively
		 * for finding whether an argument can resolve without any variables.
		 * @return one of three options:<ol>
		 * <li>a more optimized object to replace this
		 * <li>this, if an optimization was made to it
		 * <li>null, if no optimizations were performed
		 * </ol>
		 */
		protected Valuable optimizeSpec(ExpressionSolver s) {
			return null;
		}
		
		@Override
		public boolean equals(Object o) {
			if (o == null || !(o instanceof Op))
				return false;
			return rhs.equals(((Op)o).rhs);
		}
		
		public abstract String opString();
		
		public String toString() {
			StringBuffer buf = new StringBuffer("(");
			buf.append(opString());
			buf.append(" ");
			buf.append(rhs);
			buf.append(")");
			return buf.toString();
		}
	}
	
	protected static abstract class BinOp extends Op {
		protected Valuable lhs = null;
		
		@Override
		public boolean apply(List<Valuable> segments, int selfIndex) {
			super.apply(segments, selfIndex);
			setLhs(segments.remove(selfIndex - 1));
			return true;
		}
		
		public void setLhs(Valuable lhs) {
			lhs.parent = this;
			if (!lhs.usable())
				throw new RuntimeException("Malformed expression! Missing left argument for " + this.getClass().getSimpleName() + ".");
			this.lhs = lhs;
			alter();
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
		
		public String toString() {
			StringBuffer buf = new StringBuffer("(");
			buf.append(lhs);
			buf.append(" ");
			buf.append(opString());
			buf.append(" ");
			buf.append(rhs);
			buf.append(")");
			return buf.toString();
		}
	}
	
	/**
	 * Some optimizations need to locate some type of operation in one or both of their
	 * children expressions. For example, Addition/Subtraction and Multiplication both
	 * perform an optimization to extract a constant from the left tree and from the
	 * right tree in order to combine the values. Also, Negation looks for a constant
	 * to apply its negation to. This class is a structure to wrap around the necessary
	 * state variables.
	 */
	protected static class NodeRef {
		/** The parent of the node found */
		public final Op parent;
		/** Whether the constant is the right child (rhs) of the given parent */
		public final boolean rightChild;
		/** Whether the constant has been reversed (negated for Addition/Subtraction, inverse
		 *  for Multiplication/Division) by parent nodes in the tree. */
		public final boolean reverse;
		
		public NodeRef(Op parent, boolean rightChild, boolean reverse) {
			this.parent = parent;
			this.rightChild = rightChild;
			this.reverse = reverse;
		}
		
		public Valuable getNode() {
			if (rightChild)
				return parent.rhs;
			else
				return ((BinOp)parent).lhs;
		}
	}
	
	protected static class Addition extends BinOp {
		protected boolean optChanges = false;
		
		@Override
		public double getValue(ExpressionSolver s) {
			return lhs.getValue(s) + rhs.getValue(s);
		}

		@Override
		public int getPrecedence() {
			return 2;
		}
		
		protected NodeRef findConstant(Valuable start, boolean rightChild, boolean negated) {
			// We can work with addition, subtraction, and negation (which is subtraction from 0)
			if (start instanceof Constant) {
				return new NodeRef(start.parent, rightChild, negated);
			}else if (start instanceof Addition) {
				Addition add = (Addition)start;
				NodeRef left = findConstant(add.lhs, false, negated);
				if (left != null)
					return left;
				return findConstant(add.rhs, true, negated);
			}else if (start instanceof Subtraction) {
				Subtraction sub = (Subtraction)start;
				NodeRef left = findConstant(sub.lhs, false, negated);
				if (left != null)
					return left;
				return findConstant(sub.rhs, true, !negated);
			}else if (start instanceof Negation) {
				return findConstant(((Negation)start).rhs, true, !negated);
			}
			return null;
		}
		
		@Override
		protected Valuable optimizeSpec(ExpressionSolver s) {
			optChanges = false;
			// Addition is commutative and associative, which gives us some flexibility
			// We perform two major calculations here:
			// - First, we check for redundant operations (for example, x + 0 = x).
			// - Second, we try to combine constants from the two arms of the operation.
			//   For example, (x - 1) + (y + 3) = x + (y + 2).
			//   Also, -(2 + j) - (k + (m - 6)) = -j - (k + (m - 8)).
			
			// Check for redundant addition
			// x + 0 = x
			Constant none = null;
			Valuable other = null;
			if (lhs instanceof Constant && !(rhs instanceof Constant)) {
				none = (Constant)lhs;
				other = rhs;
			}else if (rhs instanceof Constant && !(lhs instanceof Constant)) {
				none = (Constant)rhs;
				other = lhs;
			}
			if (none != null && s.parse.equals(none.val, 0))
				return other;
			
			// Since the children have already been optimized, we just need to focus on
			// combining constants from both sides.
			// In other words, there should not be multiple constants to combine on a
			// single side, since those checks were already done.
			NodeRef left = findConstant(lhs, false, false);
			if (left != null) {
				NodeRef right = findConstant(rhs, true, false);
				if (right != null) {
					// Collapse the constants, bringing from left to right
					// Both parents must be BinOp, since negation already ran its constant collapse optimization
					Constant rightConst;
					if (right.rightChild) {
						rightConst = (Constant)right.parent.rhs;
					}else
						rightConst = (Constant)((BinOp)right.parent).lhs;
					
					double leftConst = ((Constant)left.getNode()).val;
					boolean thisParent = !replaceParent((BinOp)left.parent, !left.rightChild);
					// Apply left to right
					if (left.reverse ^ right.reverse)
						leftConst *= -1;
					rightConst.val += leftConst;
					
					if (thisParent) // if this was the parent of the left child, 
						return rhs; // then we need to replace it with the right
					optChanges = true;
				}
			}
			
			// We need to look through the children for any logs
			// Logs of the same base can be combined. ln(5) + ln(2) = ln(10)
			Valuable res = collapseLogs(this, s);
			if (res != null)
				return res;
			
			if (optChanges)
				return this;
			return null;
		}
		
		protected Valuable collapseLogs(BinOp start, ExpressionSolver s) {
			// Similarly to finding a constant,
			// we can look through negations, additions, and subtractions
			List<NodeRef> logs = new ArrayList<>();
			collapseLogs(s, logs, start.lhs, false, false);
			// It is not possible for the start to need to be replaced since start
			// must be a BinOp, and its left arm is analyzed first, if the left arm
			// is a log, that log would be amended rather than discarded (since it
			// would be seen first and only logs seen after logs of the same base
			// are discarded).
			// However, we need to check for the right arm since it is feasible
			// that the right side is a log of the same base as the left side:
			//      +        -> ln (4 * 2) =
			// ln 4   ln 2      ln 8
			boolean replaced = collapseLogs(s, logs, start.rhs, true, false);
			if (!replaced || start.lhs.parent != this) // if replacement failed
				return start.lhs; // must not pass rhs, which needs to be discarded
				
			return null;
		}
		protected boolean collapseLogs(ExpressionSolver s, List<NodeRef> logs,
				Valuable op, boolean rightChild, boolean negated) {
			if (op instanceof Logarithm || op instanceof NatLog) {
				Valuable base;
				if (op instanceof NatLog)
					base = new Constant(Math.E);
				else
					base = ((Logarithm)op).lhs;
				// Look through the list of logs to find if this base is already represented
				for (NodeRef ref: logs) {
					Valuable log = ref.getNode();
					
					Valuable otherBase;
					if (log instanceof Logarithm)
						otherBase = ((Logarithm)log).lhs;
					else
						otherBase = new Constant(Math.E);
					if (base.equals(otherBase)) {
						// Now we must combine op into the found other
						BinOp arg;
						if (ref.reverse == negated)
							arg = new Multiplication();
						else
							arg = new Division();
						arg.setLhs(((Op)log).rhs);
						arg.setRhs(((Op)op).rhs);
						Valuable opted = arg.optimize(s);
						if (opted == null)
							opted = arg;
						((Op)log).setRhs(opted);
						// I cannot think of any optimization where modifying the operation to
						// an optimized multiplication would require a re-optimization of the
						// log. Potentially, if xlog(x) + xlog(x) -> xlog(x*x) -> xlog(x^2) -> 2,
						// but right now I don't do the optimization of x*x -> x^2. Since
						// exponentiation is generally slower than multiplication, I don't know
						// if I ever will add that optimization. 
						
						// Now the fun part, where we discard op from the tree
						optChanges = true;
						Op parent = op.parent;
						// the parent could be a negation
						while (parent instanceof Negation) {
							rightChild = parent.parent.rhs == parent;
							parent = parent.parent;
						}
						return replaceParent((BinOp)parent, !rightChild);
					}
				}
				// If we did not find the base of this log, then add this to the list
				logs.add(new NodeRef(op.parent, rightChild, negated));
			}else if (op instanceof Addition) {
				Addition add = (Addition)op;
				collapseLogs(s, logs, add.lhs, false, negated);
				collapseLogs(s, logs, add.rhs, true, negated);
			}else if (op instanceof Subtraction) {
				Subtraction sub = (Subtraction)op;
				collapseLogs(s, logs, sub.lhs, false, negated);
				collapseLogs(s, logs, sub.rhs, true, !negated);
			}else if (op instanceof Negation) {
				return collapseLogs(s, logs, ((Negation)op).rhs, true, !negated);
			}
			// Addition and Subtraction cannot fail, since replacement can only fail at
			// the top level and if an addition or subtraction is seen, the children
			// have a parent to replace.
			return true;
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

		@Override
		public String opString() {
			return "+";
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
		protected Valuable optimizeSpec(ExpressionSolver s) {
			// If lhs is equivalent to rhs, then they cancel out to 0
			if (lhs.equals(rhs))
				return new Constant(0);
			
			// Try to reuse the logic from add (since subtract is very similar)
			Addition useAdd = new Addition();
			useAdd.setLhs(lhs);
			Negation neg = new Negation();
			neg.setRhs(rhs);
			Valuable right = neg.optimize(s);
			boolean optNeg; // We need to know if negation optimized in order to restore state
			if (right == null) {
				right = neg;
				optNeg = false;
			}else
				optNeg = true;
			
			useAdd.setRhs(right);
			Valuable opt = useAdd.optimize(s);
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
		
		@Override
		public String opString() {
			return "-";
		}
	}
	
	protected static class Negation extends Op {
		@Override
		public double getValue(ExpressionSolver s) {
			return -rhs.getValue(s);
		}
		
		@Override
		public int getPrecedence() {
			return 6;
		}

		@Override
		protected Valuable optimizeSpec(ExpressionSolver s) {
			if (rhs instanceof Negation)
				// cancel the two negations
				return ((Negation)rhs).rhs;
			if (rhs instanceof Multiplication || rhs instanceof Division) {
				// We can send the negation down into the children, which are more
				// likely to be able to cancel it or apply it to some constant.
				
				// Look for a negation or constant in both
				NodeRef parToApply = findConstantOrNeg(rhs, false);
				if (parToApply != null) {
					Negation neg = new Negation();
					if (parToApply.rightChild) {
						neg.setRhs(parToApply.parent.rhs);
						Valuable newRhs = neg.optimize(s);
						if (newRhs == null)
							newRhs = neg;
						parToApply.parent.setRhs(newRhs);
					}else {
						BinOp parent = (BinOp)parToApply.parent;
						neg.setRhs(parent.lhs);
						Valuable newRhs = neg.optimize(s);
						if (newRhs == null)
							newRhs = neg;
						parent.setLhs(newRhs);
					}
					return rhs;
				}else {
					// If no constant could be found, we will still pass the negation down
					// (since it can be useful for optimizations for operations to see their
					// actual applicable descendants rather than having them masked with a
					// negation).
					BinOp op = (BinOp)rhs;
					setRhs(op.lhs);
					op.setLhs(this);
					return op;
				}
			}
			return null;
		}
		
		protected NodeRef findConstantOrNeg(Valuable start, boolean rightChild) {
			// We can work with multiplication and division
			if (start instanceof Constant || start instanceof Negation) {
				return new NodeRef(start.parent, rightChild, false);
			}else if (start instanceof Multiplication) {
				Multiplication mult = (Multiplication)start;
				NodeRef left = findConstantOrNeg(mult.lhs, false);
				if (left != null)
					return left;
				return findConstantOrNeg(mult.rhs, true);
			}else if (start instanceof Division) {
				Division div = (Division)start;
				NodeRef left = findConstantOrNeg(div.lhs, false);
				if (left != null)
					return left;
				return findConstantOrNeg(div.rhs, true);
			}
			return null;
		}
		
		@Override
		public String opString() {
			return "+";
		}
	}
	
	protected static class Multiplication extends BinOp {
		protected boolean optChanges = false;
		protected boolean mayCollapseRightDiv = true;
		
		@Override
		public double getValue(ExpressionSolver s) {
			return lhs.getValue(s) * rhs.getValue(s);
		}
		
		@Override
		public int getPrecedence() {
			return 3;
		}

		// This code is very similar to the procedures used by Addition/Subtraction.
		// I have not yet found a clean way to abstract it.
		protected NodeRef findConstant(Valuable start, boolean rightChild, boolean inverse) {
			// We can work with multiplication, division, and negation (which is multiply by -1)
			if (start instanceof Constant) {
				return new NodeRef(start.parent, rightChild, inverse);
			}else if (start instanceof Multiplication) {
				Multiplication mult = (Multiplication)start;
				NodeRef left = findConstant(mult.lhs, false, inverse);
				if (left != null)
					return left;
				return findConstant(mult.rhs, true, inverse);
			}else if (start instanceof Division) {
				Division div = (Division)start;
				NodeRef left = findConstant(div.lhs, false, inverse);
				if (left != null)
					return left;
				return findConstant(div.rhs, true, !inverse);
			}else if (start instanceof Negation) {
				return findConstant(((Negation)start).rhs, true, inverse);
			}
			return null;
		}
		
		@Override
		protected Valuable optimizeSpec(ExpressionSolver s) {
			optChanges = false;
			
			// Check for redundant multiplication operation
			// x * 1 = x
			Constant cons = null;
			Valuable other = null;
			if (lhs instanceof Constant && !(rhs instanceof Constant)) {
				cons = (Constant)lhs;
				other = rhs;
			}else if (rhs instanceof Constant && !(lhs instanceof Constant)) {
				cons = (Constant)rhs;
				other = lhs;
			}
			if (cons != null) {
				if (s.parse.equals(cons.val, 1))
					return other;
				// We can only perform the times by 0 reduction if the other number is rational.
				// Irrational cases may not result to 0. For example, Inf * 0 = NaN, NaN * 0 = NaN.
				if (s.rational && s.parse.equals(cons.val, 0))
					return cons;
			}
			
			// Check for collapsible division
			Division div = null;
			other = null;
			boolean rightChild = true;
			if (lhs instanceof Division) {
				div = (Division)lhs;
				if (rhs instanceof Division) {
					Division div2 = (Division)rhs;
					// Try to combine numerators with each other
					// and denominators with each other.
					// If there is simplification from at least one,
					// keep the result.
					Multiplication numerator = new Multiplication();
					numerator.setLhs(div.lhs);
					numerator.setRhs(div2.lhs);
					Valuable numer = numerator.optimize(s);
					Multiplication denominator = new Multiplication();
					denominator.setLhs(div.rhs);
					denominator.setRhs(div2.rhs);
					Valuable denom = denominator.optimize(s);
					if (numer == null && denom == null) {
						// this transformation was not helpful, so undo it
						div.setLhs(div.lhs);
						div.setRhs(div.rhs);
						div2.setLhs(div2.lhs);
						div2.setRhs(div2.rhs);
					}else {
						// We can reuse div instead of construction a new Division object
						if (numer == null)
							numer = numerator;
						div.setLhs(numer);
						if (denom == null)
							denom = denominator;
						div.setRhs(denom);
						return div;
					}
				}
				other = rhs;
				rightChild = false;
			}else if (rhs instanceof Division && mayCollapseRightDiv) {
				// We cannot use right division if this came from Division's optimization
				// (since otherwise we end up with infinite recursion by collapsing 
				//  division's convolution).
				div = (Division)rhs;
				other = lhs;
			}
			if (div != null) {
				// Try to simplify the multiplication by combining other with the dividend
				Multiplication combine = new Multiplication();
				combine.setLhs(other);
				combine.setRhs(div.lhs);
				Valuable dividend = combine.optimize(s);
				// If it simplified any, we will use it.
				if (dividend != null) {
					div.setLhs(dividend);
					return div;
				}
				// Otherwise, we need to restore state
				div.setLhs(combine.rhs);
				if (rightChild)
					this.setRhs(div);
				else
					this.setLhs(div);
			}
			
			// We need to find constants on both sides to perform constant combination.
			// Very similar logic to optimizations done in Addition, but altered to suit Multiplication
			// and Division
			NodeRef left = findConstant(lhs, false, false);
			if (left != null) {
				NodeRef right = findConstant(rhs, true, false);
				if (right != null) {
					// Collapse the constants, bringing from left to right
					// Both parents must be BinOp, since negation already ran its constant collapse optimization
					Constant rightConst;
					if (right.rightChild) {
						rightConst = (Constant)right.parent.rhs;
					}else
						rightConst = (Constant)((BinOp)right.parent).lhs;
					
					double leftConst = ((Constant)left.getNode()).val;
					boolean thisParent = !replaceParent((BinOp)left.parent, !left.rightChild);
					// Apply left to right
					if (left.reverse ^ right.reverse)
						rightConst.val /= leftConst;
					rightConst.val *= leftConst;
					
					if (thisParent) // if this was the parent of the left child, 
						return rhs; // then we need to replace it with the right
					optChanges = true;
				}
			}
			
			// We need to look through the children for any exponents
			// Exponents of the same base can be combined:
			//  x^y * x^z = x^(y+z)
			//  x^y / x^z = x^(y-z)
			//  y r x * x^z = x^(1/y + z)
			Valuable res = collapseExponents(this, s);
			if (res != null)
				return res;
			
			if (optChanges)
				return this;
			return null;
		}
		
		protected Valuable collapseExponents(BinOp start, ExpressionSolver s) {
			// Similarly to finding a constant, 
			// we can look through multiplications and divisions.
			// It is feasible for us to look through negations, but then we must
			// keep track of applying the negation(s) to the non-replaced child.
			// That is currently more effort than it is worth.
			
			List<NodeRef> exps = new ArrayList<>();
			collapseExponents(s, exps, start.lhs, false, false);
			// It is not possible for the start to need to be replaced since start
			// must be a BinOp, and its left arm is analyzed first, if the left arm
			// is an exponent, it would be amended rather than discarded. 
			boolean replaced = collapseExponents(s, exps, start.rhs, true, false);
			if (!replaced || start.lhs.parent != this) // if replacement failed
				return start.lhs; // must not pass rhs, which needs to be discarded
				
			return null;
		}
		protected boolean collapseExponents(ExpressionSolver s, List<NodeRef> exps,
				Valuable op, boolean rightChild, boolean negated) {
			if (op instanceof Multiplication) {
				Multiplication mult = (Multiplication)op;
				collapseExponents(s, exps, mult.lhs, false, negated);
				collapseExponents(s, exps, mult.rhs, true, negated);
			}else if (op instanceof Division) {
				Division div = (Division)op;
				collapseExponents(s, exps, div.lhs, false, negated);
				collapseExponents(s, exps, div.rhs, true, !negated);
			}else {
				// There is a subtle optimization available, since x = x^1.
				// Multiplication will generally be cheaper than exponentiation,
				// though, so we can combine if x matches the base of an
				// exponentiation, but we will not add x as an entry to keep
				// track of. Unfortunately, that adds a wrinkle to the optimization
				// process, since x^4*x -> x^5 but x*x^4 will not be combined.
				boolean firstPower = !(op instanceof Exponentiation || op instanceof Root);
				Valuable base = getBase(op);
				
				// Look through the list of logs to find if this base is already represented
				for (NodeRef ref: exps) {
					Valuable exp = ref.getNode();
					
					Valuable otherBase = getBase(exp);
					if (base.equals(otherBase)) {
						// Now we must combine op into the found other
						BinOp arg;
						if (ref.reverse == negated) // if both are the same, add
							arg = new Addition();
						else // otherwise, we must subtract
							arg = new Subtraction();
						arg.setLhs(getExp(exp));
						arg.setRhs(getExp(op));
						Valuable opted = arg.optimize(s);
						if (opted == null)
							opted = arg;
						// Now, set the exponent of the original to our new result:
						// We are met with some complications if the original is a root
						if (exp instanceof Root) {
							Root root = (Root)exp;
							// Replace exp with an Exponentiation
							Exponentiation replacement = new Exponentiation();
							replacement.setLhs(root.rhs); // base is root's rhs
							replacement.setRhs(opted);
							// Replace root in its parent
							if (ref.rightChild)
								ref.parent.setRhs(replacement);
							else
								((BinOp)ref.parent).setLhs(replacement);
						}else
							((Op)exp).setRhs(opted);
						
						// Now the fun part, where we discard op from the tree
						optChanges = true;
						return replaceParent((BinOp)op.parent, !rightChild);
					}
				}
				if (op instanceof Root) {
					// Give the root ownership of its left child again
					Root rt = (Root)op;
					rt.setLhs(rt.lhs); // inform lhs that rt is its parent
				}
				
				// If we did not find the base of this exponent, add this to the list
				if (!firstPower)
					exps.add(new NodeRef(op.parent, rightChild, negated));		
			}
			
			return true;
		}
		
		protected Valuable getBase(Valuable op) {
			if (op instanceof Exponentiation)
				return ((Exponentiation)op).lhs;
			else if (op instanceof Root)
				return ((Root)op).rhs;
			else
				return op;
		}
		
		protected Valuable getExp(Valuable op) {
			if (op instanceof Exponentiation) {
				return ((Exponentiation)op).rhs;
			}else if (op instanceof Root) {
				Root rt = (Root)op;
				// For example, 2 r x = sqrt(x)
				// 2 r x = x ^ 1/2
				Division div = new Division();
				div.setLhs(new Constant(1));
				div.setRhs(rt.lhs); // rt needs its left child back if not combined!
				return div;
			}else {
				return new Constant(1);
			}
		}
		
		protected boolean replaceParent(BinOp parent, boolean rightReplace) {
			Valuable replaceWith;
			if (rightReplace) {
				// We can run into complications if the replace with is inverse.
				if (parent instanceof Division) {
					Division inv = new Division();
					inv.setLhs(new Constant(1));
					inv.setRhs(parent.rhs);
					replaceWith = inv;
				}else
					replaceWith = parent.rhs;
			}else
				replaceWith = parent.lhs;
			
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
		
		@Override
		public String opString() {
			return "*";
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
		protected Valuable optimizeSpec(ExpressionSolver s) {
			// Check for some redundant operations:
			if (s.rational && lhs instanceof Constant && s.parse.equals(((Constant)lhs).val, 0))
				// If the constant is a 0, then this is constant 0
				return lhs;
			// Dividing by 1 is a redundant operation
			if (rhs instanceof Constant && s.parse.equals(((Constant)rhs).val, 1)) {
				return lhs; // no need for the divide
			}else if (rhs instanceof Division) {
				// x / y / z = xz / y
				Multiplication numerator = new Multiplication();
				numerator.setLhs(lhs); // x
				Division div = (Division)rhs;
				numerator.setRhs(div.rhs); // z
				Valuable numer = numerator.optimize(s);
				if (numer == null)
					numer = numerator;
				setLhs(numer);
				// denominator stays the same
				return this;
			}
			// If the numerator and denominator are equivalent, the result is 1
			if (lhs.equals(rhs))
				return new Constant(1);
			
			// Rely on multiplication's optimizations
			Multiplication useMul = new Multiplication();
			useMul.setLhs(lhs);
			// Children have already been optimized, so if rhs resolves to a constant,
			// it would be a constant by now
			Valuable got = null;
			boolean optInv = false; // We need to know if division optimized in order to restore state
			if (rhs instanceof Constant) {
				Constant con = (Constant)rhs;
				Valuable right = new Constant(1 / con.val);
				got = right.optimize(s);
				
				if (got == null) {
					got = right;
					optInv = false;
				}else
					optInv = true;
			}else {
				// Otherwise, we just create a multiplication by 1 / rhs
				// There is an issue with this definition, since it is a recursive optimization.
				// We avoid this by telling division that it is already optimized.
				Division r = new Division();
				r.setLhs(new Constant(1));
				r.setRhs(rhs);
				r.optimized = true;
				got = r;
				useMul.mayCollapseRightDiv = false; // may not collapse our convolution!
			}
			
			useMul.setRhs(got);
			Valuable opt = useMul.optimize(s);
			if (opt != null)
				return opt;
			if (optInv)
				return useMul;
			
			// If we could not use either, we need to restore the operands of this
			setLhs(lhs);
			setRhs(rhs);
			return null;
		}
		
		@Override
		public String opString() {
			return "/";
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
		protected Valuable optimizeSpec(ExpressionSolver s) {
			// Redundant exponentiation: x^1 = x
			if (rhs instanceof Constant) {
				Constant r = (Constant)rhs;
				if (s.parse.equals(r.val, 1))
					return lhs;
				else if (s.parse.equals(r.val, 0))
					return new Constant(1); // x^0 = 1
			}
			
			// If the base is an exponent-type, then we can multiply their exponents:
			// ie: (3^2)^2 = 3^(2*2) = 3^4
			if (lhs instanceof Exponentiation) {
				Multiplication exponent = new Multiplication();
				Exponentiation inner = (Exponentiation)lhs;
				exponent.setLhs(inner.rhs);
				exponent.setRhs(rhs);
				Valuable exp = exponent.optimize(s);
				if (exp == null)
					exp = exponent;
				inner.setRhs(exp);
				return inner;
			}else if (lhs instanceof Root) {
				Division exponent = new Division();
				Root inner = (Root)lhs;
				exponent.setRhs(inner.lhs);
				exponent.setLhs(rhs);
				Valuable exp = exponent.optimize(s);
				if (exp == null)
					exp = exponent;
				this.setLhs(inner.rhs);
				this.setRhs(exp);
				return this;
			}else
				return null;
		}
		
		@Override
		public String opString() {
			return "^";
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
		protected Valuable optimizeSpec(ExpressionSolver s) {
			// Reuse exponentiation's optimizations
			Exponentiation useExp = new Exponentiation();
			Division inv = new Division();
			inv.setLhs(new Constant(1));
			inv.setRhs(lhs);
			useExp.setLhs(rhs);
			Valuable base = inv.optimize(s);
			boolean optInv; // We need to know if division optimized in order to restore state
			if (base == null) {
				base = inv;
				optInv = false;
			}else
				optInv = true;
			
			useExp.setRhs(base);
			Valuable opt = useExp.optimize(s);
			// If an optimization was made, we use it instead of this
			if (opt != null)
				return opt;
			if (optInv)
				return useExp;
			
			// If we could not use either, we need to restore the operands of this
			setLhs(lhs);
			setRhs(rhs);
			return null;
		}
		
		@Override
		public String opString() {
			return "^";
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
		protected Valuable optimizeSpec(ExpressionSolver s) {
			// There is a possible optimization for min and max, but I think it would be pretty
			// obscure, so it may not be worth the time to check for it. The idea would be to
			// match the two arms, and if they are equivalent except for some constant, then we
			// can select the arm with the lowest (for min) / highest (for max) constant.
			return null;
		}
		
		@Override
		public String opString() {
			return "min";
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
		public String opString() {
			return "max";
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
		
		@Override
		public String opString() {
			return "cos";
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
		
		@Override
		public String opString() {
			return "sin";
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
		
		@Override
		public String opString() {
			return "tan";
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
		protected Valuable optimizeSpec(ExpressionSolver s) {
			// If the argument is an exponent or a root with the same base as
			// this, then we can pull out the exponent and disregard this log
			// and the base.
			Valuable myBase = lhs;
			Valuable otBase;
			Valuable exponent;
			Valuable modInside = null;
			if (rhs instanceof Exponentiation) {
				Exponentiation exp = (Exponentiation)rhs;
				otBase = exp.lhs;
				exponent = exp.rhs;
			}else if (rhs instanceof Root) {
				Root root = (Root)rhs;
				otBase = root.rhs;
				Division div = new Division();
				div.setLhs(new Constant(1));
				div.setRhs(root.lhs);
				modInside = div.optimize(s);
				if (modInside != null)
					exponent = modInside;
				else
					exponent = div;
			}else if (rhs instanceof Constant) {
				otBase = rhs;
				exponent = new Constant(1);
			}else
				return null;
			
			// If the outer base is equivalent to the nested base,
			// then the exponent is the answer
			if (myBase.equals(otBase))
				return exponent;
			else if (modInside != null) {
				// Repair the state as best as we can.
				Root root = (Root)rhs;
				Exponentiation newRhs = new Exponentiation();
				newRhs.setLhs(root.rhs);
				newRhs.setRhs(modInside);
				return this;
			}else
				return null;
		}
		
		@Override
		public String opString() {
			return "log";
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
		protected Valuable optimizeSpec(ExpressionSolver s) {
			Logarithm log = new Logarithm();
			log.setLhs(new Constant(Math.E));
			log.setRhs(this.rhs);
			Valuable got = log.optimize(s);
			if (got != null)
				return got;
			
			// Otherwise, restore the state of this
			setRhs(rhs); // remind rhs that this is its parent
			return null;
		}
		
		@Override
		public String opString() {
			return "ln";
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
		
		@Override
		public String opString() {
			return "round";
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
		
		@Override
		public String opString() {
			return "ceil";
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
		
		@Override
		public String opString() {
			return "floor";
		}
	}
	
	protected static class Constant extends Valuable {
		protected double val;
		protected double maxErr;
		
		public Constant(double val) {
			this.val = val;
			this.maxErr = 0.0001;
		}
		public Constant(double val, double maxErr) {
			this.val = val;
			this.maxErr = maxErr;
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
			return val + maxErr > other.val && other.val + maxErr > val;
		}
		
		@Override
		public String toString() {
			return Double.toString(val);
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
		
		@Override
		public String toString() {
			return name;
		}
	}
	
	protected boolean equals(double x, double y) {
		return x + maxErr > y && y + maxErr > x;
	}

}
