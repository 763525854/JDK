/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */


package org.graalvm.compiler.lir.sparc;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.framemap.FrameMap;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.ValueKind;
import jdk.vm.ci.sparc.SPARC;
import jdk.vm.ci.sparc.SPARCKind;

/**
 * SPARC specific frame map.
 *
 * This is the format of a SPARC stack frame:
 *
 * <pre>
 *   Base       Contents
 *            :                                :  -----
 *   caller   | incoming overflow argument n   |    ^
 *   frame    :     ...                        :    | positive
 *            | incoming overflow argument 0   |    | offsets
 *            +--------------------------------+    |
 *            |                                |    |
 *            : register save area             :    |
 *            |                                |    |
 *   ---------+--------------------------------+---------------------------
 *            | spill slot 0                   |    | negative   ^      ^
 *            :     ...                        :    v offsets    |      |
 *            | spill slot n                   |  -----        total    |
 *            +--------------------------------+               frame    |
 *   current  | alignment padding              |               size     |
 *   frame    +--------------------------------+  -----          |      |
 *            | outgoing overflow argument n   |    ^            |    frame
 *            :     ...                        :    | positive   |    size
 *            | outgoing overflow argument 0   |    | offsets    |      |
 *            +--------------------------------+    |            |      |
 *            | return address                 |    |            |      |
 *            +--------------------------------+    |            |      |
 *            |                                |    |            |      |
 *            : callee save area               :    |            |      |
 *            |                                |    |            v      v
 *    %sp--&gt;  +--------------------------------+---------------------------
 *
 * </pre>
 *
 * The spill slot area also includes stack allocated memory blocks (ALLOCA blocks). The size of such
 * a block may be greater than the size of a normal spill slot or the word size.
 * <p>
 * A runtime can reserve space at the beginning of the overflow argument area. The calling
 * convention can specify that the first overflow stack argument is not at offset 0, but at a
 * specified offset. Use {@link CodeCacheProvider#getMinimumOutgoingSize()} to make sure that
 * call-free methods also have this space reserved. Then the VM can use the memory at offset 0
 * relative to the stack pointer.
 */
public final class SPARCFrameMap extends FrameMap {

    public SPARCFrameMap(CodeCacheProvider codeCache, RegisterConfig registerConfig, ReferenceMapBuilderFactory referenceMapFactory) {
        super(codeCache, registerConfig, referenceMapFactory);
        // Initial spill size is set to register save area size (SPARC register window)
        initialSpillSize = 0;
        spillSize = initialSpillSize;
    }

    @Override
    public int totalFrameSize() {
        return frameSize();
    }

    @Override
    public int currentFrameSize() {
        return alignFrameSize(SPARC.REGISTER_SAFE_AREA_SIZE + outgoingSize + spillSize);
    }

    /**
     * In SPARC we have spill slots word aligned.
     */
    @Override
    public int spillSlotSize(ValueKind<?> kind) {
        return kind.getPlatformKind().getSizeInBytes();
    }

    @Override
    public int offsetForStackSlot(StackSlot slot) {
        // @formatter:off
        assert (!slot.getRawAddFrameSize() && slot.getRawOffset() <  outgoingSize + SPARC.REGISTER_SAFE_AREA_SIZE) ||
               (slot.getRawAddFrameSize() && slot.getRawOffset()  <  0 && -slot.getRawOffset() <= spillSize) ||
               (slot.getRawAddFrameSize() && slot.getRawOffset()  >= 0) :
                   String.format("RawAddFrameSize: %b RawOffset: 0x%x spillSize: 0x%x outgoingSize: 0x%x", slot.getRawAddFrameSize(), slot.getRawOffset(), spillSize, outgoingSize);
        // @formatter:on
        return super.offsetForStackSlot(slot);
    }

    @Override
    public boolean frameNeedsAllocating() {
        return super.frameNeedsAllocating() || spillSize > 0;
    }

    public StackSlot allocateDeoptimizationRescueSlot() {
        assert spillSize == initialSpillSize : "Deoptimization rescue slot must be the first stack slot";
        return allocateSpillSlot(LIRKind.value(SPARCKind.XWORD));
    }
}
