package com.fsck.k9

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.fsck.k9.activity.MessageList

class SplashActivity: Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, MessageList::class.java)
        startActivity(intent)
//        finish()
    }
}