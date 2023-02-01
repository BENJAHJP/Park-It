package com.example.modularization.presentation.map.components

import android.Manifest
import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.modularization.R
import com.example.modularization.presentation.map.MapEvent
import com.example.modularization.presentation.map.MapScreenViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class,
    ExperimentalComposeUiApi::class, ExperimentalAnimationApi::class
)
@Composable
fun MapScreen(
    viewModel: MapScreenViewModel = hiltViewModel(),
    fusedLocationProviderClient: FusedLocationProviderClient
) {
    val scope = rememberCoroutineScope()
    val state = viewModel.state
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val mapUiSettings = remember{ MapUiSettings(zoomControlsEnabled = false) }
    val cameraPositionState = rememberCameraPositionState()
    val locationPermissionState =  rememberPermissionState(
        permission = Manifest.permission.ACCESS_FINE_LOCATION
    )
    val transitionState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }

    DisposableEffect(key1 = lifecycleOwner, effect = {
        val observer = LifecycleEventObserver{ _, event ->
            if (event == Lifecycle.Event.ON_START){
                locationPermissionState.launchPermissionRequest()
                if (locationPermissionState.status.isGranted){
                    viewModel.getDeviceLocation(fusedLocationProviderClient = fusedLocationProviderClient)
                } else if (locationPermissionState.status.shouldShowRationale){
                    locationPermissionState.launchPermissionRequest()
                } else {
                    Toast.makeText(
                        context,
                        "Denied completely",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    })

    LaunchedEffect(state.parkingSpots){
        if (state.parkingSpots.isNotEmpty()){
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(
                    LatLng(
                        state.parkingSpots.last().lat,
                        state.parkingSpots.last().lng
                    ),
                    1000F
                )
            )
        }
    }

    Scaffold(
        topBar = {
            MainTopAppBar(
                navigationIcon = Icons.Default.Menu,
                onClickNavigation = { transitionState.apply { targetState = false } }
            ) {
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (locationPermissionState.status.isGranted){
                    scope.launch {
                        cameraPositionState.animate(
                            update = CameraUpdateFactory.newLatLngZoom(
                                LatLng(
                                    state.lastKnownLocation?.latitude?:0.0,
                                    state.lastKnownLocation?.longitude?:0.0
                                ),
                                1000F
                            )
                        )
                    }
                } else if (locationPermissionState.status.shouldShowRationale){
                    locationPermissionState.launchPermissionRequest()
                } else {
                    Toast.makeText(
                        context,
                        "Denied completely",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }) {
                Icon(imageVector = Icons.Default.MyLocation,
                    contentDescription = "my location")
            }
        }
    ) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties( usePlatformDefaultWidth = false)
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween()),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween()
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    GoogleMap(
                        cameraPositionState = cameraPositionState,
                        properties = viewModel.state.properties,
                        uiSettings = mapUiSettings,
                        onMapLongClick = {
                            viewModel.onEvent(MapEvent.OnMapLongCLick(it))
                        }
                    ){
                        viewModel.state.parkingSpots.forEach { parkingSpot ->
                            Marker(
                                position = LatLng(parkingSpot.lat, parkingSpot.lng),
                                title = "Parking spot (${parkingSpot.lat}, ${parkingSpot.lng})",
                                snippet = "Long CLick to delete",
                                onInfoWindowClick = {
                                    viewModel.onEvent(MapEvent.OnInfoWindowLongClick(parkingSpot))
                                },
                                onClick = {
                                    it.showInfoWindow()
                                    true
                                },
                                icon = BitmapDescriptorFactory.fromResource(R.drawable.location),
                            )
                        }
                    }
                }
            }
        }
    }
}