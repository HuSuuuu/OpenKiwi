package com.orizon.openkiwi.core.gui

object AppPackages {
    val packages: Map<String, String> = mapOf(
        "微信" to "com.tencent.mm", "WeChat" to "com.tencent.mm",
        "QQ" to "com.tencent.mobileqq", "微博" to "com.sina.weibo",
        "Telegram" to "org.telegram.messenger", "WhatsApp" to "com.whatsapp",
        "淘宝" to "com.taobao.taobao", "京东" to "com.jingdong.app.mall",
        "拼多多" to "com.xunmeng.pinduoduo", "美团" to "com.sankuai.meituan",
        "饿了么" to "me.ele", "百度" to "com.baidu.searchbox",
        "百度搜索" to "com.baidu.searchbox", "百度地图" to "com.baidu.BaiduMap",
        "百度网盘" to "com.baidu.netdisk", "高德" to "com.autonavi.minimap",
        "高德地图" to "com.autonavi.minimap", "大众点评" to "com.dianping.v1",
        "滴滴" to "com.sdu.didi.psnger", "滴滴出行" to "com.sdu.didi.psnger",
        "携程" to "ctrip.android.view", "12306" to "com.MobileTicket",
        "铁路12306" to "com.MobileTicket", "支付宝" to "com.eg.android.AlipayGphone",
        "小红书" to "com.xingin.xhs", "知乎" to "com.zhihu.android",
        "豆瓣" to "com.douban.frodo", "抖音" to "com.ss.android.ugc.aweme",
        "TikTok" to "com.zhiliaoapp.musically", "bilibili" to "tv.danmaku.bili",
        "B站" to "tv.danmaku.bili", "哔哩哔哩" to "tv.danmaku.bili",
        "快手" to "com.smile.gifmaker", "腾讯视频" to "com.tencent.qqlive",
        "爱奇艺" to "com.qiyi.video", "优酷" to "com.youku.phone",
        "芒果TV" to "com.hunantv.imgo.activity",
        "网易云" to "com.netease.cloudmusic", "网易云音乐" to "com.netease.cloudmusic",
        "QQ音乐" to "com.tencent.qqmusic", "酷狗" to "com.kugou.android",
        "酷狗音乐" to "com.kugou.android", "喜马拉雅" to "com.ximalaya.ting.android",
        "浏览器" to "com.android.browser", "Chrome" to "com.android.chrome",
        "夸克" to "com.quark.browser", "UC" to "com.UCMobile",
        "Gmail" to "com.google.android.gm", "谷歌地图" to "com.google.android.apps.maps",
        "设置" to "com.android.settings", "Settings" to "com.android.settings",
        "相机" to "com.android.camera", "Camera" to "com.android.camera",
        "相册" to "com.android.gallery", "图库" to "com.android.gallery",
        "时钟" to "com.android.deskclock", "闹钟" to "com.android.deskclock",
        "日历" to "com.android.calendar", "计算器" to "com.android.calculator2",
        "飞书" to "com.ss.android.lark", "钉钉" to "com.alibaba.android.rimet",
        "企业微信" to "com.tencent.wework", "WPS" to "cn.wps.moffice_eng",
        "豆包" to "com.larus.nova", "Doubao" to "com.larus.nova",
        "番茄小说" to "com.dragon.read", "今日头条" to "com.ss.android.article.news",
        "头条" to "com.ss.android.article.news", "腾讯新闻" to "com.tencent.news",
        "第五人格" to "com.netease.dwrg", "王者荣耀" to "com.tencent.tmgp.sgame",
        "王者" to "com.tencent.tmgp.sgame", "和平精英" to "com.tencent.tmgp.pubgmhd",
        "吃鸡" to "com.tencent.tmgp.pubgmhd", "原神" to "com.miHoYo.Yuanshen",
        "崩坏星穹铁道" to "com.miHoYo.hkrpg", "星铁" to "com.miHoYo.hkrpg",
        "崩坏3" to "com.miHoYo.bh3", "明日方舟" to "com.hypergryph.arknights",
        "方舟" to "com.hypergryph.arknights", "阴阳师" to "com.netease.onmyoji",
        "闲鱼" to "com.taobao.idlefish"
    )

    fun getPackageName(appName: String): String? {
        val input = appName.trim()
        packages[input]?.let { return it }
        packages.entries.find { it.key.equals(input, ignoreCase = true) }?.let { return it.value }
        val matches = packages.entries.filter { input.contains(it.key, ignoreCase = true) }.sortedByDescending { it.key.length }
        if (matches.isNotEmpty()) return matches.first().value
        return null
    }

    fun getAppName(packageName: String): String? = packages.entries.find { it.value == packageName }?.key
}
