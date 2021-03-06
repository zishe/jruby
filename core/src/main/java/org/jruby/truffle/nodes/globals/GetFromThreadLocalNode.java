package org.jruby.truffle.nodes.globals;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;
import org.jruby.truffle.nodes.RubyNode;
import org.jruby.truffle.runtime.RubyContext;

/**
 * If a child node produces a {@link ThreadLocal}, get the value from it. If the value is not a {@code ThreadLocal},
 * return it unmodified.
 *
 * This is used in combination with nodes that read and writes from storage locations such as frames to make them
 * thread-local.
 *
 * Also see {@link WrapInThreadLocalNode}.
 */
@NodeChild(value = "value", type = RubyNode.class)
public abstract class GetFromThreadLocalNode extends RubyNode {

    public GetFromThreadLocalNode(RubyContext context, SourceSection sourceSection) {
        super(context, sourceSection);
    }

    public GetFromThreadLocalNode(GetFromThreadLocalNode prev) {
        super(prev);
    }

    @Specialization
    public Object get(ThreadLocal<?> threadLocal) {
        return threadLocal.get();
    }

    @Specialization(guards = "!isThreadLocal")
    public Object get(Object value) {
        return value;
    }

    public static Object get(RubyContext context, Object value) {
        if (value instanceof ThreadLocal) {
            return ((ThreadLocal<?>) value).get();
        } else {
            return value;
        }
    }


}
