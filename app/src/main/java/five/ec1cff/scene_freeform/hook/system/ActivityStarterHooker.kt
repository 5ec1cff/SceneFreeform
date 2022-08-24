package five.ec1cff.scene_freeform.hook.system

import android.app.ActivityOptions
import android.content.Intent
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.log.loggerD
import five.ec1cff.scene_freeform.hook.FreeformOptionsHelper.injectFreeformOptions
import java.lang.reflect.Field

object ActivityStarterHooker : YukiBaseHooker() {
    lateinit var mStartActivityField: Field
    override fun onHook() {
        "com.android.server.wm.ActivityStarter".hook {
            mStartActivityField = instanceClass.field { name = "mStartActivity" }.give()!!
            injectMember {
                method {
                    name = "startActivityInner"
                }
                beforeHook {
                    val record = ActivityRecord(args[0]!!)
                    val sourceRecord = args[1].asActivityRecord()
                    val flags = args[4] as Int
                    val options = args[6] as ActivityOptions?
                    if (SystemServerHooker.shouldInject(
                            record.intent,
                            record.mActivityComponent!!,
                            record.launchedFromPackage,
                            record
                    )) {
                        args(4).set(flags or Intent.FLAG_ACTIVITY_NEW_TASK)
                        args(6).set(options.injectFreeformOptions())
                    }
                    // TODO: it seems useless now
                    val resultTo = record.resultTo ?: return@beforeHook
                    if (SystemServerHooker.needFixResult()
                        && flags and Intent.FLAG_ACTIVITY_FORWARD_RESULT != 0
                        && sourceRecord != null
                    ) {
                        if (FixActivityResultController.replaceRedirect(sourceRecord, record, resultTo))
                            loggerD(msg = "fixResult:replace ${sourceRecord.hashCode()} -> ${record.hashCode()} (${resultTo.hashCode()})")
                    }
                }
            }
            injectMember {
                method {
                    name = "sendNewTaskResultRequestIfNeeded"
                    emptyParam()
                }
                beforeHook {
                    if (!SystemServerHooker.needFixResult()) return@beforeHook
                    val record = ActivityStarter(instance).mStartActivity ?: return@beforeHook
                    if (FixActivityResultController.getForceNewTask(record) != null) {
                        loggerD(msg = "fixResult:passNewTaskResult ${record.hashCode()}")
                        resultNull()
                    }
                }
            }
            injectMember {
                method {
                    name = "computeTargetTask"
                    emptyParam()
                }
                beforeHook {
                    if (!SystemServerHooker.needFixResult()) return@beforeHook
                    val record = ActivityStarter(instance).mStartActivity ?: return@beforeHook
                    val resultTo = record.resultTo ?: return@beforeHook
                    val needRedirectResult = FixActivityResultController.removeForceNewTask(record) ?: return@beforeHook
                    loggerD(msg = "fixResult:createNewTask ${record.hashCode()}")
                    if (needRedirectResult) {
                        FixActivityResultController.setRedirect(record, resultTo)
                        loggerD(msg = "fixResult:setRedirect ${record.hashCode()} (${resultTo.hashCode()})")
                    }
                    resultNull()
                }
            }
        }
    }
}

@JvmInline
value class ActivityStarter(private val mObject: Any) {
    val mStartActivity: ActivityRecord?
        get() = ActivityStarterHooker.mStartActivityField.get(mObject).asActivityRecord()
}