/*
 * Copyright (c) 2021 -Srihari Vishnu - All Rights Reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF
 * OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package com.magnitudestudios.GameFace.network


import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.magnitudestudios.GameFace.Constants
import com.magnitudestudios.GameFace.R
import com.magnitudestudios.GameFace.pojo.EnumClasses.MemberStatus
import com.magnitudestudios.GameFace.pojo.UserInfo.Profile
import com.magnitudestudios.GameFace.pojo.VideoCall.Member
import com.magnitudestudios.GameFace.repository.FirebaseHelper
import com.magnitudestudios.GameFace.repository.SessionRepository
import com.magnitudestudios.GameFace.repository.UserRepository
import com.magnitudestudios.GameFace.ui.calling.IncomingCall
import com.magnitudestudios.GameFace.ui.main.MainActivity
import kotlinx.coroutines.*

/**
 * Notification service. This is the class that handles all notifications, and
 *
 * @constructor Create empty Notification service
 * @see FirebaseMessagingService
 */
class NotificationService : FirebaseMessagingService() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    /**
     * Called when a new Firebase Device is token is issued. This function will also update the device
     * token that is stored on the server for this specific user.
     * @param token the new Firebase token of the device
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        try {
            serviceScope.launch {
                if (Firebase.auth.currentUser != null) {
                    UserRepository.updateDeviceToken(token)
                }
            }
        } catch (e: Exception) {
            Log.e("ERROR", "ON NEW DEVICE TOKEN", e)
        }

        Log.e("--NEW TOKEN--", token)
    }

    /**
     * Called when a message is received through the Firebase messaging service
     *
     * @param p0 the message that is incoming
     */
    override fun onMessageReceived(p0: RemoteMessage) {
        super.onMessageReceived(p0)
        for (a in p0.data) {
            Log.e("Got Data", "${a.key} : ${a.value}")
        }
        if (!validateMessage(p0)) return
        //Decide action depending on type of message
        when (p0.data["type"]) {
            "CALL" -> receiveCall(p0.data)
            "NOTIFICATION" -> showNotification(p0.data)
        }
    }

    /**
     * Called when there is an incoming video call from friend(s).
     * Gets the members of the room.
     *
     * @param data key-value pairs of data. Should contain roomID.
     */
    private fun receiveCall(data: Map<String, String>) {
        val roomID = data["roomID"] ?: error("NO ROOM FOUND")
        serviceScope.launch(Dispatchers.Main) {
            val profiles = withContext(Dispatchers.IO) {
                SessionRepository.updateMemberStatus(Firebase.auth.uid!!, roomID, MemberStatus.RECEIVED)
                val members = SessionRepository.getAllMembers(roomID)
                UserRepository.getUserProfilesByUID(members.map { it.uid })
            }
            if (profiles.isNotEmpty()) launchCall(roomID, profiles)
        }
    }

    /**
     * Launches the call screen to show caller information.
     *
     * @param roomID
     * @param members
     */
    private fun launchCall(roomID: String, members: List<Profile>) {
        val fullScreenIntent = Intent(this, IncomingCall::class.java).apply {
            putExtra(Constants.ROOM_ID_KEY, roomID)
            putExtra(Constants.ROOM_MEMBERS_KEY, Gson().toJson(members))
            flags = Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(this, 1,
                fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val formattedMembers = members.filter { it.uid != Firebase.auth.uid }.joinToString(", ") { it.username }
        val receivingCall = NotificationCompat.Builder(this, getString(R.string.calling_notification_ID))
                .setSmallIcon(R.drawable.logo_simple_rainbow)
                .setContentTitle(getString(R.string.notification_title_call))
                .setContentText("From: $formattedMembers")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVibrate(Constants.VIBRATE_PATTERN)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(true)

//        val missedCall = NotificationCompat.Builder(this, getString(R.string.calling_notification_ID))
//                .setSmallIcon(R.drawable.logo_simple_rainbow)
//                .setContentTitle(getString(R.string.notification_missed_call))
//                .setContentText("From: $formattedMembers")
//                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
//                .setCategory(NotificationCompat.CATEGORY_CALL)
//                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
//                .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(Constants.INCOMING_CALL_ID, receivingCall.build())
            serviceScope.launch {
                delay(10000)
                cancel(Constants.INCOMING_CALL_ID)
//                notify(Constants.MISSED_CALL_ID, missedCall.build())
            }
        }

//        startForeground(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    /**
     * Show a generic notification from the server
     *
     * @param data
     */
    private fun showNotification(data: Map<String, String>) {
        val pendingIntent = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
        val notificationBuilder = NotificationCompat.Builder(this, getString(R.string.friends_notification_ID))
                .setSmallIcon(R.drawable.logo_simple_rainbow)
                .setContentTitle(data["title"])
                .setContentText(data["body"])
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
        }
    }

    //Makes sure message has all the necessary components
    private fun validateMessage(message: RemoteMessage): Boolean {
        if (message.data.isEmpty() || !message.data.containsKey("type")) return false
        else if (Firebase.auth.currentUser == null) return false
        else if (!message.data.containsKey("toUID") || message.data["toUID"] != Firebase.auth.currentUser?.uid) return false
        return true
    }
}