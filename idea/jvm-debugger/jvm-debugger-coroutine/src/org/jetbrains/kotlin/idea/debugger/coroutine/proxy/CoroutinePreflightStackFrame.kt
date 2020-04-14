/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.engine.JVMStackFrameInfoProvider
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.ui.impl.watch.MethodsTracker
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValueChildrenList
import org.jetbrains.kotlin.idea.debugger.coroutine.coroutineDebuggerTraceEnabled
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.util.formatLocation
import org.jetbrains.kotlin.idea.debugger.safeLineNumber
import org.jetbrains.kotlin.idea.debugger.safeLocation
import org.jetbrains.kotlin.idea.debugger.safeMethod
import org.jetbrains.kotlin.idea.debugger.stackFrame.KotlinStackFrame

/**
 * Coroutine exit frame represented by a stack frames
 * invokeSuspend():-1
 * resumeWith()
 *
 */

class CoroutinePreflightStackFrame(
    val coroutineInfoData: CoroutineInfoData,
    val stackFrameDescriptorImpl: StackFrameDescriptorImpl,
    val threadPreCoroutineFrames: List<StackFrameProxyImpl>,
) : KotlinStackFrame(stackFrameDescriptorImpl), JVMStackFrameInfoProvider {

    override fun computeChildren(node: XCompositeNode) {
        val childrenList = XValueChildrenList()
        val firstRestoredCoroutineStackFrameItem = coroutineInfoData.stackTrace.firstOrNull() ?: return
        firstRestoredCoroutineStackFrameItem.spilledVariables.forEach {
            childrenList.add(it)
        } // firstRestoredCoroutineStackFrameItem should be skipped later on
        node.addChildren(childrenList, false)
        super.computeChildren(node)
    }

    override fun isInLibraryContent() =
        false

    override fun isSynthetic() =
        false

    companion object {
        fun preflight(
            invokeSuspendFrame: StackFrameProxyImpl,
            coroutineInfoData: CoroutineInfoData,
            originalFrames: List<StackFrameProxyImpl>
        ): CoroutinePreflightStackFrame? {
            val descriptor = createTopDescriptor(invokeSuspendFrame, coroutineInfoData)
            return CoroutinePreflightStackFrame(
                coroutineInfoData,
                descriptor,
                originalFrames
            )
        }

        private fun createTopDescriptor(
            frame: StackFrameProxyImpl,
            coroutineInfoData: CoroutineInfoData
        ): StackFrameDescriptorImpl {
            if (!coroutineInfoData.stackTrace.isEmpty()) {
                dumpFrames(frame, coroutineInfoData)
                val restoredFrame = coroutineInfoData.stackTrace.get(0)
                val descriptor = StackFrameDescriptorImpl(
                    LocationStackFrameProxyImpl(restoredFrame.location, frame), MethodsTracker()
                )
                return descriptor
            } else {
                return StackFrameDescriptorImpl(frame, MethodsTracker())
            }
        }

        private fun dumpFrames(
            frame: StackFrameProxyImpl,
            coroutineInfoData: CoroutineInfoData
        ) {
            if (coroutineDebuggerTraceEnabled()) {
                println("Real frame: " + formatLocation(frame.location()))
                for (f in coroutineInfoData.stackTrace) {
                    println("\trestored: " + formatLocation(coroutineInfoData.stackTrace.get(0).location))
                }
            }
        }

        private fun filterNegativeLineNumberInvokeSuspendFrames(frame: StackFrameProxyImpl): Boolean {
            val method = frame.safeLocation()?.safeMethod() ?: return false
            return method.isInvokeSuspend() && frame.safeLocation()?.safeLineNumber() ?: 0 < 0
        }
    }
}