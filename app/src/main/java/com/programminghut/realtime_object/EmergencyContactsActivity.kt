package com.programminghut.realtime_object

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class EmergencyContactsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_contacts)

        val contactsListView: ListView = findViewById(R.id.contactsListView)

        // Example contacts list, replace with dynamic data as needed
        val emergencyContacts = listOf(
            "Police: 100",
            "Fire Department: 101",
            "Ambulance: 102",
            "John Doe: +1234567890",
            "Jane Smith: +0987654321"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, emergencyContacts)
        contactsListView.adapter = adapter
    }
}