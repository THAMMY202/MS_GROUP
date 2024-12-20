package com.sos.msgroup.ui.home

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context.LOCATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.Toast

import androidx.fragment.app.Fragment
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.sos.msgroup.MonitorMeActivity
import com.sos.msgroup.R
import com.sos.msgroup.RegisterActivity
import com.sos.msgroup.databinding.FragmentHomeBinding
import com.sos.msgroup.model.HelpNotification
import com.sos.msgroup.model.SecurityGuard
import com.sos.msgroup.model.User
import com.sos.msgroup.notification.MyFirebaseMessagingService


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private val binding get() = _binding!!

    private lateinit var database: DatabaseReference
    private lateinit var currentUser: User
    private lateinit var request: HelpNotification
    private lateinit var userCurrentLocation: Location
    private val locationPermissionCode = 2

    // inside a basic activity
    private var locationManager: LocationManager? = null

    private lateinit var switchSecurityStatus: Switch
    private lateinit var security : SecurityGuard
    private lateinit var intiPanicType : String

    @SuppressLint("MissingPermission")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {

        initializeDbRef()
         activity?.let { MobileAds.initialize(it){} }

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val helpImage: Button = binding.btnCallHelp
        val cancelHelpImage: Button = binding.btnCancelHelp
        val monitorMeImage: Button = binding.btnMonitorMe

        loadBanner()


        //initialize location provides
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        locationManager = requireActivity().getSystemService(LOCATION_SERVICE) as LocationManager?

        requestLocation()

        helpImage.setOnClickListener { view ->

            try {
                if (this::currentUser.isInitialized && currentUser != null) {

                    if (this::userCurrentLocation.isInitialized && userCurrentLocation != null) {
                        //startPosting(helpImage, cancelHelpImage, view)
                        showPanicTypes(helpImage, cancelHelpImage, view)
                    } else {

                        requestLocation()

                        if (this::userCurrentLocation.isInitialized && userCurrentLocation != null) {

                            showPanicTypes(helpImage, cancelHelpImage, view)

                        } else {
                            mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                                if (location != null) {
                                    userCurrentLocation = location
                                    //startPosting(helpImage, cancelHelpImage, view)
                                    showPanicTypes(helpImage, cancelHelpImage, view)
                                } else {
                                    displayMessage(
                                        "We can't pick up your location,move around and try", view
                                    )
                                }
                            }
                        }


                    }
                }
            } catch (e: Exception) {
                showMsg(e.toString())
            }

        }

        cancelHelpImage.setOnClickListener { view ->

            cancelHelpRequest("Panic canceled", view)
            helpImage.visibility = View.VISIBLE
            cancelHelpImage.visibility = View.GONE
        }

        monitorMeImage.setOnClickListener { view ->
            goToMonitorMe(view)
        }

        switchSecurityStatus = binding.switchSecurityStatus

        switchSecurityStatus?.setOnCheckedChangeListener { _, isChecked ->
            var message = if (isChecked) "Offline ? " else "Online ?"
            switchSecurityStatus.text = message


            if(isChecked){
                security = SecurityGuard (currentUser.id,userCurrentLocation.latitude.toString(),userCurrentLocation.longitude.toString())
                postOnLineSecurityLocation(security)
            }else{
                deleteOffLineSecurityLocation(security)
            }

        }

        return root
    }

    private fun goToMonitorMe(view: View) {
        val intent = Intent (this@HomeFragment.context, MonitorMeActivity::class.java)
        startActivity(intent)
    }

    private fun loadBanner() {
        // This is an ad unit ID for a test ad. Replace with your own banner ad unit ID.
        //  binding.adView.adUnitId = "ca-app-pub-3940256099942544/9214589741"
        // binding.adView.size

        // Create an ad request.
        val adRequest = AdRequest.Builder().build()

        // Start loading the ad in the background.
        binding.adView.loadAd(adRequest)
    }


    private fun requestLocation() {
        try {
            // Request location updates
            locationManager?.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 0L, 0f, locationListener
            )
        } catch (ex: SecurityException) {
            showMsg(ex.toString())
        }
    }

    private fun startPosting(helpImage: Button, cancelHelpImage: Button, view: View) {

        if (currentUser.canPanic) {
            helpImage.visibility = View.GONE
            cancelHelpImage.visibility = View.VISIBLE
            displayMessage("Help is on the way", view)

            sendNewHelpRequest(
                currentUser.id,
                currentUser.firstName,
                currentUser.lastName,
                userCurrentLocation.latitude.toString(),
                userCurrentLocation.longitude.toString(),
                database.push().key.toString(),
                true,
                System.currentTimeMillis().toString(),
                "I need help urgent",
                currentUser.profileImage,
                intiPanicType
            )
        } else {
            displayMessage("You have not made your monthly payment", view)
        }

    }

    //private fun showPanicTypes(helpImage: ImageView, cancelHelpImage: ImageView, view: View) {
    private fun showPanicTypes(helpImage: Button, cancelHelpImage: Button, view: View) {
        // Set up the alert builder
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Choose Emergency Type")
        builder.setCancelable(false)

        // Add a checkbox list
        val animals = arrayOf("Security/Police", "Ambulance", "Roadside Assistance", "Fire")
        val checkedItems = booleanArrayOf(false, false, false, false, false)
        builder.setMultiChoiceItems(animals, checkedItems) { dialog, which, isChecked ->
            // The user checked or unchecked a box
            intiPanicType = animals[which]
        }

         // Add OK and Cancel buttons
        builder.setPositiveButton("OK") { dialog, which ->
            // The user clicked OK
            startPosting(helpImage, cancelHelpImage, view)
        }
        builder.setNegativeButton("Cancel", null)

        // Create and show the alert dialog
        val dialog = builder.create()
        dialog.show()
    }

    private fun getCurrentUserDetails() {

        var myRef: DatabaseReference = database.child("users").child(FirebaseAuth.getInstance().uid.toString())
        myRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {

                val user = dataSnapshot.getValue(User::class.java)

                if (user != null) {
                    currentUser = user

                    if(currentUser.type.lowercase() == "security" || currentUser.type.lowercase() == "admin" ){
                        switchSecurityStatus.visibility = View.VISIBLE
                    }else{
                        switchSecurityStatus.visibility = View.GONE
                    }

                } else {
                    showMsg("Unknown user")
                    return
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showMsg(error.toString())
            }
        })
    }

    private fun initializeDbRef() {
        database = FirebaseDatabase.getInstance().reference
        getCurrentUserDetails()
    }

    private fun sendNewHelpRequest(
        userId: String,
        firstName: String,
        lastName: String,
        latitude: String,
        longitude: String,
        requestId: String,
        isActive: Boolean,
        time: String,
        comment: String,
        userProlePic: String,
        panicType: String,
    ) {
        request = HelpNotification(
            userId,
            firstName,
            lastName,
            latitude,
            longitude,
            requestId,
            isActive,
            time,
            comment,
            false,
            userProlePic,
            panicType
        )
        database.child("requests").child(request.requestId).setValue(request).addOnSuccessListener {
            postNotification()
        }.addOnFailureListener {
            showMsg(it.toString())
        }
    }

    private fun cancelHelpRequest(message: String, view: View) {
        try {
            if (request != null) {
                if (this::request.isInitialized) {
                    request.isActive = false
                    database.child("requests").child(request.requestId).setValue(request)
                        .addOnSuccessListener {
                            displayMessage("Panic request cancelled", view)
                        }.addOnFailureListener {
                            showMsg(it.toString())
                        }
                } else {
                    displayMessage("On request to cancel", view)
                }
            }

        } catch (e: Exception) {
            displayMessage(e.toString(), view)
        }

    }

    //define the listener
    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            userCurrentLocation = location
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun deleteOffLineSecurityLocation(security:SecurityGuard){
        database.child("onLineSecurities").child(security.userID).removeValue().addOnSuccessListener {

        }.addOnFailureListener {
            showMsg(it.toString())
        }
    }

    private fun postOnLineSecurityLocation(security:SecurityGuard){
        database.child("onLineSecurities").child(security.userID).setValue(security).addOnSuccessListener {

        }.addOnFailureListener {
            showMsg(it.toString())
        }
    }

    private fun postNotification() {
        var topic = "_help_request"
        var title = getString(R.string.notification_title)
        var content = getString(R.string.notification_description)

        MyFirebaseMessagingService.sendMessage(title, content, topic)
    }

    private fun showMsg(msg: String) {
        val toast = Toast.makeText(activity, msg, Toast.LENGTH_LONG)
        toast.show()
    }

    private fun displayMessage(message: String, view: View) {
        if (this::request.isInitialized) {
            Snackbar.make(view, "Hi ${request.firstName} , $message", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        } else {
            Snackbar.make(view, "Hi , $message", Snackbar.LENGTH_LONG).setAction("Action", null)
                .show()
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showMsg("Permission Granted")
            } else {
                showMsg("Permission Denied")
            }
        }
    }

}