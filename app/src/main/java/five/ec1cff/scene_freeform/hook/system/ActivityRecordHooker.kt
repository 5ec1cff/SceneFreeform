package five.ec1cff.scene_freeform.hook.system

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.normalClass
import com.highcapable.yukihookapi.hook.log.loggerD
import java.lang.reflect.Field
import java.lang.reflect.Method

object ActivityRecordHooker : YukiBaseHooker() {
    lateinit var intentField: Field
    lateinit var infoField: Field
    lateinit var launchedFromPackageField: Field
    lateinit var resultToField: Field
    lateinit var mActivityComponentField: Field
    lateinit var resultWhoField: Field
    lateinit var requestCodeField: Field
    lateinit var isStateMethod: Method
    lateinit var sendResultMethod: Method
    override fun onHook() {
        "com.android.server.wm.ActivityRecord".hook {
            intentField = instanceClass.field { name = "intent" }.give()!!
            infoField = instanceClass.field { name = "info" }.give()!!
            launchedFromPackageField = instanceClass.field { name = "launchedFromPackage" }.give()!!
            resultToField = instanceClass.field { name = "resultTo" }.give()!!
            mActivityComponentField = instanceClass.field { name = "mActivityComponent" }.give()!!
            resultWhoField = instanceClass.field { name = "resultWho" }.give()!!
            requestCodeField = instanceClass.field { name = "requestCode" }.give()!!
            isStateMethod = instanceClass.method { name = "isState" }.give()!!
            sendResultMethod = instanceClass.method { name = "sendResult" }.give()!!
            val enumActivityStateRESUMED = findClass("com.android.server.wm.ActivityStack\$ActivityState")
                .normalClass!!.field { name = "RESUMED" }.get().self!!
            injectMember {
                method {
                    name = "finishActivityResults"
                }
                beforeHook {
                    if (!SystemServerHooker.needFixResult()) return@beforeHook
                    val fromRecord = ActivityRecord(instance)
                    val toRecord = fromRecord.resultTo ?: return@beforeHook
                    val isRedirect = FixActivityResultController.isRedirect(fromRecord, toRecord)
                    val isResumed = toRecord.isState(enumActivityStateRESUMED)
                    loggerD(msg = "fixResult:send ${fromRecord.hashCode()}->${toRecord.hashCode()} isRedirect=$isRedirect isResumed=$isResumed")
                    if (isRedirect && isResumed) {
                        val activityInfo = fromRecord.info
                        val callingUid = activityInfo.applicationInfo?.uid ?: 1000
                        val resultWho = fromRecord.resultWho
                        val requestCode = fromRecord.requestCode
                        val resultCode = args[0]
                        val data = args[1]
                        val dataGrants = args[2]
                        loggerD(msg = "fixResult:send ${fromRecord.hashCode()}->${toRecord.hashCode()}:callingUid=$callingUid,resultWho=$resultWho,requestCode=$requestCode,resultCode=$resultCode,data=$data,dataGrants=$dataGrants")
                        toRecord.sendResult(
                            callingUid,
                            resultWho,
                            requestCode,
                            resultCode,
                            data,
                            dataGrants
                        )
                        resultNull()
                    }
                    FixActivityResultController.removeRedirect(fromRecord)
                }
            }
        }
    }
}

/**
 * Wrapper for com.android.server.wm.ActivityRecord
 */
@JvmInline
value class ActivityRecord(private val mObject: Any) {
    /**
     * the original intent that generated us
     */
    val intent: Intent
        get() = ActivityRecordHooker.intentField.get(mObject) as Intent

    /**
     * activity info provided by developer in AndroidManifest
     */
    val info: ActivityInfo
        get() = ActivityRecordHooker.infoField.get(mObject) as ActivityInfo

    /**
     * always the package who started the activity.
     */
    val launchedFromPackage: String?
        get() = ActivityRecordHooker.launchedFromPackageField.get(mObject) as String?

    /**
     * who started this entry, so will get our reply
     */
    val resultTo: ActivityRecord?
        get() = ActivityRecordHooker.resultToField.get(mObject).asActivityRecord()

    /**
     * the intent component, or target of an alias.
     */
    val mActivityComponent: ComponentName?
        get() = ActivityRecordHooker.mActivityComponentField.get(mObject) as ComponentName?

    val resultWho: String?
        get() = ActivityRecordHooker.resultWhoField.get(mObject) as String?

    /**
     * code given by requester (resultTo)
     */
    val requestCode: Int
        get() = ActivityRecordHooker.requestCodeField.getInt(mObject)

    val instance: Any
        get() = mObject

    /**
     * Returns `true` if the Activity is in the specified state.
     */
    fun isState(state: Any): Boolean =
        ActivityRecordHooker.isStateMethod.invoke(mObject, state) as Boolean

    fun sendResult(vararg args: Any?): Any? =
        ActivityRecordHooker.sendResultMethod.invoke(mObject, *args)
}

fun Any?.asActivityRecord() = this?.let { ActivityRecord(it) }