package com.sketchreproducer.sketch_reproducer

data class DrawingStroke(
    val points: List<FloatArray>,
    val mergeable: Boolean = true,
)
