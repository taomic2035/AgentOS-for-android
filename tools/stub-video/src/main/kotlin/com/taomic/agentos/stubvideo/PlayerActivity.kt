package com.taomic.agentos.stubvideo

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class PlayerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        val title = intent.getStringExtra(HomeActivity.EXTRA_TITLE).orEmpty()
        findViewById<TextView>(R.id.player_title).text = title
    }
}
