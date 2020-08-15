/*
 * Copyright (c) 2020 - Magnitude Studios - All Rights Reserved
 * Unauthorized copying of this file, via any medium is prohibited
 * All software is proprietary and confidential
 *
 */

package com.magnitudestudios.GameFace

import android.text.Editable
import android.text.TextWatcher
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.AspectRatio
import androidx.lifecycle.MutableLiveData
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.gson.reflect.TypeToken
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun RequestManager.loadProfile(url: String, target: ImageButton) {
    if (url.isEmpty()) {
        this.load(R.drawable.ic_add_profile_pic).into(target)
        return
    }
    this.load(url)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(R.drawable.profile_placeholder)
            .error(R.drawable.ic_add_profile_pic)
            .transition(DrawableTransitionOptions.withCrossFade())
            .circleCrop()
            .into(target)
}

fun RequestManager.loadProfile(url: String?, target: ImageView) {
    if (url.isNullOrEmpty()) {
        this.load(R.drawable.ic_user_placeholder).into(target)
        return
    }
    this.load(url)
            .error(R.drawable.ic_user_placeholder)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .transition(DrawableTransitionOptions.withCrossFade())
            .placeholder(R.drawable.profile_placeholder)
            .circleCrop()
            .into(target)
}

fun <T> MutableLiveData<T>.notifyObserver() {
    this.value = this.value
}

inline fun <reified T> genericType() = object: TypeToken<T>() {}.type

fun aspectRatio(width: Int, height: Int): Int {
    val previewRatio = max(width, height).toDouble() / min(width, height)
    if (abs(previewRatio - (4.0 / 3.0)) <= abs(previewRatio - (16.0 / 9.0))) {
        return AspectRatio.RATIO_4_3
    }
    return AspectRatio.RATIO_16_9
}

inline fun DatabaseReference.doOnChildAdded(
        crossinline action : (snapshot : DataSnapshot) -> Unit
) = addListener(childAdded = action)

inline fun DatabaseReference.doOnChildRemoved(
        crossinline action : (snapshot : DataSnapshot) -> Unit
) = addListener(childRemoved = action)

inline fun DatabaseReference.doOnError(
        crossinline action : (error : DatabaseError) -> Unit
) = addListener(onCancelled = action)

inline fun DatabaseReference.addListener (
        crossinline childAdded: (
                snapshot: DataSnapshot
        ) -> Unit = { _ -> },
        crossinline childRemoved: (
            snapshot: DataSnapshot
        ) -> Unit = { _ -> },
        crossinline childChanged: (
            snapshot: DataSnapshot,
            previousChildName: String?
        ) -> Unit = { _, _ -> },
        crossinline onCancelled: (
            error: DatabaseError
        ) -> Unit = { _ -> }
): ChildEventListener {
    val childEventListener = object : ChildEventListener {
        override fun onCancelled(error: DatabaseError) = onCancelled.invoke(error)
        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = childChanged.invoke(snapshot, previousChildName)
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) = childAdded.invoke(snapshot)
        override fun onChildRemoved(snapshot: DataSnapshot) = childRemoved.invoke(snapshot)

    }
    addChildEventListener(childEventListener)
    return childEventListener
}
