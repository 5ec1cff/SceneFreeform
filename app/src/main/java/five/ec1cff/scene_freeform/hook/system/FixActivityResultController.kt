package five.ec1cff.scene_freeform.hook.system

import java.lang.ref.WeakReference
import java.util.*

object FixActivityResultController {
    /**
     * ActivityRecord -> needRedirectResult
     */
    private val mForceNewTaskActivities = WeakHashMap<Any, Boolean>()

    /**
     * ActivityRecord -> it.resultTo
     */
    private val mRedirectResultActivities =
        WeakHashMap<Any, WeakReference<Any>>()

    fun addForceNewTask(
        a: ActivityRecord,
        needRedirect: Boolean
    ) = synchronized(mForceNewTaskActivities) {
        mForceNewTaskActivities[a.instance] = needRedirect
    }

    fun getForceNewTask(a: ActivityRecord) = synchronized(mForceNewTaskActivities) {
        return@synchronized mForceNewTaskActivities[a.instance]
    }

    fun removeForceNewTask(a: ActivityRecord) = synchronized(mForceNewTaskActivities) {
        mForceNewTaskActivities.remove(a.instance)
    }

    fun setRedirect(
        from: ActivityRecord,
        to: ActivityRecord
    ) = synchronized(mRedirectResultActivities) {
        mRedirectResultActivities[from.instance] = WeakReference(to.instance)
    }

    fun isRedirect(
        from: ActivityRecord,
        to: ActivityRecord
    ) = synchronized(mRedirectResultActivities) {
        return@synchronized mRedirectResultActivities[from.instance]?.get() == to.instance
    }

    fun removeRedirect(from: ActivityRecord) = synchronized(mRedirectResultActivities) {
        mRedirectResultActivities.remove(from.instance)
    }

    fun replaceRedirect(
        origFrom: ActivityRecord,
        newFrom: ActivityRecord,
        to: ActivityRecord
    ): Boolean = synchronized(mRedirectResultActivities) {
        val realTo = mRedirectResultActivities[origFrom.instance]?.get() ?: return@synchronized false
        if (realTo != to.instance) return@synchronized false
        mRedirectResultActivities[newFrom.instance] = mRedirectResultActivities.remove(origFrom.instance)
        return@synchronized true
    }
}
