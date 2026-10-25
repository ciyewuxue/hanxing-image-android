package cn.hanxing.image

import android.app.Application

/** 应用级单例入口：引擎（含图片仓库）全进程只此一份，生成不随界面销毁而中断。 */
class HanxingApp : Application() {

    lateinit var engine: GenerationEngine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = GenerationEngine(this)
    }
}
