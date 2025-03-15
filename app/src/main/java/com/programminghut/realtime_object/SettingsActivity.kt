package com.programminghut.realtime_object

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val emergencyContacts = mapOf(
        "Sharath M Nair" to "8156944286",
        "Police" to "100",
        "Fire Department" to "101",
        "Ambulance" to "102",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        val contactSpinner: Spinner = findViewById(R.id.contactSpinner)
        val saveButton: Button = findViewById(R.id.saveButton)

        // Load saved contact number
        val savedNumber = sharedPreferences.getString("selectedEmergencyContact", "")

        // Set up Emergency Contact Spinner
        val contactNames = emergencyContacts.keys.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, contactNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        contactSpinner.adapter = adapter

        // Set the spinner to the saved contact name if it exists
        if (savedNumber != null) {
            val savedContactName = emergencyContacts.entries.find { it.value == savedNumber }?.key
            if (savedContactName != null) {
                contactSpinner.setSelection(contactNames.indexOf(savedContactName))
            }
        }

        // Save selected emergency contact's phone number
        saveButton.setOnClickListener {
            val selectedContactName = contactNames[contactSpinner.selectedItemPosition]
            val selectedContactNumber = emergencyContacts[selectedContactName]

            if (selectedContactNumber != null) {
                sharedPreferences.edit().putString("selectedEmergencyContact", selectedContactNumber).apply()
                Toast.makeText(this, "Emergency contact saved: $selectedContactName", Toast.LENGTH_SHORT).show()
            }
        }
    }
}