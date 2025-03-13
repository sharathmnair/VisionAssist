package com.programminghut.realtime_object

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val aboutTextView: TextView = findViewById(R.id.aboutTextView)
        aboutTextView.text = getString(R.string.about_text)
    }
}