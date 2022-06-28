package com.bonita.wirelesslan

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bonita.wirelesslan.databinding.ActivityMainBinding

/**
 * File Manager Activity
 *
 * @author bonita
 * @date 2021-11-17
 */
class MainActivity : AppCompatActivity() {

    private lateinit var fragment: WLanFragment

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fragment = WLanFragment()
        supportFragmentManager.beginTransaction()
            .replace(binding.frameMain.id, fragment, WLanFragment.FRAGMENT_TAG)
            .commit()
    }
}