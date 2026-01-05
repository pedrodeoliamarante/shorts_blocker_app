package com.shortsblocker.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var shieldIcon: ImageView
    private lateinit var statusIndicator: View
    private lateinit var statusTitle: TextView
    private lateinit var statusDescription: TextView
    private lateinit var actionButton: MaterialButton
    private lateinit var statusCard: CardView
    private lateinit var actionRadioGroup: RadioGroup
    private lateinit var actionBack: RadioButton
    private lateinit var actionHome: RadioButton
    private lateinit var actionRecents: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
        loadSettings()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun initViews() {
        shieldIcon = findViewById(R.id.shieldIcon)
        statusIndicator = findViewById(R.id.statusIndicator)
        statusTitle = findViewById(R.id.statusTitle)
        statusDescription = findViewById(R.id.statusDescription)
        actionButton = findViewById(R.id.openAccessibilityButton)
        statusCard = findViewById(R.id.statusCard)
        actionRadioGroup = findViewById(R.id.actionRadioGroup)
        actionBack = findViewById(R.id.actionBack)
        actionHome = findViewById(R.id.actionHome)
        actionRecents = findViewById(R.id.actionRecents)
    }

    private fun setupClickListeners() {
        actionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        statusCard.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        actionRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val action = when (checkedId) {
                R.id.actionBack -> AppPreferences.BlockAction.BACK
                R.id.actionHome -> AppPreferences.BlockAction.HOME
                R.id.actionRecents -> AppPreferences.BlockAction.RECENTS
                else -> AppPreferences.BlockAction.BACK
            }
            AppPreferences.setBlockAction(this, action)
        }
    }

    private fun loadSettings() {
        val currentAction = AppPreferences.getBlockAction(this)
        when (currentAction) {
            AppPreferences.BlockAction.BACK -> actionBack.isChecked = true
            AppPreferences.BlockAction.HOME -> actionHome.isChecked = true
            AppPreferences.BlockAction.RECENTS -> actionRecents.isChecked = true
        }
    }

    private fun updateServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled()

        if (isEnabled) {
            // Active state
            statusIndicator.setBackgroundResource(R.drawable.status_indicator_active)
            statusTitle.text = getString(R.string.status_active)
            statusDescription.text = getString(R.string.status_active_desc)
            actionButton.text = getString(R.string.open_settings)
            shieldIcon.setImageResource(R.drawable.ic_smiley_happy)
        } else {
            // Inactive state
            statusIndicator.setBackgroundResource(R.drawable.status_indicator)
            statusTitle.text = getString(R.string.status_inactive)
            statusDescription.text = getString(R.string.status_inactive_desc)
            actionButton.text = getString(R.string.enable_protection)
            shieldIcon.setImageResource(R.drawable.ic_smiley_sad)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName
        }
    }
}
