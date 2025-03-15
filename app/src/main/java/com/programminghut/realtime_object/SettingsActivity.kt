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

        // Load saved contact preference
        val savedContact = sharedPreferences.getString("selectedEmergencyContact", "")

        // Set up Emergency Contact Spinner
        val contactNames = emergencyContacts.keys.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, contactNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        contactSpinner.adapter = adapter

        if (savedContact != null && contactNames.contains(savedContact)) {
            contactSpinner.setSelection(contactNames.indexOf(savedContact))
        }

        // Save selected emergency contact
        saveButton.setOnClickListener {
            val selectedContact = contactNames[contactSpinner.selectedItemPosition]
            sharedPreferences.edit().putString("selectedEmergencyContact", selectedContact).apply()
            Toast.makeText(this, "Emergency contact saved: $selectedContact", Toast.LENGTH_SHORT).show()
        }
    }

    // Method to get the phone number of the selected contact
    fun getSelectedEmergencyNumber(): String? {
        val selectedContact = sharedPreferences.getString("selectedEmergencyContact", null)
        return emergencyContacts[selectedContact]
    }
}