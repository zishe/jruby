package org.jruby.ir.instructions;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.ir.IRVisitor;
import org.jruby.ir.Operation;
import org.jruby.ir.operands.Operand;
import org.jruby.ir.operands.StringLiteral;
import org.jruby.ir.operands.UnboxedBoolean;
import org.jruby.ir.operands.Variable;
import org.jruby.ir.transformations.inlining.CloneInfo;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.opto.ConstantCache;
import org.jruby.runtime.opto.Invalidator;

import java.util.Map;

// Const search:
// - looks up lexical scopes
// - then inheritance hierarcy if lexical search fails
// - then invokes const_missing if inheritance search fails
public class SearchConstInstr extends Instr implements ResultInstr, FixedArityInstr {
    private Operand  startingScope;
    private final String   constName;
    private final boolean  noPrivateConsts;
    private Variable result;

    // Constant caching
    private volatile transient ConstantCache cache;

    public SearchConstInstr(Variable result, String constName, Operand startingScope, boolean noPrivateConsts) {
        super(Operation.SEARCH_CONST);

        assert result != null: "SearchConstInstr result is null";

        this.result          = result;
        this.constName       = constName;
        this.startingScope   = startingScope;
        this.noPrivateConsts = noPrivateConsts;
    }

    @Override
    public Operand[] getOperands() {
        return new Operand[] { new StringLiteral(constName), startingScope, new UnboxedBoolean(noPrivateConsts) };
    }

    @Override
    public void simplifyOperands(Map<Operand, Operand> valueMap, boolean force) {
        startingScope = startingScope.getSimplifiedOperand(valueMap, force);
    }

    @Override
    public Variable getResult() {
        return result;
    }

    @Override
    public void updateResult(Variable v) {
        this.result = v;
    }

    @Override
    public Instr clone(CloneInfo ii) {
        return new SearchConstInstr(ii.getRenamedVariable(result), constName, startingScope.cloneForInlining(ii), noPrivateConsts);
    }

    @Override
    public String toString() {
        return super.toString() + "(" + constName + ", " + startingScope + ", no-private-consts=" + noPrivateConsts + ")";
    }

    public ConstantCache getConstantCache() {
        return cache;
    }

    public Object cache(ThreadContext context, StaticScope currScope, DynamicScope currDynScope, IRubyObject self, Object[] temp) {
        // Lexical lookup
        Ruby runtime = context.getRuntime();
        RubyModule object = runtime.getObject();
        StaticScope staticScope = (StaticScope) startingScope.retrieve(context, self, currScope, currDynScope, temp);
        Object constant = (staticScope == null) ? object.getConstant(constName) : staticScope.getConstantInner(constName);

        // Inheritance lookup
        RubyModule module = null;
        if (constant == null) {
            // SSS FIXME: Is this null check case correct?
            module = staticScope == null ? object : staticScope.getModule();
            constant = noPrivateConsts ? module.getConstantFromNoConstMissing(constName, false) : module.getConstantNoConstMissing(constName);
        }

        // Call const_missing or cache
        if (constant == null) {
            constant = module.callMethod(context, "const_missing", runtime.fastNewSymbol(constName));
        } else {
            // recache
            Invalidator invalidator = runtime.getConstantInvalidator(constName);
            cache = new ConstantCache((IRubyObject)constant, invalidator.getData(), invalidator);
        }

        return constant;
    }

    public Object getCachedConst() {
        ConstantCache cache = this.cache;
        return cache == null ? null : cache.value;
    }

    @Override
    public Object interpret(ThreadContext context, StaticScope currScope, DynamicScope currDynScope, IRubyObject self, Object[] temp) {
        ConstantCache cache = this.cache;
        if (!ConstantCache.isCached(cache)) return cache(context, currScope, currDynScope, self, temp);

        return cache.value;
    }

    @Override
    public void visit(IRVisitor visitor) {
        visitor.SearchConstInstr(this);
    }

    public Operand getStartingScope() {
        return startingScope;
    }

    public String getConstName() {
        return constName;
    }

    public boolean isNoPrivateConsts() {
        return noPrivateConsts;
    }
}
