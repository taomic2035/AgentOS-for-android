package com.taomic.agentos.stubvideo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 假视频 App 的主页，专为 AgentOS V0.1 / V0.6 e2e 测试设计：
 *  - 搜索框 (resource_id=search_field) + 搜索按钮 (resource_id=search_button)
 *  - 输入剧名后点搜索 → 列表显示匹配项
 *  - 列表项点击 → [PlayerActivity]，title 显示在 player_title TextView
 *
 * 关键设计：列表用 LinearLayout 平铺 TextView 并显式 setOnClickListener，
 * 而非 ListView+onItemClickListener。原因是 a11y ACTION_CLICK 走 view 的
 * OnClickListener 路径，ListView 的 onItemClick 不被触发。
 */
class HomeActivity : Activity() {

    private val library = listOf(
        "三体",
        "三体 第二部",
        "三国演义",
        "红楼梦",
        "西游记",
        "水浒传",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val field = findViewById<EditText>(R.id.search_field)
        val button = findViewById<Button>(R.id.search_button)
        val results = findViewById<LinearLayout>(R.id.results)
        val empty = findViewById<TextView>(R.id.empty_label)

        fun renderResults(hits: List<String>) {
            results.removeAllViews()
            empty.visibility = if (hits.isEmpty()) View.VISIBLE else View.GONE
            val padPx = (resources.displayMetrics.density * 16).toInt()
            for (title in hits) {
                val item = TextView(this).apply {
                    text = title
                    textSize = 18f
                    setPadding(padPx, padPx, padPx, padPx)
                    isClickable = true
                    setOnClickListener { openPlayer(title) }
                }
                results.addView(item)
            }
        }

        fun runSearch() {
            val q = field.text.toString().trim()
            renderResults(if (q.isEmpty()) library else library.filter { it.contains(q) })
        }

        button.setOnClickListener { runSearch() }
        field.setOnEditorActionListener { _, _, _ ->
            runSearch()
            true
        }

        // 首屏显示全部，便于直接 a11y dump 看节点
        renderResults(library)
    }

    private fun openPlayer(title: String) {
        startActivity(Intent(this, PlayerActivity::class.java).putExtra(EXTRA_TITLE, title))
    }

    companion object {
        const val EXTRA_TITLE: String = "title"
    }
}
