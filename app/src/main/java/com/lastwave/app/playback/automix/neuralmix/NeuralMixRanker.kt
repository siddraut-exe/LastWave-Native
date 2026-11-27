package com.lastwave.app.playback.automix.neuralmix

import kotlin.math.*

data class CompatibilityScore(val score: Double, val coverage: Double, val components: Map<String, Double>)

object NeuralMixRanker {
    /** Unknown evidence is excluded, not treated as a perfect match. */
    fun compare(a: RhythmFingerprint, b: RhythmFingerprint, outgoingKey: String? = null, incomingKey: String? = null, vocalOverlap: Boolean? = null, allowBarRotation: Boolean = false): CompatibilityScore {
        val parts = linkedMapOf<String, Double>(); var weight = 0.0; var sum = 0.0
        fun add(name: String, w: Double, value: Double?) { if (value != null && value.isFinite()) { val v = value.coerceIn(0.0,1.0); parts[name]=v; weight+=w; sum+=w*v } }
        fun similarity(x: List<Double>, y: List<Double>, offset: Int): Double? {
            if (x.size != 16 || y.size != 16) return null
            if (x.any { !it.isFinite() } || y.any { !it.isFinite() }) return null
            val xm = x.average(); val ym = y.average()
            val xc = x.map { it - xm }; val yc = y.map { it - ym }
            val xx = xc.sumOf { it*it }; val yy = yc.sumOf { it*it }
            if (xx < 0.01 || yy < 0.01) return null
            return (xc.indices.sumOf { xc[it] * yc[(it + offset) % 16] } / sqrt(xx*yy)).coerceIn(0.0, 1.0)
        }
        val offsets = if (allowBarRotation) listOf(0, 4, 8, 12) else listOf(0)
        val rotation = offsets.maxByOrNull { similarity(a.rhythm, b.rhythm, it) ?: -1.0 } ?: 0
        add("rhythm",25.0,similarity(a.rhythm,b.rhythm,rotation))
        add("tempo",15.0,if(a.bpm>0 && b.bpm>0) exp(-listOf(0.5, 1.0, 2.0).minOf { abs(ln(a.bpm/(b.bpm*it))) }*8) else null)
        add("harmonic",20.0,keyMatch(outgoingKey,incomingKey))
        add("bass",15.0,similarity(a.bass,b.bass,rotation))
        add("energy",10.0,if(a.rms>0 && b.rms>0) exp(-abs(ln(a.rms/b.rms))) else null)
        add("texture",5.0,similarity(a.texture,b.texture,rotation))
        add("vocals",10.0,vocalOverlap?.let { if(it) 0.0 else 1.0 })
        return CompatibilityScore(if(weight>0) 100*sum/weight else 0.0,weight/100,parts.toMap())
    }
    private fun keyMatch(a: String?, b: String?): Double? {
        val pattern=Regex("(1[0-2]|[1-9])([AB])")
        val x=pattern.matchEntire(a ?: return null) ?: return null
        val y=pattern.matchEntire(b ?: return null) ?: return null
        val d=abs(x.groupValues[1].toInt()-y.groupValues[1].toInt())
        val same=x.groupValues[2]==y.groupValues[2]
        return when { d==0 && same -> 1.0; d==0 || (same && (d==1 || d==11)) -> 0.85; else -> 0.2 }
    }
}
