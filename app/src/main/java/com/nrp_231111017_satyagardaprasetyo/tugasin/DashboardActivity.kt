package com.nrp_231111017_satyagardaprasetyo.tugasin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.Manifest
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors
import com.nrp_231111017_satyagardaprasetyo.tugasin.utils.TaskDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.jakewharton.threetenabp.AndroidThreeTen
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONObject

class DashboardActivity : AppCompatActivity() {

    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var rvTasks: RecyclerView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var dbHelper: TaskDatabaseHelper
    private lateinit var syncBtn: ImageButton
    private lateinit var adapter: TaskAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvLocation: TextView
    private val LOCATION_PERMISSION_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        DynamicColors.applyToActivitiesIfAvailable(application)

        AndroidThreeTen.init(this)

        // Hide action bar
        supportActionBar?.hide()

        // Initialize views
        tvTime = findViewById(R.id.tvTime)
        tvDate = findViewById(R.id.tvDate)
        rvTasks = findViewById(R.id.rvTasks)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        dbHelper = TaskDatabaseHelper(this)
        syncBtn = findViewById<ImageButton>(R.id.syncBtn)
        tvLocation = findViewById(R.id.tvLocation)

        updateTimeAndDate()

        updateTimeRunnable.run()

        setupBottomNavigation()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkLocationPermissionAndFetch()

        val loadingOverlay = findViewById<FrameLayout>(R.id.loadingOverlay)
        val tvNoTasksMessage = findViewById<TextView>(R.id.tvNoTasksMessage)

        val credentials = getSavedCredentials()
        if (credentials != null) {
            val (email, password) = credentials
            findViewById<TextView>(R.id.tvGreeting).text = "Halo, ${email.split("@")[0]}!"

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    loadingOverlay.visibility = View.VISIBLE
                    rvTasks.visibility = View.GONE
                    disableMenu()

                    val isSynced =
                        dbHelper.syncTasksIfNeeded(this@DashboardActivity, email, password)

                    Log.d(
                        "TASK_DEBUG",
                        if (isSynced) "Tasks synced successfully" else "No new tasks to sync"
                    )

                    val allTasks = dbHelper.getAllTasks(this@DashboardActivity)
                    Log.d("TASK_DEBUG", "Total tasks: ${allTasks.size}")

                    val filteredTasks = allTasks
                        .filter { it.taskType.equals("Recently overdue", ignoreCase = true) }
                        .toMutableList()
                    Log.d("TASK_DEBUG", "Filtered tasks: ${filteredTasks.size}")

                    withContext(Dispatchers.Main) {
                        if (filteredTasks.isEmpty()) {
                            tvNoTasksMessage.visibility = View.VISIBLE
                            rvTasks.visibility = View.GONE
                        } else {
                            adapter = TaskAdapter(
                                filteredTasks,
                                { task -> },
                                R.layout.item_task,
                                { url ->
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    startActivity(intent)
                                },
                                isDashboard = true
                            )
                            rvTasks.layoutManager = LinearLayoutManager(this@DashboardActivity)
                            rvTasks.adapter = adapter

                            rvTasks.visibility = View.VISIBLE
                            tvNoTasksMessage.visibility = View.GONE
                        }

                        loadingOverlay.visibility = View.GONE
                        enableMenu()
                    }
                } catch (e: Exception) {
                    Log.e("TASK_SYNC_ERROR", "Error syncing or loading tasks", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@DashboardActivity,
                            "Failed to load tasks",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadingOverlay.visibility = View.GONE
                        rvTasks.visibility = View.VISIBLE
                    }
                }
            }

            syncBtn.setOnClickListener {
                val alertDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Sync Tasks")
                    .setMessage("Are you sure you want to sync tasks?")
                    .setPositiveButton("Yes") { _, _ ->
                        loadingOverlay.visibility = View.VISIBLE
                        rvTasks.visibility = View.GONE
                        disableMenu()

                        lifecycleScope.launch {
                            try {
                                Log.d("TASK_SYNC", "Starting manual sync...")
                                val isSynced =
                                    dbHelper.syncTasksManual(
                                        this@DashboardActivity,
                                        email,
                                        password
                                    )

                                Log.d(
                                    "TASK_DEBUG",
                                    if (isSynced) "Tasks synced successfully" else "No new tasks to sync"
                                )

                                withContext(Dispatchers.Main) {
                                    loadingOverlay.visibility = View.GONE
                                    rvTasks.visibility = View.VISIBLE

                                    Toast.makeText(
                                        this@DashboardActivity,
                                        if (isSynced) "Tasks synced successfully" else "No new tasks to sync",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    if (isSynced) {
                                        val updatedTasks =
                                            dbHelper.getAllTasks(this@DashboardActivity)
                                                .filter {
                                                    it.taskType.equals(
                                                        "Recently overdue",
                                                        ignoreCase = true
                                                    )
                                                }
                                                .toMutableList()

                                        adapter.updateTasks(updatedTasks)
                                    }
                                }

                                enableMenu()
                            } catch (e: Exception) {
                                Log.e("TASK_SYNC_ERROR", "Error syncing tasks", e)
                                withContext(Dispatchers.Main) {
                                    loadingOverlay.visibility = View.GONE
                                    Toast.makeText(
                                        this@DashboardActivity,
                                        "Failed to sync tasks",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        // Dismiss the dialog
                        dialog.dismiss()
                    }
                    .create()

                alertDialog.show()
            }
        }

        val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
            1, TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ReminderWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
//
//        getLastLocation()
    }

    private fun checkLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            fetchLastLocation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            fetchLastLocation()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchLastLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val lat = location.latitude
                    val lng = location.longitude
                    Log.d("LL", "Lat: $lat, Lng: $lng")
                    fetchStreetNameFromMapbox(lat, lng)
                } else {
                    Log.w("Location", "Location is null. Try requesting updated location.")
                    tvLocation.text = "Location not available"
                }
            }
            .addOnFailureListener {
                Log.e("Location", "Failed to get location", it)
                tvLocation.text = "Failed to retrieve location"
            }
    }

    private fun fetchStreetNameFromMapbox(lat: Double, lng: Double) {
        val accessToken = getString(R.string.mapbox_access_token)
        val url =
            "https://api.mapbox.com/geocoding/v5/mapbox.places/$lng,$lat.json?access_token=$accessToken"

        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("MapboxGeocode", "Geocoding failed: ${e.message}")
                runOnUiThread {
                    tvLocation.text = "Error retrieving address"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    try {
                        val json = JSONObject(body ?: "")
                        val features = json.getJSONArray("features")
                        if (features.length() > 0) {
                            val placeName = features.getJSONObject(0).getString("place_name")
                            Log.d("MapboxGeocode", "Street Name: $placeName")
                            runOnUiThread {
                                tvLocation.text = placeName
                            }
                        } else {
                            runOnUiThread {
                                tvLocation.text = "Address not found"
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MapboxGeocode", "JSON Parsing Error", e)
                        runOnUiThread {
                            tvLocation.text = "Error parsing address"
                        }
                    }
                } else {
                    Log.e("MapboxGeocode", "Mapbox error: ${response.code}")
                    runOnUiThread {
                        tvLocation.text = "Mapbox error"
                    }
                }
            }
        })
    }

    private fun getLastLocation() {
        if (checkLocationPermission()) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val lat = location.latitude
                        val lng = location.longitude
                        val accessToken = getString(R.string.mapbox_access_token)

                        Log.d("LL", "Lat: $lat, Lng: $lng")

                        val url =
                            "https://api.mapbox.com/geocoding/v5/mapbox.places/$lng,$lat.json?access_token=$accessToken"

                        Log.d("MapboxURL", url)

                        val request = Request.Builder()
                            .url(url)
                            .build()

                        val client = OkHttpClient()
                        client.newCall(request).enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                Log.e("MapboxGeocode", "Failed: ${e.message}")
                            }

                            override fun onResponse(call: Call, response: Response) {
                                if (response.isSuccessful) {
                                    val body = response.body?.string()
                                    if (body != null) {
                                        try {
                                            val json = JSONObject(body)
                                            val features = json.getJSONArray("features")
                                            if (features.length() > 0) {
                                                val placeName = features.getJSONObject(0)
                                                    .getString("place_name")
                                                Log.d("MapboxGeocode", "Street Name: $placeName")

                                                runOnUiThread {
                                                    tvLocation.text = placeName
                                                }
                                            } else {
                                                Log.w("MapboxGeocode", "No features found")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("MapboxGeocode", "Parsing error", e)
                                        }
                                    } else {
                                        Log.w("MapboxGeocode", "Empty response body")
                                    }
                                } else {
                                    Log.e(
                                        "MapboxGeocode",
                                        "Unsuccessful response: ${response.code}"
                                    )
                                }
                            }
                        })
                    } else {
                        Log.e("Location", "Location is null")
                    }
                }
                .addOnFailureListener {
                    Log.e("Location", "Failed to get location", it)
                }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
        }
    }


    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateTimeAndDate() {
        val calendar = Calendar.getInstance()

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        tvTime.text = timeFormat.format(calendar.time)

        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
        tvDate.text = dateFormat.format(calendar.time)
    }

    private fun getSavedCredentials(): Pair<String, String>? {
        val db = dbHelper.readableDatabase
        val cursor = db.query("credentials", null, null, null, null, null, null)
        var result: Pair<String, String>? = null

        if (cursor.moveToFirst()) {
            val email = cursor.getString(cursor.getColumnIndexOrThrow("email"))
            val password = cursor.getString(cursor.getColumnIndexOrThrow("password"))
            result = Pair(email, password)
        }
        cursor.close()
        return result
    }

    private val handler = Handler(Looper.getMainLooper())
    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateTimeAndDate()
            handler.postDelayed(this, 1000)
        }
    }

    fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    overridePendingTransition(0, 0)
                    return@setOnItemSelectedListener true
                }

                R.id.navigation_tasks -> {
                    val intent = Intent(this, TasksActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    return@setOnItemSelectedListener true
                }

                R.id.navigation_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    overridePendingTransition(0, 0)
                    return@setOnItemSelectedListener true
                }

                else -> false
            }
        }

        // Set home as selected
        bottomNavigation.selectedItemId = R.id.navigation_home
    }

    fun disableMenu() {
        val menu = bottomNavigation.menu
        for (i in 0 until menu.size()) {
            menu.getItem(i).isEnabled = false
        }

        syncBtn.isEnabled = false
    }

    fun enableMenu() {
        val menu = bottomNavigation.menu
        for (i in 0 until menu.size()) {
            menu.getItem(i).isEnabled = true
        }

        syncBtn.isEnabled = true
    }
}