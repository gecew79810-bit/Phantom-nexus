package com.example.action.goal

import android.content.Context
import android.os.BatteryManager
import com.example.bridge.AndroidSystemBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.PriorityQueue

data class QueuedGoal(
    val goal: GoalDefinition,
    val nodes: List<TaskNode>,
    val onComplete: (TaskStepResult) -> Unit
) : Comparable<QueuedGoal> {
    override fun compareTo(other: QueuedGoal): Int {
        // Higher priority first
        return other.goal.priority.ordinal.compareTo(this.goal.priority.ordinal)
    }
}

class NexusTaskQueue(
    private val context: Context,
    private val executor: TaskDAGExecutor,
    private val systemBridge: AndroidSystemBridge,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val queue = PriorityQueue<QueuedGoal>()
    private var isProcessing = false

    @Synchronized
    fun enqueueGoal(
        goal: GoalDefinition,
        nodes: List<TaskNode>,
        onComplete: (TaskStepResult) -> Unit = {}
    ) {
        queue.add(QueuedGoal(goal, nodes, onComplete))
        processNext()
    }

    @Synchronized
    private fun processNext() {
        if (isProcessing || queue.isEmpty()) return
        isProcessing = true

        val next = queue.poll() ?: run {
            isProcessing = false
            return
        }

        coroutineScope.launch {
            try {
                // Resource awareness check
                val diag = systemBridge.getRealSystemDiagnostic()
                if (diag.batteryPercent <= 10 && !diag.isCharging && next.goal.priority != GoalPriority.CRITICAL) {
                    next.onComplete(
                        TaskStepResult(
                            nodeId = next.goal.goalId,
                            success = false,
                            message = "Task deferred: Battery critically low (${diag.batteryPercent}%). Connect charger to proceed."
                        )
                    )
                } else {
                    val result = executor.executeGraph(next.goal, next.nodes)
                    next.onComplete(result)
                }
            } finally {
                synchronized(this@NexusTaskQueue) {
                    isProcessing = false
                    processNext()
                }
            }
        }
    }

    @Synchronized
    fun clearQueue() {
        queue.clear()
    }

    @Synchronized
    fun getQueueSize(): Int = queue.size
}
