/*
 * Copyright (c) 2020 - Magnitude Studios - All Rights Reserved
 * Unauthorized copying of this file, via any medium is prohibited
 * All software is proprietary and confidential
 *
 */

package com.magnitudestudios.GameFace.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.ktx.Firebase
import com.magnitudestudios.GameFace.Constants
import com.magnitudestudios.GameFace.pojo.Helper.Resource
import com.magnitudestudios.GameFace.pojo.UserInfo.Friend
import com.magnitudestudios.GameFace.pojo.UserInfo.FriendRequest
import com.magnitudestudios.GameFace.pojo.UserInfo.Profile
import com.magnitudestudios.GameFace.pojo.UserInfo.User
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

object FirebaseHelper {
    private const val TAG = "FirebaseHelper"

    fun getCurrentUserRef() : DatabaseReference {
        return Firebase.database.reference
                .child(Constants.USERS_PATH)
                .child(Firebase.auth.currentUser!!.uid)
    }

    private fun getCurrentUserProfileRef() : DatabaseReference {
        return Firebase.database.reference
                .child(Constants.PROFILE_PATH)
                .child(Firebase.auth.currentUser!!.uid)
    }

    private fun getUserRef(uid: String): DatabaseReference {
        return Firebase.database.reference.child(Constants.USERS_PATH).child(uid)
    }

    fun createUser(user: User): Task<Void> {
        return getCurrentUserRef().setValue(user)
    }

    suspend fun setUser(user: User) {
        getCurrentUserRef().setValue(user).await()
    }

    fun createProfile(profile: Profile): Task<Void> {
        return getCurrentUserProfileRef().setValue(profile)
    }

    suspend fun updateUser(values: MutableMap<String, Any>): Boolean {
        return try {
            getCurrentUserProfileRef().updateChildren(values).await()
            true
        } catch (e: FirebaseException) {
            Log.e("ERROR", "Updating User", e)
            false
        }
    }

    suspend fun getDeviceToken(): String {
        return FirebaseInstanceId.getInstance().instanceId.await().token
    }

    suspend fun updateDeviceToken(token: String) {
        if (Firebase.auth.currentUser == null) return
        val updated = getUserByUID(Firebase.auth.currentUser?.uid!!)
        if (updated != null) {
            (updated.devicesID as ArrayList).add(token)
            setUser(updated)
            Log.e("UPDATED", "DEVICE TOKEN SUCESS")
        }
    }

    suspend fun getUserByUID(uid: String): User? {
        if (!exists(Constants.USERS_PATH, uid)) return null
        return suspendCoroutine { cont ->
            getUserRef(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onCancelled(p0: DatabaseError) {
                            cont.resume(null)
                        }

                        override fun onDataChange(data: DataSnapshot) {
                            val user = data.getValue(User::class.java)
                            cont.resume(user)
                        }
                    })
        }
    }

    suspend fun getUserProfileByUID(uid: String): Profile? {
        if (!exists(Constants.PROFILE_PATH, uid)) return null
        return suspendCoroutine { cont ->
            Firebase.database.reference
                    .child(Constants.PROFILE_PATH).child(uid)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onCancelled(p0: DatabaseError) {
                            cont.resume(null)
                        }

                        override fun onDataChange(data: DataSnapshot) {
                            val userProfile = data.getValue(Profile::class.java)
                            cont.resume(userProfile)
                        }
                    })
        }
    }

    suspend fun getUserProfilesByUID(uids: List<String>): List<Profile> {
        val temp = mutableListOf<Profile>()
        for (uid in uids) {
            val profile = getUserProfileByUID(uid)
            if (profile != null) temp.add(profile)
        }
        return temp
    }

    suspend fun exists(vararg path: String): Boolean {
        var reference = Firebase.database.reference
        for (s in path) reference = reference.child(s)
        return suspendCancellableCoroutine { cont ->
            reference.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                    Log.e("ERROR AT EXISTS", p0.message)
                    cont.cancel(p0.toException())
                }

                override fun onDataChange(data: DataSnapshot) {
                    cont.resume(data.exists())
                }

            })
        }
    }

    suspend fun getValue(vararg path: String): Any? {
        var reference = Firebase.database.reference
        for (s in path) reference = reference.child(s)
        return suspendCancellableCoroutine { cont ->
            reference.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                    Log.e("ERROR AT hasValue", p0.message)
                    cont.cancel(p0.toException())
                }

                override fun onDataChange(data: DataSnapshot) {
                    cont.resume(data.value)
                }

            })
        }
    }

    suspend fun getProfilesByUsername(query: String): Resource<List<Profile>> {
        Log.e("QUErY: ", query)
        return suspendCoroutine { cont ->
            Firebase.database.reference.child(Constants.PROFILE_PATH)
                    .orderByChild("username")
                    .startAt(query)
                    .endAt("${query}\uf8ff")
                    .limitToFirst(25)
                    .addListenerForSingleValueEvent(
                            object : ValueEventListener {
                                override fun onCancelled(p0: DatabaseError) {
                                    cont.resume(Resource.error(p0.message, null))
                                }

                                override fun onDataChange(data: DataSnapshot) {
                                    val temp = mutableListOf<Profile>()
                                    Log.e("HERE", data.toString())
                                    for (snap in data.children) {
                                        val profile = snap.getValue(Profile::class.java)
                                        if (profile != null) temp.add(profile)
                                    }
                                    cont.resume(Resource.success(temp))
                                }

                            }
                    )
        }
    }

    fun sendFriendRequest(toUser: Profile) {
        //Send Request to Friend
        getUserRef(toUser.uid)
                .child(User::friendRequests.name).child(Firebase.auth.currentUser!!.uid).setValue(
                        FriendRequest(Firebase.auth.currentUser!!.uid, ServerValue.TIMESTAMP, false)
                )

        //Put in friendRequestsSent
        getCurrentUserRef()
                .child(User::friendRequestsSent.name).child(toUser.uid).setValue(
                        FriendRequest(toUser.uid, ServerValue.TIMESTAMP, false)
                )
    }

    suspend fun deleteFriendRequest(uid: String) {
        getCurrentUserRef()
                .child(User::friendRequests.name)
                .child(uid)
                .removeValue().await()

        getUserRef(uid)
                .child(User::friendRequestsSent.name)
                .child(Firebase.auth.currentUser!!.uid)
                .removeValue().await()
    }

    suspend fun acceptFriendRequest(uid: String) {
        getCurrentUserRef()
                .child(User::friends.name)
                .child(uid)
                .setValue(Friend(uid))
        getUserRef(uid)
                .child(User::friends.name)
                .child(Firebase.auth.currentUser!!.uid)
                .setValue(Friend(Firebase.auth.currentUser!!.uid))
        deleteFriendRequest(uid)
    }

}