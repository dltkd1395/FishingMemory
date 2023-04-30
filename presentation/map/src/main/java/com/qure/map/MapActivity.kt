package com.qure.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PointF
import android.location.Location
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.*
import com.naver.maps.map.NaverMap.OnMapClickListener
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.util.FusedLocationSource
import com.qure.core.BaseActivity
import com.qure.core.extensions.dpToPx
import com.qure.core.extensions.toReverseCoordsString
import com.qure.core.util.setOnSingleClickListener
import com.qure.domain.entity.MarkerType
import com.qure.map.databinding.ActivityMapBinding
import com.qure.map.model.FishingSpotUI
import com.qure.map.model.toTedClusterItem
import com.qure.memo.detail.DetailMemoActivity.Companion.MEMO_DATA
import com.qure.memo.model.MemoUI
import com.qure.memo.model.toTedClusterItem
import com.qure.navigator.DetailMemoNavigator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ted.gun0912.clustering.BaseBuilder
import ted.gun0912.clustering.clustering.Cluster
import ted.gun0912.clustering.clustering.TedClusterItem
import ted.gun0912.clustering.naver.TedNaverClustering
import ted.gun0912.clustering.naver.TedNaverMarker
import javax.inject.Inject

@AndroidEntryPoint
class MapActivity : BaseActivity<ActivityMapBinding>(R.layout.activity_map), OnMapReadyCallback,
    OnMapClickListener {

    @Inject
    lateinit var detailMemoNavigator: DetailMemoNavigator

    private val viewModel by viewModels<MapViewModel>()
    private val adapter: MapAdapter by lazy {
        MapAdapter { item ->
            val intent = if (item is MemoUI) {
                detailMemoNavigator.intent(this)
            } else {
                detailMemoNavigator.intent(this)
            }

            intent.apply {
                if (item is MemoUI) {
                    putExtra(MEMO_DATA, item)
                }
                if (item is FishingSpotUI) {
                    putExtra("ITEM_ME", item)
                }
            }
            startActivity(intent)
        }
    }
    private lateinit var naverMap: NaverMap
    private lateinit var locationSource: FusedLocationSource
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var tedNaverClustering: BaseBuilder<TedNaverClustering<TedClusterItem>,
            TedClusterItem,
            Marker,
            TedNaverMarker,
            NaverMap,
            OverlayImage>? = null
    private var preTedNaverClustering: TedNaverClustering<TedClusterItem>?? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationSource = FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)
        viewModel.getFilteredMemo()
        openMapFragment()
        initView()
        initRecyclerView()
    }

    private fun initView() {
        binding.imageViewActivityMapBack.setOnSingleClickListener {
            finish()
        }

        binding.imageViewActivityMapLocation.setOnClickListener {
            updateCurrentLocation()
        }

        binding.chipGroupActivityMap.setOnCheckedChangeListener { group, checkedId ->
            selectMapType(checkedId)
        }

        binding.chipGroupActivityMapMarkertype.setOnCheckedChangeListener { group, checkedId ->
            selectMarkerType(checkedId)
        }
    }

    private fun initRecyclerView() {
        binding.bottomSheetActivityMap.recyclerViewBottomSheetMemoList.adapter = adapter
    }

    private fun selectMapType(checkedId: Int) {
        when (checkedId) {
            R.id.chip_activityMap_basic_map -> naverMap.mapType = NaverMap.MapType.Basic
            R.id.chip_activityMap_satellite_map -> naverMap.mapType = NaverMap.MapType.Satellite
            R.id.chip_activityMap_terrain_map -> naverMap.mapType = NaverMap.MapType.Terrain
        }
    }

    private fun selectMarkerType(checkedId: Int) {

        val markerType = when (checkedId) {
            R.id.chip_activityMap_memo -> {
                viewModel.getFilteredMemo()
                MarkerType.MEMO
            }
            R.id.chip_activityMap_sea -> MarkerType.SEA
            R.id.chip_activityMap_reservoir -> MarkerType.RESERVOIR
            R.id.chip_activityMap_flatland -> MarkerType.FLATLAND
            R.id.chip_activityMap_other -> MarkerType.OTHER
            else -> throw IllegalArgumentException("Invalid checkedId")
        }
        if (markerType != MarkerType.MEMO) {
            viewModel.getFishingSpot(markerType)
        }
        clearMpaMarker()
    }

    private fun clearMpaMarker() {
        preTedNaverClustering?.clearItems()
        preTedNaverClustering = tedNaverClustering?.make()
        tedNaverClustering = null
    }

    private fun observe() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch {
                    viewModel.markers.collect { markers ->
                        tedNaverClustering = initNaverClustering(markers)
                        preTedNaverClustering = tedNaverClustering?.make()
                        tedNaverClustering = null
                    }
                }
            }
        }
    }

    private fun initNaverClustering(markers: List<Any>): BaseBuilder<TedNaverClustering<TedClusterItem>, TedClusterItem, Marker, TedNaverMarker, NaverMap, OverlayImage> {
        val clusterItems = markers.map {
            when (it) {
                is MemoUI -> it.toTedClusterItem()
                is FishingSpotUI -> it.toTedClusterItem()
                else -> throw IllegalStateException("create Marker error")
            }
        }
        return TedNaverClustering.with<TedClusterItem>(this, naverMap)
            .customMarker { createMarker() }
            .customCluster { setClusteringText(it) }
            .markerClickListener { marker ->
                setBottomSheetView(
                    getSelectedMarker(
                        marker,
                        markers
                    )
                )
            }
            .clusterClickListener { clusterItems ->
                val clusterMarkers = getSelectedClustering(clusterItems, markers)
                setBottomSheetView(clusterMarkers.toList())
            }
            .minClusterSize(1)
            .clusterBuckets(intArrayOf(1000))
            .items(clusterItems)
    }

    private fun getSelectedClustering(
        clusterItems: Cluster<TedClusterItem>,
        markers: List<Any>
    ): MutableSet<Any> {
        val clusterMarkers = mutableSetOf<Any>()
        clusterItems.items.forEach {
            val latLng = it.getTedLatLng().toReverseCoordsString()
            val filteredMarkers = markers.filter { item ->
                when (item) {
                    is MemoUI -> item.coords == latLng
                    is FishingSpotUI -> "${item.longitude},${item.latitude}" == latLng
                    else -> false
                }
            }
            clusterMarkers.addAll(filteredMarkers)
        }
        return clusterMarkers
    }

    private fun getSelectedMarker(
        marker: TedClusterItem,
        markers: List<Any>
    ): List<Any> {
        val (lng, lat) = marker.getTedLatLng()
        val markerItems = markers.filter {
            when (it) {
                is MemoUI -> it.coords == "${lat},${lng}"
                is FishingSpotUI ->
                    LatLng(it.longitude, it.latitude) ==
                            LatLng(lat, lng)
                else -> false
            }
        }
        return markerItems
    }

    private fun setClusteringText(it: Cluster<TedClusterItem>): View {
        return TextView(this).apply {
            setBackgroundResource(com.qure.core_design.R.drawable.bg_oval_gray600)
            setTextColor(Color.WHITE)
            setPadding(50, 40, 50, 40)
            text = "${it.size}"
        }
    }

    private fun createMarker(): Marker {
        val marker = Marker().apply {
            icon =
                OverlayImage.fromResource(com.qure.core_design.R.drawable.bg_map_fill_marker)
            width = 100
            height = 120
            isHideCollidedCaptions = true
            isHideCollidedSymbols = true
        }
        return marker
    }

    private fun setBottomSheetView(items: List<Any>) {
        changeBottomSheetPeekHeight(300)
        if (items.isNotEmpty() && items[0] is MemoUI) {
            binding.bottomSheetActivityMap.textViewBottomSheetMemoListCount.text =
                "${items.size}개의 메모"
        } else {
            binding.bottomSheetActivityMap.textViewBottomSheetMemoListCount.text =
                "${items.size}개의 낚시터"
        }
        binding.bottomSheetActivityMap.recyclerViewBottomSheetMemoList.adapter = adapter
        adapter.submitList(items)
    }

    private fun openMapFragment() {
        val fm = supportFragmentManager
        val mapFragment =
            fm.findFragmentById(R.id.fragment_activityMap) as MapFragment?
                ?: MapFragment.newInstance().also {
                    fm.beginTransaction()
                        .add(R.id.fragment_activityMap, it)
                        .commit()
                }
        mapFragment.getMapAsync(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions,
                grantResults
            )
        ) {
            if (!locationSource.isActivated) { // 권한 거부됨
                naverMap.locationTrackingMode = LocationTrackingMode.None
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onMapReady(naverMap: NaverMap) {
        this.naverMap = naverMap
        tedNaverClustering = TedNaverClustering.with<TedClusterItem>(this, naverMap)
        naverMap.setOnMapClickListener(this)
        setMapSettings()
        updateCurrentLocation()
        observe()

    }

    private fun setMapSettings() {
        naverMap.apply {
            locationSource = locationSource
            locationTrackingMode = LocationTrackingMode.NoFollow
        }

        val uiSettings = naverMap.uiSettings
        uiSettings.apply {
            isCompassEnabled = false
            isZoomControlEnabled = false
            setLogoMargin(0, 0, 0, 60.dpToPx(this@MapActivity))
        }
    }

    private fun updateCurrentLocation() {
        var currentLocation: Location?
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
                currentLocation = location
                naverMap.locationOverlay.run {
                    isVisible = true
                    position = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
                }
                val cameraUpdate = CameraUpdate.scrollTo(
                    LatLng(
                        currentLocation!!.latitude,
                        currentLocation!!.longitude
                    )
                )
                naverMap.moveCamera(cameraUpdate)
            }
    }

    override fun onMapClick(p0: PointF, p1: LatLng) {
        changeBottomSheetPeekHeight(50)
    }

    private fun changeBottomSheetPeekHeight(height: Int) {
        val bottomSheet =
            BottomSheetBehavior.from(binding.bottomSheetActivityMap.constraintLayoutBottomSheetMemoList)
        bottomSheet.setPeekHeight(height.dpToPx(this), true)

        bottomSheet.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheetView: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_SETTLING) {
                    bottomSheetView.animate()
                        .translationY(0f)
                }
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetView.animate()
                        .setDuration(200)
                        .translationY(20f)
                }
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    bottomSheetView.animate()
                        .setDuration(200)
                        .translationY(-20f)
                }
            }

            override fun onSlide(bottomSheetView: View, slideOffset: Float) {
                when {
                    slideOffset < 0f -> {
                        if (bottomSheet.peekHeight == 300.dpToPx(this@MapActivity)) {
                            updateUiOnSlide(310, 250)
                        } else {
                            updateUiOnSlide(60, 0)
                        }
                    }

                    slideOffset > 0f -> {
                        if (bottomSheet.peekHeight == 300.dpToPx(this@MapActivity)) {
                            updateUiOnSlide(310, 250)
                        } else {
                            updateUiOnSlide(60, 0)
                        }
                    }
                }
            }

        })
    }

    private fun updateUiOnSlide(mapUIheight: Int, layoutHeight: Int) {
        val uiSettings = naverMap.uiSettings
        uiSettings.apply {
            isCompassEnabled = false
            isZoomControlEnabled = false
            setLogoMargin(0, 0, 0, mapUIheight.dpToPx(this@MapActivity))
        }
        val layoutParams =
            binding.constraintLayoutActivityMap.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.setMargins(0, 0, 0, layoutHeight.dpToPx(this@MapActivity))
        binding.constraintLayoutActivityMap.layoutParams = layoutParams
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }
}