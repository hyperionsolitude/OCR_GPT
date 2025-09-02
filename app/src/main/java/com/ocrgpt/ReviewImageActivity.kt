package com.ocrgpt

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import android.widget.Button

class ReviewImageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review_image)
        
        val imageView = findViewById<ImageView>(R.id.review_image_view)
        val closeButton = findViewById<Button>(R.id.close_button)
        val imageUriString = intent.getStringExtra("image_uri")
        
        imageUriString?.let { uriString ->
            val uri = Uri.parse(uriString)
            imageView.setImageURI(uri)
        }
        
        closeButton.setOnClickListener { finish() }
    }
} 