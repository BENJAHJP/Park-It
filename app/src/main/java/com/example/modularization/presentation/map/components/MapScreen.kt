package com.example.modularization.presentation.map.components

import android.Manifest
import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.modularization.R
import com.example.modularization.presentation.map.MapEvent
import com.example.modularization.presentation.map.MapScreenViewModel
import com.example.modularization.ui.theme.Purple80
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
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

    DisposableEffect(key1 = lifecycleOwner, effect = {
        val observer = LifecycleEventObserver{ _, event ->
            if (event == Lifecycle.Event.ON_START){
                locationPermissionState.launchPermissionRequest()
                if (locationPermissionState.status.isGranted){
                    viewModel.getDeviceLocation(fusedLocationProviderClient = fusedLocationProviderClient)
                } else if (locationPermissionState.status.shouldShowRationale){
                    locationPermissionState.launchPermissionRequest()
                } else {

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
                onClickNavigation = {
                    viewModel.dialogState = true
                }
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
                        "Permission denied completely",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }) {
                Icon(imageVector = Icons.Default.MyLocation,
                    contentDescription = "my location")
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                cameraPositionState = cameraPositionState,
                properties = viewModel.state.properties,
                uiSettings = mapUiSettings,
                onMapLongClick = {
                    viewModel.lat = it.latitude
                    viewModel.lng = it.longitude
                    viewModel.titleDialogState = true
                }
            ){
                viewModel.state.parkingSpots.forEach { parkingSpot ->
                    Marker(
                        position = LatLng(parkingSpot.lat, parkingSpot.lng),
                        title = "Parking spot (${parkingSpot.title})",
                        snippet = "CLick to delete",
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
            if (viewModel.dialogState){
                Card(
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 5.dp,
                        pressedElevation = 0.dp
                    ),
                    modifier = Modifier
                        .fillMaxHeight(0.4f)
                        .fillMaxWidth()
                        .padding(20.dp)
                        .align(Alignment.Center),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Parking Spots",
                                fontSize = 25.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.dialogState = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "close")
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.SpaceBetween
                        ){
                            items(state.parkingSpots){ parkingSpot ->
                                Card(
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .fillMaxHeight(1f)
                                        .width(100.dp)
                                        .clickable {
                                            scope.launch {
                                                viewModel.dialogState = false
                                                cameraPositionState.animate(
                                                    update = CameraUpdateFactory.newLatLngZoom(
                                                        LatLng(
                                                            parkingSpot.lat,
                                                            parkingSpot.lng
                                                        ),
                                                        1000F
                                                    )
                                                )
                                            }
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp)
                                        ,
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = parkingSpot.title)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (viewModel.titleDialogState){
                Card (
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 5.dp,
                        pressedElevation = 0.dp
                    ),
                    modifier = Modifier
                        .fillMaxHeight(0.4f)
                        .fillMaxWidth()
                        .padding(20.dp)
                        .align(Alignment.Center),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Enter Title",
                                fontSize = 25.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.titleDialogState = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "close")
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = viewModel.title,
                            onValueChange = { viewModel.onEvent(MapEvent.OnTitleChanged(it)) },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Purple80,
                                    contentColor = Color.Black
                                ),
                                onClick = {
                                    viewModel.onEvent(MapEvent.OnProceedCLick)
                                    viewModel.titleDialogState = false
                                }
                            ) {
                                Text(text = "Proceed")
                            }
                        }
                    }
                }
            }
        }
    }
}