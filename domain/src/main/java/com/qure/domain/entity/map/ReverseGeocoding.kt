package com.qure.domain.entity.map

data class ReverseGeocoding(
    val status: Status,
    val results: List<Results>,
)

data class Status(
    val code: Int,
    val name: String,
    val message: String,
)

data class Results(
    val name: String,
    val code: Code,
    val region: RegionEntity,
    val land: Land,
)

data class Code(
    val id: String,
    val type: String,
    val mappingId: String,
)

data class RegionEntity(
    val area0: Area,
    val area1: Area,
    val area2: Area,
    val area3: Area,
    val area4: Area,
)

data class Land(
    val type: String,
    val name: String,
    val number1: String,
    val number2: String,
    val coords: Coords,
    val addition0: Addition,
    val addition1: Addition,
    val addition2: Addition,
    val addition3: Addition,
    val addition4: Addition,
)

data class Addition(
    val type: String,
    val value: String,
)

data class Area(
    val name: String,
    val coords: Coords,
    val alias: String,
)

data class Coords(
    val center: Center,
)

data class Center(
    val crs: String,
    val x: Float,
    val y: Float,
)
